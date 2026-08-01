package com.sketchstudio.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.ShapeType
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
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = element.color.toArgb()
            alpha = (element.alpha * 255).toInt().coerceIn(0, 255)
            style = Paint.Style.STROKE
            strokeWidth = element.strokeWidthFraction * w
            strokeCap = if (element.tool == DrawTool.MARKER) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path()
        val first = element.points.first()
        path.moveTo(first.x * w, first.y * h)
        for (i in 1 until element.points.size) {
            val p = element.points[i]
            path.lineTo(p.x * w, p.y * h)
        }
        canvas.drawPath(path, paint)
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
