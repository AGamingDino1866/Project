package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.MarkupMode
import com.sketchstudio.app.model.ShapeType
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val ERASE_RADIUS_NORMALIZED = 0.035f

@Composable
fun MarkupCanvas(
    metrics: ImageDisplayMetrics,
    state: EditorUiState,
    onBeginStroke: () -> Unit,
    onStrokeFinished: (MarkupElement) -> Unit,
    onElementsErased: (Set<String>) -> Unit,
    onTextPlace: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    var inProgressPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    val gestureModifier = if (state.markupMode == MarkupMode.TEXT) {
        Modifier.pointerInput(state.markupMode) {
            detectTapGestures { pos -> onTextPlace(metrics.toNormalized(pos)) }
        }
    } else {
        Modifier.pointerInput(state.markupMode, state.activeDrawTool) {
            detectDragGestures(
                onDragStart = { pos ->
                    onBeginStroke()
                    val norm = metrics.toNormalized(pos)
                    when {
                        state.markupMode == MarkupMode.SHAPE -> {
                            shapeStart = norm
                            shapeEnd = norm
                        }
                        state.activeDrawTool == DrawTool.ERASER -> {
                            val hit = hitTestElements(state.elements, norm)
                            if (hit.isNotEmpty()) onElementsErased(hit)
                        }
                        else -> {
                            inProgressPath = listOf(norm)
                        }
                    }
                },
                onDrag = { change, _ ->
                    val norm = metrics.toNormalized(change.position)
                    when {
                        state.markupMode == MarkupMode.SHAPE -> shapeEnd = norm
                        state.activeDrawTool == DrawTool.ERASER -> {
                            val hit = hitTestElements(state.elements, norm)
                            if (hit.isNotEmpty()) onElementsErased(hit)
                        }
                        else -> inProgressPath = inProgressPath + norm
                    }
                },
                onDragEnd = {
                    finalizeStroke(
                        state, inProgressPath, shapeStart, shapeEnd, onStrokeFinished
                    )
                    inProgressPath = emptyList()
                    shapeStart = null
                    shapeEnd = null
                },
                onDragCancel = {
                    inProgressPath = emptyList()
                    shapeStart = null
                    shapeEnd = null
                }
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize().then(gestureModifier)) {
        state.elements.forEach { element ->
            drawMarkupElement(element, metrics, textMeasurer)
        }

        if (inProgressPath.size > 1) {
            drawStrokePath(
                points = inProgressPath,
                metrics = metrics,
                color = state.currentColor,
                strokeWidthFraction = state.currentStrokeWidthFraction,
                alpha = strokeAlphaFor(state.activeDrawTool) * state.currentOpacity,
                tool = state.activeDrawTool
            )
        }

        val start = shapeStart
        val end = shapeEnd
        if (start != null && end != null) {
            drawShape(
                type = state.activeShapeType,
                start = start,
                end = end,
                metrics = metrics,
                color = state.currentColor,
                strokeWidthFraction = state.currentStrokeWidthFraction,
                alpha = state.currentOpacity,
                filled = false
            )
        }
    }
}

private fun finalizeStroke(
    state: EditorUiState,
    inProgressPath: List<Offset>,
    shapeStart: Offset?,
    shapeEnd: Offset?,
    onStrokeFinished: (MarkupElement) -> Unit
) {
    if (state.markupMode == MarkupMode.SHAPE) {
        val start = shapeStart
        val end = shapeEnd
        if (start != null && end != null && (start - end).getDistance() > 0.005f) {
            onStrokeFinished(
                MarkupElement.ShapeElement(
                    id = UUID.randomUUID().toString(),
                    type = state.activeShapeType,
                    color = state.currentColor,
                    strokeWidthFraction = state.currentStrokeWidthFraction,
                    alpha = state.currentOpacity,
                    filled = false,
                    start = start,
                    end = end
                )
            )
        }
    } else if (state.activeDrawTool != DrawTool.ERASER && inProgressPath.size > 1) {
        onStrokeFinished(
            MarkupElement.PathElement(
                id = UUID.randomUUID().toString(),
                tool = state.activeDrawTool,
                color = state.currentColor,
                strokeWidthFraction = state.currentStrokeWidthFraction,
                alpha = strokeAlphaFor(state.activeDrawTool) * state.currentOpacity,
                points = inProgressPath
            )
        )
    }
}

private fun strokeAlphaFor(tool: DrawTool): Float = when (tool) {
    DrawTool.MARKER -> 0.45f
    DrawTool.PENCIL -> 0.85f
    else -> 1f
}

private fun hitTestElements(elements: List<MarkupElement>, point: Offset): Set<String> {
    val hits = mutableSetOf<String>()
    for (element in elements) {
        val hit = when (element) {
            is MarkupElement.PathElement -> element.points.any { (it - point).getDistance() < ERASE_RADIUS_NORMALIZED }
            is MarkupElement.ShapeElement -> {
                val left = min(element.start.x, element.end.x) - ERASE_RADIUS_NORMALIZED
                val right = max(element.start.x, element.end.x) + ERASE_RADIUS_NORMALIZED
                val top = min(element.start.y, element.end.y) - ERASE_RADIUS_NORMALIZED
                val bottom = max(element.start.y, element.end.y) + ERASE_RADIUS_NORMALIZED
                point.x in left..right && point.y in top..bottom
            }
            is MarkupElement.TextElement -> {
                val halfW = element.text.length * element.fontSizeFraction * 0.3f + 0.02f
                val halfH = element.fontSizeFraction * 0.9f
                point.x in (element.center.x - halfW)..(element.center.x + halfW) &&
                    point.y in (element.center.y - halfH)..(element.center.y + halfH)
            }
        }
        if (hit) hits.add(element.id)
    }
    return hits
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMarkupElement(
    element: MarkupElement,
    metrics: ImageDisplayMetrics,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    when (element) {
        is MarkupElement.PathElement -> drawStrokePath(
            points = element.points,
            metrics = metrics,
            color = element.color,
            strokeWidthFraction = element.strokeWidthFraction,
            alpha = element.alpha,
            tool = element.tool
        )
        is MarkupElement.ShapeElement -> drawShape(
            type = element.type,
            start = element.start,
            end = element.end,
            metrics = metrics,
            color = element.color,
            strokeWidthFraction = element.strokeWidthFraction,
            alpha = element.alpha,
            filled = element.filled
        )
        is MarkupElement.TextElement -> {
            val fontSizePx = element.fontSizeFraction * metrics.displayedSize.width
            val style = TextStyle(
                color = element.color,
                fontSize = fontSizePx.toSp(),
                fontWeight = FontWeight.SemiBold
            )
            val layout = textMeasurer.measure(text = element.text, style = style)
            val center = metrics.toContainerLocal(element.center)
            val topLeft = Offset(
                center.x - layout.size.width / 2f,
                center.y - layout.size.height / 2f
            )
            drawText(textLayoutResult = layout, topLeft = topLeft)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStrokePath(
    points: List<Offset>,
    metrics: ImageDisplayMetrics,
    color: Color,
    strokeWidthFraction: Float,
    alpha: Float,
    tool: DrawTool
) {
    if (points.isEmpty()) return
    val path = Path()
    val first = metrics.toContainerLocal(points.first())
    path.moveTo(first.x, first.y)
    for (i in 1 until points.size) {
        val p = metrics.toContainerLocal(points[i])
        path.lineTo(p.x, p.y)
    }
    val strokeWidthPx = (strokeWidthFraction * metrics.displayedSize.width).coerceAtLeast(1f)
    val cap = if (tool == DrawTool.MARKER) StrokeCap.Square else StrokeCap.Round
    drawPath(
        path = path,
        color = color,
        alpha = alpha,
        style = Stroke(width = strokeWidthPx, cap = cap, join = StrokeJoin.Round)
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    type: ShapeType,
    start: Offset,
    end: Offset,
    metrics: ImageDisplayMetrics,
    color: Color,
    strokeWidthFraction: Float,
    alpha: Float,
    filled: Boolean
) {
    val a = metrics.toContainerLocal(start)
    val b = metrics.toContainerLocal(end)
    val strokeWidthPx = (strokeWidthFraction * metrics.displayedSize.width).coerceAtLeast(1f)
    val style: DrawStyle = if (filled) Fill else Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (type) {
        ShapeType.LINE -> drawLine(
            color = color,
            start = a,
            end = b,
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round,
            alpha = alpha
        )
        ShapeType.ARROW -> {
            drawLine(color = color, start = a, end = b, strokeWidth = strokeWidthPx, cap = StrokeCap.Round, alpha = alpha)
            val angle = atan2((b.y - a.y), (b.x - a.x))
            val headLength = max(strokeWidthPx * 4f, 18f)
            val spread = Math.toRadians(28.0).toFloat()
            val p1 = Offset(
                b.x - headLength * cos(angle - spread).toFloat(),
                b.y - headLength * sin(angle - spread).toFloat()
            )
            val p2 = Offset(
                b.x - headLength * cos(angle + spread).toFloat(),
                b.y - headLength * sin(angle + spread).toFloat()
            )
            val arrowPath = Path().apply {
                moveTo(b.x, b.y)
                lineTo(p1.x, p1.y)
                moveTo(b.x, b.y)
                lineTo(p2.x, p2.y)
            }
            drawPath(
                arrowPath,
                color = color,
                alpha = alpha,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        ShapeType.RECTANGLE -> {
            val topLeft = Offset(min(a.x, b.x), min(a.y, b.y))
            val size = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
            drawRect(color = color, topLeft = topLeft, size = size, alpha = alpha, style = style)
        }
        ShapeType.OVAL -> {
            val topLeft = Offset(min(a.x, b.x), min(a.y, b.y))
            val size = androidx.compose.ui.geometry.Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
            drawOval(color = color, topLeft = topLeft, size = size, alpha = alpha, style = style)
        }
    }
}
