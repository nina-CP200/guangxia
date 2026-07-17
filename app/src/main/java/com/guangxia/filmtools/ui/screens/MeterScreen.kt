package com.guangxia.filmtools.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import com.guangxia.filmtools.camera.CameraMeterController
import com.guangxia.filmtools.core.ExposureCalculator
import com.guangxia.filmtools.core.ExposurePriority
import com.guangxia.filmtools.core.ExposureResult
import com.guangxia.filmtools.core.MeterReading
import com.guangxia.filmtools.core.MeteringMode
import com.guangxia.filmtools.core.ReadingStatus
import com.guangxia.filmtools.data.AppSettings
import com.guangxia.filmtools.ui.MainViewModel
import com.guangxia.filmtools.ui.components.RotaryWheel
import com.guangxia.filmtools.ui.components.ScreenHeader
import com.guangxia.filmtools.ui.components.SegmentSwitch
import com.guangxia.filmtools.ui.theme.Carbon
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.Danger
import com.guangxia.filmtools.ui.theme.InstrumentShape
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.Panel
import com.guangxia.filmtools.ui.theme.PanelSoft
import com.guangxia.filmtools.ui.theme.Paper
import java.util.Locale

@Composable
fun MeterScreen(viewModel: MainViewModel, settings: AppSettings, reading: MeterReading?, cameraError: String?) {
    val accent = LocalToolAccent.current
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    var priority by rememberSaveable { mutableStateOf(ExposurePriority.APERTURE) }
    var filmIso by rememberSaveable { mutableIntStateOf(400) }
    var apertureIndex by rememberSaveable { mutableIntStateOf(12) }
    var shutterIndex by rememberSaveable { mutableIntStateOf(36) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var isoLocked by rememberSaveable { mutableStateOf(true) }
    var calibrationExpanded by rememberSaveable { mutableStateOf(false) }
    var calibration by remember(settings.evCalibration) { mutableDoubleStateOf(settings.evCalibration) }

    val result = reading?.let {
        if (priority == ExposurePriority.APERTURE) ExposureCalculator.shutterFor(it.ev100, ExposureCalculator.apertures[apertureIndex], filmIso)
        else ExposureCalculator.apertureFor(it.ev100, ExposureCalculator.shutters[shutterIndex], filmIso)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
    ) {
        item { ScreenHeader("测光", "读取场景反射光") }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(ControlShape)
                        .background(Panel),
                ) {
                    when {
                        hasPermission -> CameraPreview(settings.meteringMode, locked, viewModel)
                        else -> PermissionPanel(
                            onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            onSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) },
                        )
                    }
                    if (hasPermission) CameraCornerMask()
                    if (hasPermission && settings.meteringMode == MeteringMode.CENTER) CenterReticle()
                    Row(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatusBadge(reading, cameraError)
                        IconButton(
                            onClick = { locked = !locked; viewModel.setExposureLocked(locked) },
                            modifier = Modifier.background(Carbon.copy(alpha = 0.82f), CircleShape),
                        ) {
                            Icon(
                                if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = if (locked) "解锁曝光" else "锁定曝光",
                                tint = accent,
                            )
                        }
                    }
                }
                SegmentSwitch(
                    options = listOf(MeteringMode.AVERAGE to "全局平均", MeteringMode.CENTER to "中心点"),
                    selected = settings.meteringMode,
                    onSelect = viewModel::setMeteringMode,
                )
                MeterReadoutDeck(reading, result, priority)
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Panel, InstrumentShape).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SegmentSwitch(
                        options = listOf(ExposurePriority.APERTURE to "光圈优先", ExposurePriority.SHUTTER to "快门优先"),
                        selected = priority,
                        onSelect = { priority = it },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        RotaryWheel(
                            label = "胶卷 ISO",
                            values = isoValues.map { "ISO $it" },
                            selectedIndex = isoValues.indexOf(filmIso).coerceAtLeast(0),
                            onIndexChange = { filmIso = isoValues[it] },
                            locked = isoLocked,
                            onLockToggle = { isoLocked = !isoLocked },
                            footer = "",
                            embedded = true,
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.padding(top = 48.dp).width(1.dp).height(120.dp).background(PanelSoft))
                        key(priority) {
                            if (priority == ExposurePriority.APERTURE) {
                                RotaryWheel(
                                    label = "光圈",
                                    values = ExposureCalculator.apertures.map { "f/${formatAperture(it)}" },
                                    selectedIndex = apertureIndex,
                                    onIndexChange = { apertureIndex = it },
                                    embedded = true,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                RotaryWheel(
                                    label = "快门",
                                    values = ExposureCalculator.shutters.map(ExposureCalculator::formatShutter),
                                    selectedIndex = shutterIndex,
                                    onIndexChange = { shutterIndex = it },
                                    embedded = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().background(Panel, ControlShape)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { calibrationExpanded = !calibrationExpanded }
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("设备 EV 校准", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatSigned(calibration), color = accent, style = MaterialTheme.typography.titleMedium)
                            Icon(
                                if (calibrationExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (calibrationExpanded) "收起校准" else "展开校准",
                                tint = Muted,
                            )
                        }
                    }
                    AnimatedVisibility(calibrationExpanded) {
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            HorizontalDivider(color = PanelSoft)
                            Slider(
                                value = calibration.toFloat(),
                                onValueChange = { calibration = (kotlin.math.round(it * 4) / 4.0).coerceIn(-3.0, 3.0) },
                                onValueChangeFinished = { viewModel.setCalibration(calibration) },
                                valueRange = -3f..3f,
                                steps = 23,
                            )
                            Text("用灰卡或可信测光表比对后调整，校准值只保存在本机。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MeterReadoutDeck(reading: MeterReading?, result: ExposureResult?, priority: ExposurePriority) {
    val accent = LocalToolAccent.current
    Column(
        Modifier.fillMaxWidth().background(Panel, InstrumentShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1.4f)) {
                Text(if (priority == ExposurePriority.APERTURE) "推荐快门" else "推荐光圈", color = Muted, style = MaterialTheme.typography.labelLarge)
                Text(
                    result?.displayValue ?: "--",
                    color = Paper,
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                )
            }
            Column(Modifier.weight(0.8f), horizontalAlignment = Alignment.End) {
                Text("EV 100", color = Muted, style = MaterialTheme.typography.labelLarge)
                Text(
                    reading?.ev100?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                    color = accent,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
        HorizontalDivider(color = PanelSoft)
        ExposureDeviationScale(result?.deviationStops)
        Text(
            result?.let { "偏差 ${formatSigned(it.deviationStops)} EV" } ?: "等待稳定读数",
            color = if (result == null || kotlin.math.abs(result.deviationStops) < 0.17) Muted else accent,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExposureDeviationScale(deviation: Double?) {
    val accent = LocalToolAccent.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(16.dp)
            .semantics { stateDescription = deviation?.let { "曝光偏差 ${formatSigned(it)} EV" } ?: "曝光偏差不可用" },
    ) {
        val baselineY = size.height
        repeat(9) { index ->
            val x = size.width * index / 8f
            val major = index == 0 || index == 4 || index == 8
            drawLine(
                color = Muted,
                start = Offset(x, baselineY),
                end = Offset(x, baselineY - if (major) 10.dp.toPx() else 6.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        deviation?.let {
            val x = size.width * (0.5f + it.coerceIn(-0.5, 0.5).toFloat())
            drawLine(accent, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun CameraPreview(mode: MeteringMode, locked: Boolean, viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val controller = remember(previewView, lifecycleOwner) {
        CameraMeterController(context, lifecycleOwner, previewView, viewModel::acceptCameraReading, viewModel::setCameraError)
    }
    DisposableEffect(lifecycleOwner, previewView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> previewView.alpha = 1f
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> previewView.alpha = 0f
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        previewView.alpha = if (
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            1f
        } else {
            0f
        }
        onDispose {
            previewView.alpha = 0f
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(controller) {
        viewModel.setCameraError(null)
        controller.start(mode)
        onDispose { controller.stop() }
    }
    LaunchedEffect(mode) { controller.setMode(mode) }
    LaunchedEffect(locked) { controller.setExposureLocked(locked) }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun CameraCornerMask() {
    Canvas(Modifier.fillMaxSize()) {
        val radius = 12.dp.toPx()
        val roundedBounds = Path().apply {
            addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
        }
        clipPath(roundedBounds, ClipOp.Difference) {
            drawRect(Panel)
        }
    }
}

@Composable
private fun CenterReticle() {
    val accent = LocalToolAccent.current
    Canvas(Modifier.fillMaxSize()) {
        val radius = size.minDimension * 0.075f
        drawCircle(accent, radius, center, style = Stroke(1.5.dp.toPx()))
        drawLine(accent, Offset(center.x - radius - 14.dp.toPx(), center.y), Offset(center.x - radius / 2, center.y), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(accent, Offset(center.x + radius / 2, center.y), Offset(center.x + radius + 14.dp.toPx(), center.y), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(accent, Offset(center.x, center.y - radius - 14.dp.toPx()), Offset(center.x, center.y - radius / 2), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(accent, Offset(center.x, center.y + radius / 2), Offset(center.x, center.y + radius + 14.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun StatusBadge(reading: MeterReading?, error: String?) {
    val accent = LocalToolAccent.current
    val (text, color) = when {
        error != null -> error to Danger
        reading == null -> "正在读取" to accent
        reading.status == ReadingStatus.TOO_DARK -> "光线极暗" to Danger
        reading.status == ReadingStatus.TOO_BRIGHT -> "光线极亮" to Danger
        else -> "读数稳定" to accent
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(Carbon.copy(alpha = 0.82f), ControlShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("需要相机权限才能测光", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text("计算器和胶卷备忘录不受影响。", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRequest) { Text("再次授权") }
            Button(onClick = onSettings, colors = ButtonDefaults.buttonColors(containerColor = Panel)) { Text("系统设置") }
        }
    }
}

private val isoValues = listOf(25, 32, 40, 50, 64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800, 1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400)
private fun formatAperture(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
private fun formatSigned(value: Double) = String.format(Locale.US, "%+.2f", value)
