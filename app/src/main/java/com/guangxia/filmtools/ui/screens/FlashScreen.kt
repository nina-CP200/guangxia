package com.guangxia.filmtools.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guangxia.filmtools.core.DistanceUnit
import com.guangxia.filmtools.core.ExposureCalculator
import com.guangxia.filmtools.core.FlashCalculator
import com.guangxia.filmtools.core.FlashPower
import com.guangxia.filmtools.core.FlashPowerSettings
import com.guangxia.filmtools.ui.components.HorizontalPowerDial
import com.guangxia.filmtools.ui.components.InstrumentPanel
import com.guangxia.filmtools.ui.components.ScreenHeader
import com.guangxia.filmtools.ui.components.SegmentSwitch
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.Danger
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.PanelRaised
import java.util.Locale

@Composable
fun FlashScreen(unit: DistanceUnit, onUnitChange: (DistanceUnit) -> Unit) {
    var guideNumber by rememberSaveable { mutableStateOf("40") }
    var iso by rememberSaveable { mutableStateOf("100") }
    var guideLocked by rememberSaveable { mutableStateOf(true) }
    var isoLocked by rememberSaveable { mutableStateOf(true) }
    var apertureIndex by rememberSaveable { mutableIntStateOf(ExposureCalculator.apertures.indexOf(8.0)) }
    var distanceTenthMeter by rememberSaveable { mutableIntStateOf(50) }
    var powerStepTenths by rememberSaveable { mutableIntStateOf(3) }
    var powerIndex by rememberSaveable { mutableIntStateOf(0) }
    var controlSource by rememberSaveable { mutableStateOf(FlashControlSource.PARAMETERS) }

    val powerStep = powerStepTenths / 10.0
    val powerOptions = remember(powerStepTenths) { FlashPowerSettings.options(powerStep) }
    val selectedPower = powerOptions[powerIndex.coerceIn(0, powerOptions.lastIndex)]
    val aperture = ExposureCalculator.apertures[apertureIndex.coerceIn(0, ExposureCalculator.apertures.lastIndex)]
    val distanceMeters = distanceTenthMeter.coerceAtLeast(1) / 10.0
    val distance = FlashCalculator.convertDistance(distanceMeters, DistanceUnit.METERS, unit)
    val fullPowerGuideNumber = guideNumber.toDoubleOrNull()
    val shootingIso = iso.toDoubleOrNull()
    val requiredPowerRatio = if (fullPowerGuideNumber != null && shootingIso != null && fullPowerGuideNumber > 0.0 && shootingIso > 0.0) {
        FlashCalculator.powerRatioFor(fullPowerGuideNumber, aperture, distance, shootingIso)
    } else null
    val powerDrivenAperture = if (fullPowerGuideNumber != null && shootingIso != null && fullPowerGuideNumber > 0.0 && shootingIso > 0.0) {
        FlashCalculator.apertureForPower(fullPowerGuideNumber, selectedPower.ratio, distance, shootingIso)
    } else null
    val rangeWarning = when (controlSource) {
        FlashControlSource.PARAMETERS -> when {
            requiredPowerRatio != null && requiredPowerRatio > powerOptions.first().ratio -> "所需功率高于 1/1"
            requiredPowerRatio != null && requiredPowerRatio < powerOptions.last().ratio -> "所需功率低于 1/256"
            else -> null
        }
        FlashControlSource.POWER -> when {
            powerDrivenAperture != null && powerDrivenAperture < ExposureCalculator.apertures.first() -> "所需光圈小于 f/1"
            powerDrivenAperture != null && powerDrivenAperture > ExposureCalculator.apertures.last() -> "所需光圈大于 f/32"
            else -> null
        }
    }

    LaunchedEffect(fullPowerGuideNumber, shootingIso, apertureIndex, distance, powerStepTenths, powerIndex, controlSource) {
        if (fullPowerGuideNumber == null || shootingIso == null || fullPowerGuideNumber <= 0.0 || shootingIso <= 0.0) return@LaunchedEffect
        if (controlSource == FlashControlSource.PARAMETERS) {
            val requiredRatio = FlashCalculator.powerRatioFor(fullPowerGuideNumber, aperture, distance, shootingIso)
            val target = FlashCalculator.closestPowerIndex(powerOptions.map(FlashPower::ratio), requiredRatio)
            if (powerIndex != target) powerIndex = target
        } else {
            val exactAperture = FlashCalculator.apertureForPower(fullPowerGuideNumber, selectedPower.ratio, distance, shootingIso)
            val target = ExposureCalculator.apertures.indices.minBy { index -> kotlin.math.abs(kotlin.math.ln(ExposureCalculator.apertures[index] / exactAperture)) }
            if (apertureIndex != target) apertureIndex = target
        }
    }

    fun changeUnit(newUnit: DistanceUnit) {
        if (newUnit == unit) return
        guideNumber.toDoubleOrNull()?.let { guideNumber = formatInput(FlashCalculator.convertDistance(it, unit, newUnit)) }
        onUnitChange(newUnit)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("闪光", "距离、光圈与功率联动") }
        item {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SegmentSwitch(
                    options = listOf(DistanceUnit.METERS to "米制 · m", DistanceUnit.FEET to "英制 · ft"),
                    selected = unit,
                    onSelect = ::changeUnit,
                )
                InstrumentPanel {
                    Text("曝光联动", style = MaterialTheme.typography.titleMedium)
                    HorizontalPowerDial(
                        label = "光圈",
                        values = ExposureCalculator.apertures.map { "f/${formatInput(it)}" },
                        details = ExposureCalculator.apertures.map { "" },
                        selectedIndex = apertureIndex,
                        onIndexChange = { index ->
                            apertureIndex = index
                            controlSource = FlashControlSource.PARAMETERS
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    DistanceStepper(
                        distanceMeters = distanceMeters,
                        unit = unit,
                        onDecrease = {
                            distanceTenthMeter = (distanceTenthMeter - 1).coerceAtLeast(1)
                            controlSource = FlashControlSource.PARAMETERS
                        },
                        onIncrease = {
                            distanceTenthMeter = (distanceTenthMeter + 1).coerceAtMost(1000)
                            controlSource = FlashControlSource.PARAMETERS
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("功率步长", color = Muted, style = MaterialTheme.typography.labelLarge)
                        SegmentSwitch(
                            options = listOf(1 to "0.1 EV", 3 to "0.3 EV"),
                            selected = powerStepTenths,
                            onSelect = { step ->
                                val previousRatio = selectedPower.ratio
                                val nextOptions = FlashPowerSettings.options(step / 10.0)
                                powerStepTenths = step
                                powerIndex = FlashCalculator.closestPowerIndex(nextOptions.map(FlashPower::ratio), previousRatio)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalPowerDial(
                        label = "闪光功率",
                        values = powerOptions.map(FlashPowerSettings::displayLabel),
                        details = powerOptions.map { "−${FlashPowerSettings.displayEv(it.stopsBelowFull)} EV" },
                        selectedIndex = powerIndex,
                        onIndexChange = { index ->
                            powerIndex = index
                            controlSource = FlashControlSource.POWER
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    rangeWarning?.let {
                        Text(it, color = Danger, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                InstrumentPanel {
                    Text("已知条件", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FlashFieldInput("闪光指数", if (unit == DistanceUnit.METERS) "GN · m" else "GN · ft", guideNumber, { guideNumber = it }, guideLocked, { guideLocked = !guideLocked }, Modifier.weight(1f))
                        FlashFieldInput("拍摄 ISO", "感光度", iso, { iso = it }, isoLocked, { isoLocked = !isoLocked }, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private enum class FlashControlSource { PARAMETERS, POWER }

@Composable
private fun DistanceStepper(
    distanceMeters: Double,
    unit: DistanceUnit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalToolAccent.current
    Column(modifier.fillMaxWidth().background(PanelRaised, ControlShape).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("距离", color = Muted, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onDecrease, modifier = Modifier.background(accent.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)) { Icon(Icons.Rounded.Remove, "距离减 0.1 米", tint = accent) }
            Text(formatDistance(distanceMeters, unit), color = accent, style = MaterialTheme.typography.headlineLarge)
            IconButton(onClick = onIncrease, modifier = Modifier.background(accent.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)) { Icon(Icons.Rounded.Add, "距离加 0.1 米", tint = accent) }
        }
    }
}

@Composable
private fun FlashFieldInput(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    locked: Boolean,
    onLockToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalToolAccent.current
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (input.length <= 10) onValueChange(input.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        supportingText = { Text(if (locked) "$suffix · 已锁定" else suffix) },
        singleLine = true,
        readOnly = locked,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            IconButton(onClick = onLockToggle) {
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, contentDescription = if (locked) "解锁 $label" else "锁定 $label", tint = if (locked) accent else Muted)
            }
        },
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = PanelRaised,
            unfocusedContainerColor = PanelRaised,
        ),
        modifier = modifier,
    )
}

private fun formatInput(value: Double): String = if (kotlin.math.abs(value - value.toInt()) < 0.005) value.toInt().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun formatDistance(meters: Double, unit: DistanceUnit): String {
    val value = FlashCalculator.convertDistance(meters, DistanceUnit.METERS, unit)
    return "${formatInput(value)} ${if (unit == DistanceUnit.METERS) "m" else "ft"}"
}
