package com.sketchstudio.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.sketchstudio.app.model.BrushFamily
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.model.family
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Renders markup elements (stored in normalized [0, 1] coordinates) directly
 * onto a plain android.graphics.Bitmap, used for the final flattened export.
 */
object MarkupRenderer {

    fun renderElements(bitmap: Bitmap, elements: List<MarkupElement>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val w = result.width.toFloat()
        val h = result.height.toFloat()
        elements.forEach { drawElement(canvas, it, w, h) }
        return result
    }

    private fun drawElement(canvas: Canvas, element: MarkupElement, w: Float, h: Float) {
        when (element) {
            is MarkupElement.PathElement -> drawPathElement(canvas, element, w, h)
            is MarkupElement.ShapeElement -> drawShapeElement(canvas, element, w, h)
            is MarkupElement.TextElement -> drawTextElement(canvas, element, w, h)
        }
    }

    private fun drawPathElement(canvas: Canvas, element: MarkupElement.PathElement, w: Float, h: Float) {
        if (element.points.size < 2) return
        val mapped = element.points.map { Offset(it.x * w, it.y * h) }
        val baseWidth = element.strokeWidthFraction * w

        when (element.tool.family()) {
            BrushFamily.VARIABLE_WIDTH -> drawVariableWidthPath(canvas, mapped, element.color, element.alpha, baseWidth, element.tool)
            BrushFamily.GLOW -> drawGlowPath(canvas, mapped, element.color, element.alpha, baseWidth)
            BrushFamily.RAINBOW -> drawRainbowPath(canvas, mapped, element.alpha, baseWidth)
            BrushFamily.DASHED -> drawDashedPath(canvas, mapped, element.color, element.alpha, baseWidth)
            BrushFamily.STAMP -> drawStampPath(canvas, mapped, element.color, element.alpha, baseWidth, element.tool)
            BrushFamily.SOFT_DABS -> drawSoftDabPath(canvas, mapped, element.color, element.alpha, baseWidth, element.tool)
            BrushFamily.CONSTANT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = element.color.toArgb()
                    alpha = (element.alpha * 255).toInt().coerceIn(0, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = baseWidth
                    strokeCap = if (element.tool == DrawTool.MARKER) Paint.Cap.SQUARE else Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawPath(smoothedPath(mapped), paint)
            }
        }
    }

    /** Mirrors MarkupCanvas's smoothedPath() exactly so export matches the live preview. */
    private fun smoothedPath(mapped: List<Offset>): Path {
        val path = Path()
        if (mapped.isEmpty()) return path
        path.moveTo(mapped.first().x, mapped.first().y)
        if (mapped.size < 3) {
            for (i in 1 until mapped.size) path.lineTo(mapped[i].x, mapped[i].y)
            return path
        }
        for (i in 1 until mapped.size - 1) {
            val curr = mapped[i]
            val next = mapped[i + 1]
            path.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
        }
        val last = mapped.last()
        path.lineTo(last.x, last.y)
        return path
    }

