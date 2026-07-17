package com.guangxia.filmtools.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guangxia.filmtools.ui.theme.ControlShape
import com.guangxia.filmtools.ui.theme.LocalToolAccent
import com.guangxia.filmtools.ui.theme.Muted
import com.guangxia.filmtools.ui.theme.PanelRaised
import com.guangxia.filmtools.ui.theme.Paper
import com.guangxia.filmtools.ui.theme.RecessShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HorizontalPowerDial(
    label: String,
    values: List<String>,
    details: List<String>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(values.isNotEmpty() && values.size == details.size) { "功率拨盘数据无效" }
    val accent = LocalToolAccent.current
    val haptics = LocalHapticFeedback.current
    val safeIndex = selectedIndex.coerceIn(0, values.lastIndex)
    val latestIndex by rememberUpdatedState(safeIndex)
    val latestOnIndexChange by rememberUpdatedState(onIndexChange)
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

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = Paper,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
        )
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(ControlShape)
                .background(PanelRaised)
                .semantics {
                    stateDescription = values[safeIndex]
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = safeIndex.toFloat(),
                        range = 0f..values.lastIndex.toFloat(),
                        steps = (values.size - 2).coerceAtLeast(0),
                    )
                    setProgress { target ->
                        latestOnIndexChange(target.roundToInt().coerceIn(0, values.lastIndex))
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction("前一档") {
                            if (safeIndex > 0) latestOnIndexChange(safeIndex - 1)
                            safeIndex > 0
                        },
                        CustomAccessibilityAction("后一档") {
                            if (safeIndex < values.lastIndex) latestOnIndexChange(safeIndex + 1)
                            safeIndex < values.lastIndex
                        },
                    )
                },
        ) {
            val itemWidth = maxWidth * 0.52f
            val sidePadding = (maxWidth - itemWidth) / 2
            LazyRow(
                state = listState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = sidePadding),
                modifier = Modifier.fillMaxSize(),
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
                    val scale = 0.64f + 0.36f * proximity
                    Box(
                        Modifier.width(itemWidth).height(88.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    ) {
                        Text(
                            value,
                            color = lerp(Muted, accent, proximity),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (value.length >= 8) 25.sp else 30.sp,
                                letterSpacing = (-0.8).sp,
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                        )
                        Text(
                            if (index == safeIndex) details[index] else "",
                            color = Muted,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.align(Alignment.BottomCenter).height(24.dp).padding(top = 2.dp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.TopCenter).size(width = 2.dp, height = 10.dp).background(accent, RecessShape))
            Box(Modifier.align(Alignment.BottomCenter).size(width = 2.dp, height = 10.dp).background(accent, RecessShape))
        }
    }
}
