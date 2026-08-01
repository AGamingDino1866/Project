package com.sketchstudio.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.model.VariableWidthTools
import kotlin.math.abs
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

        if (element.tool in VariableWidthTools) {
            drawVariableWidthPath(canvas, mapped, element.color.toArgb(), element.alpha, baseWidth, element.tool)
            return
        }

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

    /** Mirrors MarkupCanvas's drawVariableWidthStroke()/variableSegmentWidth() exactly. */
    private fun drawVariableWidthPath(
        canvas: Canvas,
        mapped: List<Offset>,
        colorArgb: Int,
        alpha: Float,
        baseWidthPx: Float,
        tool: DrawTool
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (i in 0 until mapped.size - 1) {
            val a = mapped[i]
            val b = mapped[i + 1]
            paint.strokeWidth = variableSegmentWidth(tool, i, a, b, baseWidthPx)
            canvas.drawLine(a.x, a.y, b.x, b.y, paint)
        }
    }

    private val NIB_ANGLE_RADIANS = Math.toRadians(45.0).toFloat()

    private fun variableSegmentWidth(
        tool: DrawTool,
        index: Int,
        segmentStart: Offset,
        segmentEnd: Offset,
        baseWidthPx: Float
    ): Float = when (tool) {
        DrawTool.CRAYON -> {
            val wave = 0.78f + 0.22f * sin(index * 0.9f)
            (baseWidthPx * wave).coerceAtLeast(1f)
        }
        DrawTool.CALLIGRAPHY -> {
            val angle = atan2(segmentEnd.y - segmentStart.y, segmentEnd.x - segmentStart.x)
            val factor = 0.35f + 0.9f * abs(sin(angle - NIB_ANGLE_RADIANS))
            (baseWidthPx * factor).coerceAtLeast(1f)
        }
        else -> baseWidthPx
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