    /** Mirrors MarkupCanvas's drawVariableWidthStroke(), using the same BrushMath formulas. */
    private fun drawVariableWidthPath(canvas: Canvas, mapped: List<Offset>, color: Color, alpha: Float, baseWidthPx: Float, tool: DrawTool) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (i in 0 until mapped.size - 1) {
            val a = mapped[i]
            val b = mapped[i + 1]
            paint.strokeWidth = BrushMath.variableSegmentWidth(tool, i, a, b, baseWidthPx)
            paint.alpha = ((alpha * BrushMath.segmentAlphaFactor(tool, i)).coerceIn(0f, 1f) * 255).toInt()
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    /** Mirrors MarkupCanvas's drawGlowStroke(): soft wide underlay plus a bright near-white core. */
    private fun drawGlowPath(canvas: Canvas, mapped: List<Offset>, color: Color, alpha: Float, baseWidthPx: Float) {
        val path = smoothedPath(mapped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        paint.color = color.toArgb()
        paint.alpha = (alpha * 0.3f * 255).toInt()
        paint.strokeWidth = baseWidthPx * 3.2f
        canvas.drawPath(path, paint)

        paint.alpha = (alpha * 0.55f * 255).toInt()
        paint.strokeWidth = baseWidthPx * 1.8f
        canvas.drawPath(path, paint)

        val core = lerp(color, Color.White, 0.55f)
        paint.color = core.toArgb()
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        paint.strokeWidth = (baseWidthPx * 0.5f).coerceAtLeast(1f)
        canvas.drawPath(path, paint)
    }

    /** Mirrors MarkupCanvas's drawRainbowStroke(). */
    private fun drawRainbowPath(canvas: Canvas, mapped: List<Offset>, alpha: Float, baseWidthPx: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = baseWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        }
        val hsv = floatArrayOf(0f, 0.85f, 0.95f)
        for (i in 0 until mapped.size - 1) {
            hsv[0] = BrushMath.rainbowHueAt(i)
            val a = paint.alpha
            paint.color = android.graphics.Color.HSVToColor(hsv)
            paint.alpha = a
            canvas.drawLine(mapped[i].x, mapped[i].y, mapped[i + 1].x, mapped[i + 1].y, paint)
        }
    }

    /** Mirrors MarkupCanvas's drawDashedStroke(). */
    private fun drawDashedPath(canvas: Canvas, mapped: List<Offset>, color: Color, alpha: Float, baseWidthPx: Float) {
        val dashLen = (baseWidthPx * 2.2f).coerceAtLeast(6f)
        val gapLen = (baseWidthPx * 1.6f).coerceAtLeast(5f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = baseWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            pathEffect = DashPathEffect(floatArrayOf(dashLen, gapLen), 0f)
        }
        canvas.drawPath(smoothedPath(mapped), paint)
    }

    /** Mirrors MarkupCanvas's drawStampStroke(): Star Stamp / Confetti. */
    private fun drawStampPath(canvas: Canvas, mapped: List<Offset>, color: Color, alpha: Float, baseWidthPx: Float, tool: DrawTool) {
        val spacing = BrushMath.stampSpacing(tool, baseWidthPx)
        val size = BrushMath.stampSize(tool, baseWidthPx)
        val samples = BrushMath.sampleAlongPath(mapped, spacing)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        // Setting Paint.color resets its alpha to that color's own (opaque) alpha channel,
        // so alpha must always be (re)applied after color, every time color changes.
        val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
        for (sample in samples) {
            val rotationRadians = BrushMath.stampRotation(sample.index)
            when (tool) {
                DrawTool.STAR -> {
                    paint.color = color.toArgb()
                    paint.alpha = alphaInt
                    val polygon = BrushMath.starPolygon(sample.position, size, size * 0.45f, rotationRadians)
                    val starPath = Path().apply {
                        moveTo(polygon.first().x, polygon.first().y)
                        for (p in polygon.drop(1)) lineTo(p.x, p.y)
                        close()
                    }
                    canvas.drawPath(starPath, paint)
                }
                DrawTool.CONFETTI -> {
                    val hsv = FloatArray(3)
                    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                    hsv[0] = (hsv[0] + BrushMath.confettiHueShift(sample.index)) % 360f
                    hsv[1] = hsv[1].coerceAtLeast(0.55f)
                    hsv[2] = hsv[2].coerceAtLeast(0.65f)
                    paint.color = android.graphics.Color.HSVToColor(hsv)
                    paint.alpha = alphaInt
                    val half = size / 2f
                    canvas.save()
                    canvas.rotate(Math.toDegrees(rotationRadians.toDouble()).toFloat(), sample.position.x, sample.position.y)
                    canvas.drawRect(
                        sample.position.x - half, sample.position.y - half,
                        sample.position.x + half, sample.position.y + half,
                        paint
                    )
                    canvas.restore()
                }
                else -> Unit
            }
        }
    }

    /** Mirrors MarkupCanvas's drawSoftDabStroke(): Airbrush / Watercolor. */
    private fun drawSoftDabPath(canvas: Canvas, mapped: List<Offset>, color: Color, alpha: Float, baseWidthPx: Float, tool: DrawTool) {
        val spacing = BrushMath.dabSpacing(tool, baseWidthPx)
        val samples = BrushMath.sampleAlongPath(mapped, spacing)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            style = Paint.Style.FILL
        }
        for (sample in samples) {
            val radius = BrushMath.dabRadius(tool, baseWidthPx, sample.index)
            paint.alpha = ((alpha * BrushMath.dabAlphaFactor(tool, sample.index)).coerceIn(0f, 1f) * 255).toInt()
            canvas.drawCircle(sample.position.x, sample.position.y, radius, paint)
        }
    }

    private fun drawShapeElement(canvas: Canvas, element: MarkupElement.ShapeElement, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.color.toArgb()
            alpha = (element.alpha * 255).toInt().coerceIn(0, 255)
            style = if (element.filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = element.strokeWidthFraction * w
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val x1 = element.start.x * w
        val y1 = element.start.y * h
        val x2 = element.end.x * w
        val y2 = element.end.y * h
        val left = min(x1, x2)
        val top = min(y1, y2)
        val right = max(x1, x2)
        val bottom = max(y1, y2)

        when (element.type) {
            ShapeType.LINE -> canvas.drawLine(x1, y1, x2, y2, paint)
            ShapeType.ARROW -> {
                canvas.drawLine(x1, y1, x2, y2, paint)
                drawArrowHead(canvas, x1, y1, x2, y2, paint)
            }
            ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint)
            ShapeType.OVAL -> canvas.drawOval(RectF(left, top, right, bottom), paint)
        }
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        val angle = atan2(y2 - y1, x2 - x1)
        val headLength = max(paint.strokeWidth * 4f, 24f)
        val spread = Math.toRadians(28.0).toFloat()
        val p1x = x2 - headLength * cos(angle - spread).toFloat()
        val p1y = y2 - headLength * sin(angle - spread).toFloat()
        val p2x = x2 - headLength * cos(angle + spread).toFloat()
        val p2y = y2 - headLength * sin(angle + spread).toFloat()
        canvas.drawLine(x2, y2, p1x, p1y, paint)
        canvas.drawLine(x2, y2, p2x, p2y, paint)
    }

    private fun drawTextElement(canvas: Canvas, element: MarkupElement.TextElement, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.color.toArgb()
            textSize = element.fontSizeFraction * w
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val cx = element.center.x * w
        val cy = element.center.y * h
        val metrics = paint.fontMetrics
        val verticalOffset = -(metrics.ascent + metrics.descent) / 2f
        canvas.drawText(element.text, cx, cy + verticalOffset, paint)
    }
}
