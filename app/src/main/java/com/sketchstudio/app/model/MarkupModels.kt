package com.sketchstudio.app.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class DrawTool {
    PEN, INK, PENCIL,
    MARKER, WATERCOLOR, AIRBRUSH,
    CRAYON, CHALK,
    CALLIGRAPHY, RIBBON,
    NEON, RAINBOW, DASHED, STAR, CONFETTI,
    ERASER
}

/** Groups brushes by how they're rendered, so MarkupCanvas/MarkupRenderer can dispatch on one thing. */
enum class BrushFamily { CONSTANT, VARIABLE_WIDTH, GLOW, RAINBOW, DASHED, STAMP, SOFT_DABS }

fun DrawTool.family(): BrushFamily = when (this) {
    DrawTool.PEN, DrawTool.MARKER, DrawTool.PENCIL -> BrushFamily.CONSTANT
    DrawTool.CRAYON, DrawTool.CHALK, DrawTool.CALLIGRAPHY, DrawTool.RIBBON, DrawTool.INK -> BrushFamily.VARIABLE_WIDTH
    DrawTool.NEON -> BrushFamily.GLOW
    DrawTool.RAINBOW -> BrushFamily.RAINBOW
    DrawTool.DASHED -> BrushFamily.DASHED
    DrawTool.STAR, DrawTool.CONFETTI -> BrushFamily.STAMP
    DrawTool.WATERCOLOR, DrawTool.AIRBRUSH -> BrushFamily.SOFT_DABS
    DrawTool.ERASER -> BrushFamily.CONSTANT
}

data class BrushInfo(val tool: DrawTool, val label: String, val category: String)

val BrushCatalog: List<BrushInfo> = listOf(
    BrushInfo(DrawTool.PEN, "Pen", "Precision"),
    BrushInfo(DrawTool.INK, "Ink", "Precision"),
    BrushInfo(DrawTool.PENCIL, "Pencil", "Precision"),
    BrushInfo(DrawTool.MARKER, "Marker", "Painterly"),
    BrushInfo(DrawTool.WATERCOLOR, "Watercolor", "Painterly"),
    BrushInfo(DrawTool.AIRBRUSH, "Airbrush", "Painterly"),
    BrushInfo(DrawTool.CRAYON, "Crayon", "Textured"),
    BrushInfo(DrawTool.CHALK, "Chalk", "Textured"),
    BrushInfo(DrawTool.CALLIGRAPHY, "Calligraphy", "Calligraphy"),
    BrushInfo(DrawTool.RIBBON, "Ribbon", "Calligraphy"),
    BrushInfo(DrawTool.NEON, "Neon", "Fun & Decorative"),
    BrushInfo(DrawTool.RAINBOW, "Rainbow", "Fun & Decorative"),
    BrushInfo(DrawTool.DASHED, "Dashed", "Fun & Decorative"),
    BrushInfo(DrawTool.STAR, "Star Stamp", "Fun & Decorative"),
    BrushInfo(DrawTool.CONFETTI, "Confetti", "Fun & Decorative")
)

enum class ShapeType {
    LINE, ARROW, RECTANGLE, OVAL
}

/**
 * All geometry below is stored normalized to [0, 1] against the current base
 * image's own width/height, so elements stay correctly placed regardless of
 * on-screen zoom and can be remapped cheaply when the base image is cropped.
 */
sealed class MarkupElement {
    abstract val id: String

    data class PathElement(
        override val id: String,
        val tool: DrawTool,
        val color: Color,
        val strokeWidthFraction: Float,
        val alpha: Float,
        val points: List<Offset>
    ) : MarkupElement()

    data class ShapeElement(
        override val id: String,
        val type: ShapeType,
        val color: Color,
        val strokeWidthFraction: Float,
        val alpha: Float,
        val filled: Boolean,
        val start: Offset,
        val end: Offset
    ) : MarkupElement()

    data class TextElement(
        override val id: String,
        val text: String,
        val color: Color,
        val fontSizeFraction: Float,
        val center: Offset,
        val rotationDegrees: Float = 0f
    ) : MarkupElement()
}

/** Remaps normalized geometry after a 90-degree clockwise rotation of the base image. */
fun MarkupElement.remapForRotation90CW(): MarkupElement {
    fun rotate(p: Offset) = Offset(1f - p.y, p.x)
    return when (this) {
        is MarkupElement.PathElement -> copy(points = points.map(::rotate))
        is MarkupElement.ShapeElement -> copy(start = rotate(start), end = rotate(end))
        is MarkupElement.TextElement -> copy(center = rotate(center), rotationDegrees = rotationDegrees + 90f)
    }
}

/** Remaps normalized geometry after a horizontal or vertical flip of the base image. */
fun MarkupElement.remapForFlip(horizontal: Boolean): MarkupElement {
    fun flip(p: Offset) = if (horizontal) Offset(1f - p.x, p.y) else Offset(p.x, 1f - p.y)
    return when (this) {
        is MarkupElement.PathElement -> copy(points = points.map(::flip))
        is MarkupElement.ShapeElement -> copy(start = flip(start), end = flip(end))
        is MarkupElement.TextElement -> copy(center = flip(center))
    }
}

/** Remaps normalized geometry after the base image is cropped. */
fun MarkupElement.remapForCrop(cropRect: androidx.compose.ui.geometry.Rect): MarkupElement {
    fun remap(p: Offset): Offset = Offset(
        x = ((p.x - cropRect.left) / cropRect.width).coerceIn(-1f, 2f),
        y = ((p.y - cropRect.top) / cropRect.height).coerceIn(-1f, 2f)
    )
    return when (this) {
        is MarkupElement.PathElement -> copy(
            points = points.map(::remap),
            strokeWidthFraction = strokeWidthFraction / cropRect.width
        )
        is MarkupElement.ShapeElement -> copy(
            start = remap(start),
            end = remap(end),
            strokeWidthFraction = strokeWidthFraction / cropRect.width
        )
        is MarkupElement.TextElement -> copy(
            center = remap(center),
            fontSizeFraction = fontSizeFraction / cropRect.width
        )
    }
}
