package com.theemrock.smoothmedia.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ScrollTrack = Color(0x3326343B)
private val ScrollThumb = Color(0xCC19C3E6)

@Composable
fun LibraryListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
            .pointerInput(state) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    val total = state.layoutInfo.totalItemsCount
                    if (total > 0) scope.launch { state.scrollToItem((fraction * (total - 1)).roundToInt()) }
                }
            },
    ) {
        val total = state.layoutInfo.totalItemsCount.coerceAtLeast(1)
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val thumbFraction = (visible.toFloat() / total).coerceIn(.08f, 1f)
        val first = state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)
        ScrollThumb(thumbFraction, first)
    }
}

@Composable
fun LibraryGridScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
            .pointerInput(state) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                    val total = state.layoutInfo.totalItemsCount
                    if (total > 0) scope.launch { state.scrollToItem((fraction * (total - 1)).roundToInt()) }
                }
            },
    ) {
        val total = state.layoutInfo.totalItemsCount.coerceAtLeast(1)
        val visible = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val thumbFraction = (visible.toFloat() / total).coerceIn(.08f, 1f)
        val first = state.firstVisibleItemIndex.toFloat() / (total - visible).coerceAtLeast(1)
        ScrollThumb(thumbFraction, first)
    }
}

@Composable
private fun ScrollThumb(thumbFraction: Float, positionFraction: Float) {
    BoxWithConstraints(modifier = Modifier.fillMaxHeight().background(ScrollTrack)) {
        val thumbHeight = (maxHeight * thumbFraction).coerceAtLeast(24.dp)
        val offset = (maxHeight - thumbHeight) * positionFraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(thumbHeight)
                .offset(y = offset)
                .background(ScrollThumb),
        )
    }
}
