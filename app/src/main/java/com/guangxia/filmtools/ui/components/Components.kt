package com.guangxia.filmtools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.InstrumentShape
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.RecessShape
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.Panel
import com.guangxia.filmtools.ui.theme.PanelRaised
import com.guangxia.filmtools.ui.theme.PanelSoft

@Composable
fun ScreenHeader(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
fun InstrumentPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = InstrumentShape,
        color = Panel,
        content = { Column(Modifier.padding(16.dp)) { content() } },
    )
}

@Composable
fun <T> SegmentSwitch(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(ControlShape).background(PanelRaised).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RecessShape)
                    .background(if (active) PanelSoft else PanelRaised)
                    .selectable(
                        selected = active,
                        onClick = { onSelect(value) },
                        role = Role.Tab,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.onSurface else Muted,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, modifier: Modifier = Modifier) {
    val accent = LocalToolAccent.current
    Column(modifier) {
        Text(label.uppercase(), color = Muted, style = MaterialTheme.typography.labelLarge)
        Text(value, color = accent, style = MaterialTheme.typography.headlineMedium)
    }
}
