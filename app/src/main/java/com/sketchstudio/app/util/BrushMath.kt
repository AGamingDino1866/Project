package com.sketchstudio.app.util

import androidx.compose.ui.geometry.Offset
import com.sketchstudio.app.model.DrawTool
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure geometry/formula helpers for every brush, shared verbatim by the live
 * Compose preview (MarkupCanvas) and the flattened export renderer
 * (MarkupRenderer). Nothing here depends on Compose drawing APIs or
 * android.graphics, so a brush's math is defined exactly once and can never
 * drift between what the user sees while drawing and what gets saved.
 * Every formula is a deterministic function of a segment/sample index —
 * never true randomness — so preview and export always match.
 */
object BrushMath {
    private val NIB_ANGLE_RADIANS = Math.toRadians(45.0).toFloat()

    /** Per-segment stroke width for BrushFamily.VARIABLE_WIDTH tools. */
    fun variableSegmentWidth(tool: DrawTool, index: Int, segmentStart: Offset, segmentEnd: Offset, baseWidthPx: Float): Float {
        val factor = when (tool) {
            DrawTool.CRAYON -> 0.78f + 0.22f * sin(index * 0.9f)
            DrawTool.CHALK -> 0.62f + 0.38f * sin(index * 1.7f)
            DrawTool.RIBBON -> 0.35f + 0.65f * abs(sin(index * 0.35f))
            DrawTool.CALLIGRAPHY -> {
                val angle = atan2(segmentEnd.y - segmentStart.y, segmentEnd.x - segmentStart.x)
                0.35f + 0.9f * abs(sin(angle - NIB_ANGLE_RADIANS))
            }
            DrawTool.INK -> {
                val speed = hypot((segmentEnd.x - segmentStart.x).toDouble(), (segmentEnd.y - segmentStart.y).toDouble()).toFloat()
                val speedFactor = (speed / (baseWidthPx * 6f)).coerceIn(0f, 1f)
                1.35f - 0.75f * speedFactor
            }
            else -> 1f
        }
        return (baseWidthPx * factor).coerceAtLeast(1f)
    }

    /** Per-segment alpha multiplier, used by textured brushes like Chalk. */
    fun segmentAlphaFactor(tool: DrawTool, index: Int): Float = when (tool) {
        DrawTool.CHALK -> 0.55f + 0.35f * abs(sin(index * 2.3f))
        else -> 1f
    }

    /** Hue (degrees) for the Rainbow brush at a given segment index. */
    fun rainbowHueAt(index: Int): Float = (index * 9f) % 360f

    data class StampPoint(val position: Offset, val angleRadians: Float, val index: Int)

    /** Walks a path in mapped (pixel) space, emitting evenly-spaced points with local direction — used by stamp and soft-dab brushes. */
    fun sampleAlongPath(mapped: List<Offset>, spacing: Float): List<StampPoint> {
        if (mapped.size < 2 || spacing <= 0f) return emptyList()
        val samples = mutableListOf<StampPoint>()
        var carry = 0f
        var index = 0
        for (i in 0 until mapped.size - 1) {
            val a = mapped[i]
            val b = mapped[i + 1]
            val segLen = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
            if (segLen <= 0f) continue
            val angle = atan2(b.y - a.y, b.x - a.x)
            var dist = carry
            while (dist < segLen) {
                val t = dist / segLen
                samples.add(StampPoint(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), angle, index))
                index++
                dist += spacing
            }
            carry = dist - segLen
        }
        return samples
    }

    /** Outline of a 5-point star centered at [center], alternating outer/inner radius. */
    fun starPolygon(center: Offset, outerRadius: Float, innerRadius: Float, rotationRadians: Float): List<Offset> {
        val points = ArrayList<Offset>(10)
        val step = (Math.PI / 5.0).toFloat()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outerRadius else innerRadius
            val angle = rotationRadians + i * step
            points.add(Offset(center.x + r * cos(angle), center.y + r * sin(angle)))
        }
        return points
    }

    fun stampSpacing(tool: DrawTool, baseWidthPx: Float): Float = when (tool) {
        DrawTool.STAR -> (baseWidthPx * 2.6f).coerceAtLeast(12f)
        DrawTool.CONFETTI -> (baseWidthPx * 1.9f).coerceAtLeast(9f)
        else -> (baseWidthPx * 2f).coerceAtLeast(10f)
    }

    fun stampSize(tool: DrawTool, baseWidthPx: Float): Float = when (tool) {
        DrawTool.STAR -> baseWidthPx * 1.7f
        DrawTool.CONFETTI -> baseWidthPx * 1.1f
        else -> baseWidthPx
    }

    /** Deterministic per-stamp rotation (radians) so repeated shapes don't look mechanically identical. */
    fun stampRotation(index: Int): Float = (index * 47) % 360 * (Math.PI.toFloat() / 180f)

    /** Deterministic per-stamp hue shift (degrees) for the multi-color Confetti brush. */
    fun confettiHueShift(index: Int): Float = (index % 5) * 42f

    fun dabSpacing(tool: DrawTool, baseWidthPx: Float): Float = when (tool) {
        DrawTool.AIRBRUSH -> (baseWidthPx * 0.35f).coerceAtLeast(2f)
        DrawTool.WATERCOLOR -> (baseWidthPx * 1.1f).coerceAtLeast(3f)
        else -> baseWidthPx.coerceAtLeast(2f)
    }

    fun dabRadius(tool: DrawTool, baseWidthPx: Float, index: Int): Float = when (tool) {
        DrawTool.AIRBRUSH -> baseWidthPx * (0.5f + 0.35f * abs(sin(index * 1.3f)))
        DrawTool.WATERCOLOR -> baseWidthPx * (1.6f + 0.5f * abs(sin(index * 0.6f)))
        else -> baseWidthPx * 0.5f
    }

    fun dabAlphaFactor(tool: DrawTool, index: Int): Float = when (tool) {
        DrawTool.AIRBRUSH -> 0.14f + 0.06f * abs(sin(index * 2.1f))
        DrawTool.WATERCOLOR -> 0.09f + 0.05f * abs(sin(index * 1.1f))
        else -> 0.15f
    }
}
