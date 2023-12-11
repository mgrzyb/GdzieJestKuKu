package com.fungarium.gdziejestkuku

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun FolloweeButton(
    followee: Followee,
    onClick: () -> Unit,
    onDragStart: (position: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (position: Offset) -> Unit
) {
    var originalPosition by remember { mutableStateOf(Offset.Zero) }

    var isBeingDragged by remember { mutableStateOf(false) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .alpha(if (isBeingDragged) 0.8f else 1f)
            .onGloballyPositioned {
                if (!isBeingDragged) originalPosition = it.positionInWindow();
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragOrigin = it;
                        isBeingDragged = true;
                        onDragStart(originalPosition + dragOrigin)
                    },
                    onDragEnd = {
                        isBeingDragged = false;
                        dragOffset = Offset.Zero;
                        onDragEnd()
                    },
                    onDragCancel = {
                        isBeingDragged = false;
                        dragOffset = Offset.Zero;
                        onDragCancel()
                    })
                { change, dragAmount ->
                    change.consume();
                    dragOffset += dragAmount;
                    var position = originalPosition + dragOrigin + dragOffset;
                    onDrag(position);
                }
            }) {
        Text(followee.nickname)
    }
}
