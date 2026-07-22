package com.guangxia.filmtools.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.guangxia.filmtools.core.FilmCategory
import com.guangxia.filmtools.core.FilmReciprocityProfile
import com.guangxia.filmtools.core.ReciprocityCalculator
import com.guangxia.filmtools.core.ReciprocityResult
import com.guangxia.filmtools.core.ReciprocitySource
import com.guangxia.filmtools.ui.components.InstrumentPanel
import com.guangxia.filmtools.ui.components.ScreenHeader
import com.guangxia.filmtools.ui.components.SegmentSwitch
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.Danger
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.PanelRaised
import com.guangxia.filmtools.ui.theme.PanelSoft
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciprocityScreen() {
    val accent = LocalToolAccent.current
    val profiles = ReciprocityCalculator.profiles
    var selectedId by rememberSaveable { mutableStateOf("kodak-portra-400") }
    var meteredTime by rememberSaveable { mutableStateOf("10") }
    var customEnabled by rememberSaveable { mutableStateOf(false) }
    var customName by rememberSaveable { mutableStateOf("我的胶卷") }
    var customExponent by rememberSaveable { mutableStateOf("1.31") }
    var customOnset by rememberSaveable { mutableStateOf("1") }
    var menuExpanded by remember { mutableStateOf(false) }

    val selectedProfile = profiles.firstOrNull { it.id == selectedId } ?: profiles.first()
    val customProfile = remember(customName, customExponent, customOnset) {
        runCatching {
            ReciprocityCalculator.customProfile(
                name = customName,
                exponent = customExponent.toDouble(),
                onsetSeconds = customOnset.toDouble(),
            )
        }.getOrNull()
    }
    val activeProfile = if (customEnabled) customProfile else selectedProfile
    val inputSeconds = meteredTime.toDoubleOrNull()?.takeIf { it > 0.0 }
    val customError = if (customEnabled && customProfile == null) "请填写有效的名称、P 参数和起始秒数。" else null
    val result = if (activeProfile != null && inputSeconds != null) {
        runCatching { ReciprocityCalculator.calculate(activeProfile, inputSeconds) }.getOrNull()
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("倒易律", "长曝光补偿与色彩提示") }
        item {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SegmentSwitch(
                    options = listOf(false to "资料库", true to "自定义"),
                    selected = customEnabled,
                    onSelect = { customEnabled = it },
                )
                if (customEnabled) {
                    CustomProfilePanel(
                        name = customName,
                        onNameChange = { customName = it.take(24) },
                        exponent = customExponent,
                        onExponentChange = { customExponent = cleanDecimal(it) },
                        onset = customOnset,
                        onOnsetChange = { customOnset = cleanDecimal(it) },
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = menuExpanded,
                        onExpandedChange = { menuExpanded = !menuExpanded },
                    ) {
                        FilmSelector(
                            profile = selectedProfile,
                            expanded = menuExpanded,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                                profiles.groupBy { it.category }.forEach { (category, categoryProfiles) ->
                                    Text(
                                        category.label,
                                        color = Muted,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    categoryProfiles.forEach { profile ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(profile.name)
                                                    Text(profile.manufacturer, color = Muted, style = MaterialTheme.typography.labelLarge)
                                                }
                                            },
                                            onClick = {
                                                selectedId = profile.id
                                                menuExpanded = false
                                            },
                                            trailingIcon = { SourceBadge(profile.source) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                InstrumentPanel {
                    Text("测光时间", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = meteredTime,
                        onValueChange = { meteredTime = cleanDecimal(it) },
                        label = { Text("原始曝光时间") },
                        suffix = { Text("秒") },
                        supportingText = { Text(if (inputSeconds == null) "请输入大于 0 的秒数" else "以测光表读数为准，不含滤镜补偿") },
                        isError = meteredTime.isNotEmpty() && inputSeconds == null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = ControlShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = PanelRaised,
                            unfocusedContainerColor = PanelRaised,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("1", "4", "8", "15", "30", "60", "120").forEach { value ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (meteredTime == value) PanelSoft else PanelRaised,
                                modifier = Modifier.clickable { meteredTime = value },
                            ) {
                                Text("$value s", modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = if (meteredTime == value) accent else Muted)
                            }
                        }
                    }
                }
                ResultPanel(result, activeProfile, customError)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilmSelector(profile: FilmReciprocityProfile, expanded: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalToolAccent.current
    OutlinedTextField(
        value = "${profile.manufacturer} · ${profile.name}",
        onValueChange = {},
        readOnly = true,
        label = { Text("胶卷") },
        supportingText = { Text(profile.category.label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = PanelRaised,
            unfocusedContainerColor = PanelRaised,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultPanel(result: ReciprocityResult?, profile: FilmReciprocityProfile?, inputError: String? = null) {
    val source = result?.source ?: profile?.source ?: if (inputError != null) ReciprocitySource.CUSTOM else ReciprocitySource.REFERENCE_ONLY
    InstrumentPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("修正结果", style = MaterialTheme.typography.titleMedium)
            SourceBadge(source)
        }
        when {
            inputError != null -> Text(inputError, color = Danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            result == null -> Text("输入有效的曝光时间后显示结果。", color = Muted, modifier = Modifier.padding(top = 12.dp))
            result.correctedSeconds == null -> {
                Text("暂无可靠的自动修正值", color = LocalToolAccent.current, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
                result.warning?.let { NoticeText(it) }
            }
            else -> {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ResultValue("测光", formatDuration(result.meteredSeconds), Modifier.weight(1f))
                    Text("→", color = Muted, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
                    ResultValue("建议曝光", formatDuration(result.correctedSeconds), Modifier.weight(1f))
                }
                result.exposureStops?.let { ResultValue("增加补偿", "+${formatStops(it)} EV", Modifier.padding(top = 14.dp)) }
                result.filter?.takeIf { it != "不建议" }?.let {
                    NoticeText("色彩补偿滤镜：$it")
                }
                result.warning?.let { NoticeText(it) }
            }
        }
        profile?.let {
            HorizontalDivider(color = PanelSoft, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
            Text(it.sourceNote, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ResultValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelLarge)
        Text(value, color = LocalToolAccent.current, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun NoticeText(text: String) {
    Text(
        text,
        color = if (text.contains("暂无") || text.contains("超出")) Danger else Muted,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun SourceBadge(source: ReciprocitySource) {
    val accent = LocalToolAccent.current
    val color = when (source) {
        ReciprocitySource.OFFICIAL -> accent
        ReciprocitySource.COMMUNITY_ESTIMATE -> Color(0xFFE5B567)
        ReciprocitySource.CUSTOM -> Color(0xFFB6A6E8)
        ReciprocitySource.REFERENCE_ONLY -> Muted
    }
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(source.label, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun CustomProfilePanel(
    name: String,
    onNameChange: (String) -> Unit,
    exponent: String,
    onExponentChange: (String) -> Unit,
    onset: String,
    onOnsetChange: (String) -> Unit,
) {
    InstrumentPanel {
        Text("自定义倒易律参数", style = MaterialTheme.typography.titleMedium)
        Text("使用厂家资料中的 P 参数；没有资料时请把结果当作估算。", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("胶卷名称") },
            singleLine = true,
            shape = ControlShape,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalField("P 参数", exponent, onExponentChange, Modifier.weight(1f))
            DecimalField("起始秒数", onset, onOnsetChange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DecimalField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = ControlShape,
        colors = fieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalToolAccent.current,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = PanelRaised,
    unfocusedContainerColor = PanelRaised,
)

private fun cleanDecimal(value: String): String = value.filter { it.isDigit() || it == '.' }.let { input ->
    val dot = input.indexOf('.')
    if (dot < 0) input.take(12) else input.substring(0, dot + 1) + input.substring(dot + 1).replace(".", "").take(6)
}

private fun formatDuration(seconds: Double): String = when {
    seconds < 1.0 -> "${String.format(Locale.US, "%.2f", seconds).trimEnd('0').trimEnd('.')} s"
    seconds < 60.0 -> "${formatShortNumber(seconds)} s"
    seconds < 3600.0 -> "${(seconds / 60).toInt()}分 ${(seconds % 60).toInt()}秒"
    else -> "${(seconds / 3600).toInt()}时 ${((seconds % 3600) / 60).toInt()}分"
}

private fun formatStops(value: Double): String = String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')

private fun formatShortNumber(value: Double): String = if (kotlin.math.abs(value - value.toInt()) < 0.005) {
    value.toInt().toString()
} else {
    String.format(Locale.US, "%.1f", value).trimEnd('0').trimEnd('.')
}
