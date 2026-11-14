package com.brbrs.koto.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press drag reordering for a LazyColumn, without pulling in a library.
 *
 * Only rows the caller marks as draggable take part, so section headers and footers stay put.
 * The new order is reported once on drop rather than on every frame, because each move is a
 * CalDAV write and reporting continuously would mean dozens of PUTs for one drag.
 */
class ReorderState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    /** Key of the row being dragged, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** Vertical offset of the dragged row, in pixels. */
    var dragOffset by mutableStateOf(0f)
        private set

    private fun itemAt(key: Any?) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    fun onDragStart(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    fun onDrag(deltaY: Float) {
        dragOffset += deltaY
        val dragged = itemAt(draggingKey) ?: return
        val center = dragged.offset + dragged.size / 2f + dragOffset

        // Find the row the dragged one is now hovering over, ignoring itself and anything
        // the caller did not mark draggable (headers have no entry in the order list).
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { candidate ->
            candidate.key != draggingKey &&
                center.toInt() in candidate.offset..(candidate.offset + candidate.size)
        } ?: return

        onMove(dragged.index, target.index)
        // Keep the dragged row under the finger after the list shifts beneath it.
        dragOffset += (dragged.offset - (itemAt(draggingKey)?.offset ?: dragged.offset))

        // Scroll when dragging near either edge, or long lists can't be reordered far.
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val edge = 120
        val overTop = (dragged.offset + dragOffset) - listState.layoutInfo.viewportStartOffset
        val overBottom = (dragged.offset + dragged.size + dragOffset) - (viewport - edge)
        when {
            overTop < edge -> scope.launch { listState.scrollBy(-12f) }
            overBottom > 0 -> scope.launch { listState.scrollBy(12f) }
        }
    }

    fun onDragEnd() {
        draggingKey = null
        dragOffset = 0f
        onDrop()
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    onMove: (from: Int, to: Int) -> Unit,
    onDrop: () -> Unit,
): ReorderState {
    val scope = rememberCoroutineScope()
    return remember(listState) { ReorderState(listState, scope, onMove, onDrop) }
}

/** Attach to a row to make it draggable. [key] must match the key given to `items`. */
fun Modifier.reorderable(state: ReorderState, key: Any, enabled: Boolean): Modifier =
    if (!enabled) this else this.pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, amount -> change.consume(); state.onDrag(amount.y) },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        )
    }
