package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.sketchstudio.app.model.BrushFamily
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.MarkupMode
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.model.family
import com.sketchstudio.app.util.BrushMath
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
    val haptics = LocalHapticFeedback.current
    var inProgressPath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    // The gesture coroutine below only restarts when markupMode/activeDrawTool
    // change (see pointerInput keys). Everything else read inside it (color,
    // stroke width, opacity, elements...) must come through this so it always
    // sees the latest value instead of whatever was captured when the
    // coroutine last (re)launched.
    val latestState = rememberUpdatedState(state)

    val gestureModifier = if (state.markupMode == MarkupMode.TEXT) {
        Modifier.pointerInput(state.markupMode) {
            detectTapGestures { pos -> onTextPlace(metrics.toNormalized(pos)) }
        }
    } else {
        Modifier.pointerInput(state.markupMode, state.activeDrawTool) {
            detectDragGestures(
                onDragStart = { pos ->
                    onBeginStroke()
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val current = latestState.value
                    val norm = metrics.toNormalized(pos)
                    when {
                        current.markupMode == MarkupMode.SHAPE -> {
                            shapeStart = norm
                            shapeEnd = norm
                        }
                        current.activeDrawTool == DrawTool.ERASER -> {
                            val hit = hitTestElements(current.elements, norm)
                            if (hit.isNotEmpty()) onElementsErased(hit)
                        }
                        else -> {
                            inProgressPath = listOf(norm)
                        }
                    }
                },
                onDrag = { change, _ ->
                    val current = latestState.value
                    val norm = metrics.toNormalized(change.position)
                    when {
                        current.markupMode == MarkupMode.SHAPE -> shapeEnd = norm
                        current.activeDrawTool == DrawTool.ERASER -> {
                            val hit = hitTestElements(current.elements, norm)
                            if (hit.isNotEmpty()) onElementsErased(hit)
                        }
                        else -> inProgressPath = inProgressPath + norm
                    }
                },
                onDragEnd = {
                    finalizeStroke(
                        latestState.value, inProgressPath, shapeStart, shapeEnd, onStrokeFinished
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
    DrawTool.CRAYON -> 0.8f
    DrawTool.CHALK -> 0.7f
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
    if (points.size < 2) return
    val strokeWidthPx = (strokeWidthFraction * metrics.displayedSize.width).coerceAtLeast(1f)
    val mapped = points.map { metrics.toContainerLocal(it) }

    when (tool.family()) {
        BrushFamily.VARIABLE_WIDTH -> drawVariableWidthStroke(mapped, color, strokeWidthPx, alpha, tool)
        BrushFamily.GLOW -> drawGlowStroke(mapped, color, strokeWidthPx, alpha)
        BrushFamily.RAINBOW -> drawRainbowStroke(mapped, strokeWidthPx, alpha)
        BrushFamily.DASHED -> drawDashedStroke(mapped, color, strokeWidthPx, alpha)
        BrushFamily.STAMP -> drawStampStroke(mapped, color, strokeWidthPx, alpha, tool)
        BrushFamily.SOFT_DABS -> drawSoftDabStroke(mapped, color, strokeWidthPx, alpha, tool)
        BrushFamily.CONSTANT -> {
            val path = smoothedPath(mapped)
            val cap = if (tool == DrawTool.MARKER) StrokeCap.Square else StrokeCap.Round
            drawPath(
                path = path,
                color = color,
                alpha = alpha,
                style = Stroke(width = strokeWidthPx, cap = cap, join = StrokeJoin.Round)
            )
        }
    }
}

/** Builds a smoothed path through midpoints (quadratic bezier), reducing the jagged look of raw finger-drag points. */
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
        val midX = (curr.x + next.x) / 2f
        val midY = (curr.y + next.y) / 2f
        path.quadraticTo(curr.x, curr.y, midX, midY)
    }
    val last = mapped.last()
    path.lineTo(last.x, last.y)
    return path
}

/**
 * Renders a stroke whose width varies along its length (Crayon, Chalk,
 * Calligraphy, Ribbon, Ink). Drawn as a sequence of per-segment lines rather
 * than one continuous Path, since Compose can't vary a Stroke's width
 * partway through a path. All width math lives in BrushMath so this always
 * matches the exported bitmap exactly.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVariableWidthStroke(
    mapped: List<Offset>,
    color: Color,
    baseWidthPx: Float,
    alpha: Float,
    tool: DrawTool
) {
    for (i in 0 until mapped.size - 1) {
        val a = mapped[i]
        val b = mapped[i + 1]
        val segWidth = BrushMath.variableSegmentWidth(tool, i, a, b, baseWidthPx)
        val segAlpha = (alpha * BrushMath.segmentAlphaFactor(tool, i)).coerceIn(0f, 1f)
        drawLine(color = color, start = a, end = b, strokeWidth = segWidth, cap = StrokeCap.Round, alpha = segAlpha)
    }
}

/** Neon: a soft wide glow underlay plus a bright, near-white core line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlowStroke(
    mapped: List<Offset>,
    color: Color,
    baseWidthPx: Float,
    alpha: Float
) {
    val path = smoothedPath(mapped)
    drawPath(path, color = color, alpha = alpha * 0.3f, style = Stroke(width = baseWidthPx * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color = color, alpha = alpha * 0.55f, style = Stroke(width = baseWidthPx * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val core = lerp(color, Color.White, 0.55f)
    drawPath(path, color = core, alpha = alpha, style = Stroke(width = (baseWidthPx * 0.5f).coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Rainbow: each segment's hue advances deterministically along the stroke. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRainbowStroke(
    mapped: List<Offset>,
    baseWidthPx: Float,
    alpha: Float
) {
    for (i in 0 until mapped.size - 1) {
        drawLine(
            color = Color.hsv(BrushMath.rainbowHueAt(i), 0.85f, 0.95f),
            start = mapped[i],
            end = mapped[i + 1],
            strokeWidth = baseWidthPx,
            cap = StrokeCap.Round,
            alpha = alpha
        )
    }
}

/** Dashed: a smoothed path with a native dash-pattern PathEffect. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDashedStroke(
    mapped: List<Offset>,
    color: Color,
    baseWidthPx: Float,
    alpha: Float
) {
    val dashLen = (baseWidthPx * 2.2f).coerceAtLeast(6f)
    val gapLen = (baseWidthPx * 1.6f).coerceAtLeast(5f)
    drawPath(
        path = smoothedPath(mapped),
        color = color,
        alpha = alpha,
        style = Stroke(
            width = baseWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen), 0f)
        )
    )
}

/** Star Stamp / Confetti: a shape repeated at evenly-spaced points along the path. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStampStroke(
    mapped: List<Offset>,
    color: Color,
    baseWidthPx: Float,
    alpha: Float,
    tool: DrawTool
) {
    val spacing = BrushMath.stampSpacing(tool, baseWidthPx)
    val size = BrushMath.stampSize(tool, baseWidthPx)
    val samples = BrushMath.sampleAlongPath(mapped, spacing)
    for (sample in samples) {
        val rotationRadians = BrushMath.stampRotation(sample.index)
        when (tool) {
            DrawTool.STAR -> {
                val polygon = BrushMath.starPolygon(sample.position, size, size * 0.45f, rotationRadians)
                val starPath = Path().apply {
                    moveTo(polygon.first().x, polygon.first().y)
                    for (p in polygon.drop(1)) lineTo(p.x, p.y)
                    close()
                }
                drawPath(starPath, color = color, alpha = alpha)
            }
            DrawTool.CONFETTI -> {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                val shiftedHue = (hsv[0] + BrushMath.confettiHueShift(sample.index)) % 360f
                val dabColor = Color.hsv(shiftedHue, hsv[1].coerceAtLeast(0.55f), hsv[2].coerceAtLeast(0.65f))
                rotate(degrees = Math.toDegrees(rotationRadians.toDouble()).toFloat(), pivot = sample.position) {
                    drawRect(
                        color = dabColor,
                        topLeft = Offset(sample.position.x - size / 2f, sample.position.y - size / 2f),
                        size = androidx.compose.ui.geometry.Size(size, size),
                        alpha = alpha
                    )
                }
            }
            else -> Unit
        }
    }
}

/** Airbrush / Watercolor: many soft, low-alpha circular dabs along the path. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftDabStroke(
    mapped: List<Offset>,
    color: Color,
    baseWidthPx: Float,
    alpha: Float,
    tool: DrawTool
) {
    val spacing = BrushMath.dabSpacing(tool, baseWidthPx)
    val samples = BrushMath.sampleAlongPath(mapped, spacing)
    for (sample in samples) {
        val radius = BrushMath.dabRadius(tool, baseWidthPx, sample.index)
        val dabAlpha = (alpha * BrushMath.dabAlphaFactor(tool, sample.index)).coerceIn(0f, 1f)
        drawCircle(color = color, radius = radius, center = sample.position, alpha = dabAlpha)
    }
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
