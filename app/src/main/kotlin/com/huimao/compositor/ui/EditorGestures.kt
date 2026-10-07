package com.huimao.compositor.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/**
 * Editor canvas gestures.
 *
 * - Brush tool (or stylus): single finger paints. A second finger going down
 *   mid-stroke cancels the stroke and switches to zoom/pan.
 * - Hand tool: one finger pans, two fingers pan + pinch-zoom.
 * - Tap with the brush = one dab.
 *
 * All positions are in the Canvas's local (screen) pixels; the caller maps
 * them into document space.
 */
fun Modifier.editorGestures(
    brushActive: Boolean,
    onStrokeStart: (screen: Offset, pressure: Float, stylus: Boolean) -> Unit,
    onStrokeMove: (screen: Offset, pressure: Float, stylus: Boolean) -> Unit,
    onStrokeEnd: (commit: Boolean) -> Unit,
    onZoomPan: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
): Modifier = pointerInput(brushActive, onStrokeStart, onStrokeMove, onStrokeEnd, onZoomPan) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val stylusDown = down.type == PointerType.Stylus || down.type == PointerType.Eraser
        if (brushActive || stylusDown) {
            strokeLoop(
                down = down,
                stylusDown = stylusDown,
                onStrokeStart = onStrokeStart,
                onStrokeMove = onStrokeMove,
                onStrokeEnd = onStrokeEnd,
                onZoomPan = onZoomPan,
            )
        } else {
            transformLoop(onZoomPan)
        }
    }
}

private fun pressureOf(change: PointerInputChange, stylus: Boolean): Float =
    if (stylus) change.pressure.coerceIn(0f, 1f) else 1f

private suspend fun AwaitPointerEventScope.strokeLoop(
    down: PointerInputChange,
    stylusDown: Boolean,
    onStrokeStart: (Offset, Float, Boolean) -> Unit,
    onStrokeMove: (Offset, Float, Boolean) -> Unit,
    onStrokeEnd: (Boolean) -> Unit,
    onZoomPan: (Offset, Offset, Float) -> Unit,
) {
    val id = down.id
    val touchSlop = viewConfiguration.touchSlop
    var started = false
    var slopAccum = 0f
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size >= 2) {
            // Second finger: give up the stroke, switch to zoom/pan.
            if (started) onStrokeEnd(false)
            event.changes.forEach { it.consume() }
            transformLoop(onZoomPan)
            return
        }
        val c = event.changes.find { it.id == id }
        if (c == null || !c.pressed) {
            if (started) {
                onStrokeEnd(true)
            } else {
                // Tap = single dab.
                onStrokeStart(down.position, pressureOf(down, stylusDown), stylusDown)
                onStrokeEnd(true)
            }
            return
        }
        val pressure = pressureOf(c, stylusDown)
        if (!started) {
            slopAccum += c.positionChange().getDistance()
            if (slopAccum > touchSlop) {
                started = true
                onStrokeStart(down.position, pressureOf(down, stylusDown), stylusDown)
                onStrokeMove(c.position, pressure, stylusDown)
                c.consume()
            }
        } else {
            onStrokeMove(c.position, pressure, stylusDown)
            c.consume()
        }
    }
}

private suspend fun AwaitPointerEventScope.transformLoop(
    onZoomPan: (Offset, Offset, Float) -> Unit,
) {
    var prevCentroid: Offset? = null
    var prevDist = 0f
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) return
        val centroid = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } / pressed.size.toFloat()
        val dist = if (pressed.size >= 2) {
            (pressed[0].position - pressed[1].position).getDistance()
        } else 0f
        val pc = prevCentroid
        if (pc == null) {
            prevCentroid = centroid
            prevDist = dist
        } else {
            val zoom = if (pressed.size >= 2 && prevDist > 0f && dist > 0f) dist / prevDist else 1f
            if (zoom != 1f || centroid != pc) {
                onZoomPan(centroid, centroid - pc, zoom)
            }
            prevCentroid = centroid
            prevDist = dist
        }
        event.changes.forEach { it.consume() }
    }
}
