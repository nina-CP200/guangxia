package com.guangxia.filmtools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.Panel
import com.guangxia.filmtools.ui.theme.PanelRaised
import com.guangxia.filmtools.ui.theme.Paper
import com.guangxia.filmtools.ui.theme.RecessShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RotaryWheel(
    label: String,
    values: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onLockToggle: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    footer: String = if (locked) "已锁定" else "",
    embedded: Boolean = false,
) {
    require(values.isNotEmpty()) { "转盘至少需要一个档位" }
    val accent = LocalToolAccent.current
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, values.lastIndex)
    val latestIndex by rememberUpdatedState(safeIndex)
    val latestOnIndexChange by rememberUpdatedState(onIndexChange)

    val rowHeight = 40.dp
    val wheelHeight = 120.dp
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    var lastHapticIndex by remember(values.size) { mutableIntStateOf(safeIndex) }
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(listState, values.size) {
        snapshotFlow { listState.layoutInfo }
            .mapNotNull { layout ->
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                layout.visibleItemsInfo.minByOrNull { item ->
                    abs(item.offset + item.size / 2 - viewportCenter)
                }?.index
            }
            .distinctUntilChanged()
            .collect { centeredIndex ->
                if (!programmaticScroll) {
                    if (centeredIndex != latestIndex) latestOnIndexChange(centeredIndex)
                    if (listState.isScrollInProgress && centeredIndex != lastHapticIndex) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                lastHapticIndex = centeredIndex
            }
    }

    // Automatic calculation changes are shown as a short captured movement toward the
    // new detent. Active finger gestures always retain control of the list.
    LaunchedEffect(safeIndex, values.size) {
        if (!listState.isScrollInProgress) {
            val layout = listState.layoutInfo
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val centeredIndex = layout.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - viewportCenter)
            }?.index
            if (centeredIndex != null && centeredIndex != safeIndex) {
                programmaticScroll = true
                try {
                    listState.animateScrollToItem(safeIndex)
                } finally {
                    programmaticScroll = false
                    lastHapticIndex = safeIndex
                }
            }
        }
    }

    val frameModifier = if (embedded) {
        modifier.padding(horizontal = 4.dp)
    } else {
        modifier.background(PanelRaised, ControlShape).padding(12.dp)
    }

    Column(frameModifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = Paper, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onClear != null) {
                    TextButton(
                        onClick = onClear,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("清空", color = Muted, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (onLockToggle != null) {
                    IconButton(onClick = onLockToggle, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (locked) "解锁 $label" else "锁定 $label",
                            tint = if (locked) accent else Muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(wheelHeight)
                .clip(RecessShape)
                .background(Panel)
                .semantics {
                    stateDescription = values[safeIndex]
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = safeIndex.toFloat(),
                        range = 0f..values.lastIndex.toFloat(),
                        steps = (values.size - 2).coerceAtLeast(0),
                    )
                    if (!locked) {
                        setProgress { target ->
                            latestOnIndexChange(target.roundToInt().coerceIn(0, values.lastIndex))
                            true
                        }
                        customActions = listOf(
                            CustomAccessibilityAction("上一档") {
                                if (safeIndex > 0) latestOnIndexChange(safeIndex - 1)
                                safeIndex > 0
                            },
                            CustomAccessibilityAction("下一档") {
                                if (safeIndex < values.lastIndex) latestOnIndexChange(safeIndex + 1)
                                safeIndex < values.lastIndex
                            },
                        )
                    }
                },
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = flingBehavior,
                userScrollEnabled = !locked,
                contentPadding = PaddingValues(vertical = rowHeight),
                modifier = Modifier.fillMaxWidth().height(wheelHeight),
            ) {
                itemsIndexed(values, key = { index, _ -> index }) { index, value ->
                    val proximity by remember(index, listState) {
                        derivedStateOf {
                            val layout = listState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                                ?: return@derivedStateOf 0f
                            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                            val itemCenter = item.offset + item.size / 2f
                            (1f - abs(itemCenter - viewportCenter) / item.size.coerceAtLeast(1)).coerceIn(0f, 1f)
                        }
                    }
                    val scale = 0.68f + 0.32f * proximity
                    val centerColor = if (locked) Muted else Paper
                    Box(
                        Modifier.fillMaxWidth().height(rowHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value,
                            color = lerp(Muted, centerColor, proximity),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = 1.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                },
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.CenterStart).size(width = 12.dp, height = 2.dp).background(accent, RecessShape))
            Box(Modifier.align(Alignment.CenterEnd).size(width = 12.dp, height = 2.dp).background(accent, RecessShape))
        }
        if (footer.isNotBlank()) {
            Text(
                footer,
                color = Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
