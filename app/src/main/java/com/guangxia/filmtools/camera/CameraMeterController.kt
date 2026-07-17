package com.guangxia.filmtools.camera

import android.content.Context
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

data class RawCameraReading(
    val aperture: Double,
    val exposureSeconds: Double,
    val iso: Int,
    val status: ReadingStatus,
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
    @Volatile private var fallbackAperture: Float? = null
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
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (now - lastReadingDispatchNanos < READING_DISPATCH_INTERVAL_NANOS) return
                    lastReadingDispatchNanos = now
                    val aperture = result.get(CaptureResult.LENS_APERTURE)
                        ?: fallbackAperture
                        ?: return
                    val exposureNanos = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        ?: return
                    val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        ?: return
                    if (exposureNanos <= 0 || iso <= 0 || aperture <= 0f) return
                    val seconds = exposureNanos / 1_000_000_000.0
                    val status = when {
                        seconds >= 0.5 || iso >= 6400 -> ReadingStatus.TOO_DARK
                        seconds <= 1.0 / 16_000.0 && iso <= 64 -> ReadingStatus.TOO_BRIGHT
                        else -> ReadingStatus.NORMAL
                    }
                    onReading(RawCameraReading(aperture.toDouble(), seconds, iso, status))
                }
            })
            val builtPreview = builder.build().also { it.surfaceProvider = previewView.surfaceProvider }
            val selectorBuilder = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == logicalCameraId }
                }
            val lockedSelector = selectorBuilder.build()
            val baseSessionConfig = SessionConfig.Builder(builtPreview).build()
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
            val configuredSession = SessionConfig.Builder(builtPreview).apply {
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

    private fun applyMeteringMode() {
        val control = camera?.cameraControl ?: return
        if (mode == MeteringMode.AVERAGE) {
            control.cancelFocusAndMetering()
        } else {
            val point = previewView.meteringPointFactory.createPoint(0.5f, 0.5f, 0.15f)
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
        camera = null
        activeFrameRateRange = null
        fallbackAperture = null
        lastReadingDispatchNanos = 0L
    }

    private data class ResolvedCameraSetup(
        val fixedAperture: Float?,
        val preferredFpsRange: Range<Int>?,
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
