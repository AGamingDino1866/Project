package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Two-finger-only pan/zoom, deliberately isolated from single-finger
 * drawing gestures. This watches pointer events on the Initial pass (which
 * runs parent-to-child, before the Main pass single-finger drag detectors
 * further down the tree ever see them) and only starts consuming once 2+
 * pointers are simultaneously down. A single finger is never touched here —
 * it always reaches the drawing/crop-handle gesture beneath completely
 * untouched. If a second finger comes down mid-stroke, this starts
 * consuming from that point on, which cancels the in-progress single-finger
 * gesture (matches how most drawing apps handle an accidental second touch).
 */
fun Modifier.twoFingerTransformGesture(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange != Offset.Zero) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    onGesture(centroid, panChange, zoomChange)
                }
                event.changes.forEach { change -> if (change.pressed) change.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
