package com.guangxia.filmtools.camera

import android.content.Context
import android.graphics.PointF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.guangxia.filmtools.core.MeteringMode
import com.guangxia.filmtools.core.ReadingStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class RawCameraReading(
    val aperture: Double,
    val exposureSeconds: Double,
    val iso: Int,
    val status: ReadingStatus,
    val regionLuminance: Double? = null,
)

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@OptIn(androidx.camera.core.ExperimentalSessionConfig::class)
class CameraMeterController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onReading: (RawCameraReading) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var sessionConfig: SessionConfig? = null
    private var activeFrameRateRange: Range<Int>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analysisExecutor: java.util.concurrent.ExecutorService? = null
    @Volatile private var fallbackAperture: Float? = null
    private val exposureHistory = java.util.ArrayDeque<LatestExposure>()
    @Volatile private var exposurePointX = 0.5f
    @Volatile private var exposurePointY = 0.5f
    private var mode: MeteringMode = MeteringMode.AVERAGE
    @Volatile private var exposureLocked = false
    @Volatile private var started = false
    private var lastReadingDispatchNanos = 0L

    fun start(initialMode: MeteringMode) {
        started = true
        mode = initialMode
        lastReadingDispatchNanos = 0L
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        future.addListener({
            runCatching {
                val cameraProvider = future.get()
                val selectedInfo = CameraSelector.DEFAULT_BACK_CAMERA
                    .filter(cameraProvider.availableCameraInfos)
                    .firstOrNull() ?: error("找不到后置摄像头")
                val logicalCameraId = Camera2CameraInfo.from(selectedInfo).cameraId
                val cameraXFrameRateRanges = selectedInfo.supportedFrameRateRanges
                    .map { it.lower..it.upper }
                Triple(cameraProvider, logicalCameraId, cameraXFrameRateRanges)
            }.onSuccess { (cameraProvider, logicalCameraId, cameraXFrameRateRanges) ->
                val cachedSetup = cameraSetupCache[logicalCameraId]
                if (cachedSetup != null) {
                    bindCamera(cameraProvider, logicalCameraId, cachedSetup)
                } else {
                    lensResolverExecutor.execute {
                        val setup = ResolvedCameraSetup(
                            fixedAperture = findFixedAperture(logicalCameraId),
                            preferredFpsRange = findPreferredPreviewFpsRange(
                                logicalCameraId,
                                cameraXFrameRateRanges,
                            ),
                        )
                        cameraSetupCache[logicalCameraId] = setup
                        mainExecutor.execute {
                            if (started) bindCamera(cameraProvider, logicalCameraId, setup)
                        }
                    }
                }
            }.onFailure {
                onError(it.message ?: "无法启动后置摄像头")
            }
        }, mainExecutor)
    }

    private fun bindCamera(cameraProvider: ProcessCameraProvider, logicalCameraId: String, setup: ResolvedCameraSetup) {
        if (!started) return
        runCatching {
            val builder = Preview.Builder().setResolutionSelector(previewResolutionSelector)
            val extender = Camera2Interop.Extender(builder)
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON,
            )
            extender.setCaptureRequestOption(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD,
            )
            extender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    val aperture = result.get(CaptureResult.LENS_APERTURE)
                        ?: fallbackAperture
                        ?: return
                    val exposureNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        ?: return
                    val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        ?: return
                    if (exposureNanos <= 0 || iso <= 0 || aperture <= 0f) return
                    val seconds = exposureNanos / 1_000_000_000.0
                    val exposure = LatestExposure(
                        aperture = aperture.toDouble(),
                        exposureSeconds = seconds,
                        iso = iso,
                        timestampNanos = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L,
                    )
                    synchronized(exposureHistory) {
                        exposureHistory.addLast(exposure)
                        while (exposureHistory.size > 12) exposureHistory.removeFirst()
                    }
                }
            })
            val builtPreview = builder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            val builtAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "GuangXia-MeterAnalysis").apply { isDaemon = true }
            }
            builtAnalysis.setAnalyzer(executor, ::analyzeImage)
            imageAnalysis = builtAnalysis
            analysisExecutor = executor
            val selectorBuilder = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == logicalCameraId }
                }
            val lockedSelector = selectorBuilder.build()
            val baseSessionConfig = SessionConfig.Builder(builtPreview, builtAnalysis).build()
            val guaranteedRanges = runCatching {
                cameraProvider.getCameraInfo(lockedSelector)
                    .getSupportedFrameRateRanges(baseSessionConfig)
                    .map { it.lower..it.upper }
            }.getOrNull()
            val selectedRange = guaranteedRanges
                ?.takeIf { it.isNotEmpty() }
                ?.let(::selectSmoothPreviewFpsRange)
                ?.let { Range(it.first, it.last) }
                ?: setup.preferredFpsRange
            val configuredSession = SessionConfig.Builder(builtPreview, builtAnalysis).apply {
                selectedRange?.let(::setFrameRateRange)
            }.build()
            cameraProvider.unbindAll()
            val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, lockedSelector, configuredSession)
            provider = cameraProvider
            camera = boundCamera
            preview = builtPreview
            sessionConfig = configuredSession
            activeFrameRateRange = selectedRange
            fallbackAperture = setup.fixedAperture
                ?: Camera2CameraInfo.from(boundCamera.cameraInfo)
                    .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                    ?.firstOrNull()
            previewView.post {
                applyMeteringMode()
                applyCameraRequestOptions()
            }
        }.onFailure {
            onError(it.message ?: "无法启动后置摄像头")
        }
    }

    fun setMode(value: MeteringMode) {
        if (mode == value && camera != null) return
        mode = value
        previewView.post { applyMeteringMode() }
    }

    fun setExposurePoint(x: Float, y: Float) {
        exposurePointX = x.coerceIn(0f, 1f)
        exposurePointY = y.coerceIn(0f, 1f)
        if (mode == MeteringMode.CENTER) previewView.post { applyMeteringMode() }
    }

    private fun applyMeteringMode() {
        val control = camera?.cameraControl ?: return
        if (mode == MeteringMode.AVERAGE) {
            control.cancelFocusAndMetering()
        } else {
            if (previewView.width <= 0 || previewView.height <= 0) {
                previewView.post { applyMeteringMode() }
                return
            }
            val point = previewView.meteringPointFactory.createPoint(
                previewView.width * exposurePointX,
                previewView.height * exposurePointY,
                0.15f,
            )
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
                .disableAutoCancel()
                .build()
            control.startFocusAndMetering(action)
        }
    }

    fun setExposureLocked(locked: Boolean) {
        exposureLocked = locked
        applyCameraRequestOptions()
    }

    private fun applyCameraRequestOptions() {
        val control = camera?.cameraControl ?: return
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, exposureLocked)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD,
            )
            .apply {
                activeFrameRateRange?.let {
                    setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it)
                }
            }
            .build()
        Camera2CameraControl.from(control).setCaptureRequestOptions(options)
    }

    fun stop() {
        started = false
        sessionConfig?.let { provider?.unbind(it) } ?: preview?.let { provider?.unbind(it) }
        sessionConfig = null
        preview = null
        imageAnalysis = null
        camera = null
        activeFrameRateRange = null
        fallbackAperture = null
        synchronized(exposureHistory) { exposureHistory.clear() }
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
        lastReadingDispatchNanos = 0L
    }

    private fun analyzeImage(image: ImageProxy) {
        try {
            val exposure = exposureFor(image.imageInfo.timestamp) ?: return
            val now = SystemClock.elapsedRealtimeNanos()
            if (now - lastReadingDispatchNanos < READING_DISPATCH_INTERVAL_NANOS) return
            val luminance = sampleMeteringRegion(image)
            if (!luminance.isFinite() || luminance <= 0.0) return
            lastReadingDispatchNanos = now
            val status = when {
                exposure.exposureSeconds >= 0.5 || exposure.iso >= 6400 -> ReadingStatus.TOO_DARK
                exposure.exposureSeconds <= 1.0 / 16_000.0 && exposure.iso <= 64 -> ReadingStatus.TOO_BRIGHT
                else -> ReadingStatus.NORMAL
            }
            onReading(
                RawCameraReading(
                    aperture = exposure.aperture,
                    exposureSeconds = exposure.exposureSeconds,
                    iso = exposure.iso,
                    status = status,
                    regionLuminance = luminance,
                )
            )
        } finally {
            image.close()
        }
    }

    private fun sampleMeteringRegion(image: ImageProxy): Double {
        val width = image.width
        val height = image.height
        val point = displayToImagePoint(
            exposurePointX,
            exposurePointY,
            image.imageInfo.rotationDegrees,
        )
        val rect = if (mode == MeteringMode.AVERAGE) {
            android.graphics.Rect(0, 0, width, height)
        } else {
            val halfWidth = width * 0.075f
            val halfHeight = height * 0.075f
            android.graphics.Rect(
                (point.x * width - halfWidth).roundToInt().coerceIn(0, width - 1),
                (point.y * height - halfHeight).roundToInt().coerceIn(0, height - 1),
                (point.x * width + halfWidth).roundToInt().coerceIn(1, width),
                (point.y * height + halfHeight).roundToInt().coerceIn(1, height),
            ).also {
                if (it.right <= it.left) it.right = (it.left + 1).coerceAtMost(width)
                if (it.bottom <= it.top) it.bottom = (it.top + 1).coerceAtMost(height)
            }
        }
        return averageY(image, rect)
    }

    private fun averageY(image: ImageProxy, rect: android.graphics.Rect): Double {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val step = sqrt((rect.width().toDouble() * rect.height() / 20_000.0))
            .roundToInt()
            .coerceAtLeast(1)
        var sum = 0L
        var count = 0L
        var y = rect.top
        while (y < rect.bottom) {
            val rowStart = y * plane.rowStride
            var x = rect.left
            while (x < rect.right) {
                val index = rowStart + x * plane.pixelStride
                if (index in 0 until buffer.capacity()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += step
            }
            y += step
        }
        return if (count == 0L) 0.0 else sum.toDouble() / count
    }

    private fun displayToImagePoint(x: Float, y: Float, rotationDegrees: Int): PointF = when (rotationDegrees) {
        90 -> PointF(y, 1f - x)
        180 -> PointF(1f - x, 1f - y)
        270 -> PointF(1f - y, x)
        else -> PointF(x, y)
    }

    private fun exposureFor(imageTimestampNanos: Long): LatestExposure? = synchronized(exposureHistory) {
        if (exposureHistory.isEmpty()) return@synchronized null
        if (imageTimestampNanos <= 0L) return@synchronized exposureHistory.peekLast()
        exposureHistory.minByOrNull { exposure ->
            if (exposure.timestampNanos <= 0L) Long.MAX_VALUE
            else abs(exposure.timestampNanos - imageTimestampNanos)
        }
    }

    private data class ResolvedCameraSetup(
        val fixedAperture: Float?,
        val preferredFpsRange: Range<Int>?,
    )

    private data class LatestExposure(
        val aperture: Double,
        val exposureSeconds: Double,
        val iso: Int,
        val timestampNanos: Long,
    )

    private fun findFixedAperture(cameraId: String): Float? = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
            ?.firstOrNull()
    }.getOrNull()

    private fun findPreferredPreviewFpsRange(
        cameraId: String,
        cameraXFrameRateRanges: List<IntRange>,
    ): Range<Int>? = runCatching {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val hardwareRanges = manager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            ?.map { it.lower..it.upper }
            .orEmpty()
        val compatibleRanges = if (cameraXFrameRateRanges.isEmpty()) {
            hardwareRanges
        } else {
            hardwareRanges.filter { it in cameraXFrameRateRanges }
        }
        val selected = selectSmoothPreviewFpsRange(
            compatibleRanges,
        ) ?: return@runCatching null
        Range(selected.first, selected.last)
    }.getOrNull()

    private companion object {
        const val READING_DISPATCH_INTERVAL_NANOS = 100_000_000L
        val previewResolutionSelector: ResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                ),
            )
            .build()
        val lensResolverExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "GuangXia-LensResolver").apply { isDaemon = true }
        }
        val cameraSetupCache = ConcurrentHashMap<String, ResolvedCameraSetup>()
    }
}

internal fun selectSmoothPreviewFpsRange(ranges: List<IntRange>): IntRange? {
    ranges.firstOrNull { it.first == 30 && it.last == 30 }?.let { return it }
    return ranges
        .asSequence()
        .filter { it.first >= 30 && it.last <= 60 }
        .minWithOrNull(compareBy<IntRange> { it.first }.thenByDescending { it.last })
}
