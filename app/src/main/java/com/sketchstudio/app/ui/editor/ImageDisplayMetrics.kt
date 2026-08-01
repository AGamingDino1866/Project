package com.sketchstudio.app.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

/**
 * Describes where a [Fit]-scaled image is drawn within a larger container,
 * so pointer coordinates (container-local px) can be converted to and from
 * image-normalized [0, 1] coordinates.
 */
data class ImageDisplayMetrics(
    val containerSize: Size,
    val imageSize: Size
) {
    val scale: Float = if (imageSize.width <= 0f || imageSize.height <= 0f) {
        1f
    } else {
        min(containerSize.width / imageSize.width, containerSize.height / imageSize.height)
    }

    val displayedSize: Size = Size(imageSize.width * scale, imageSize.height * scale)

    val topLeft: Offset = Offset(
        (containerSize.width - displayedSize.width) / 2f,
        (containerSize.height - displayedSize.height) / 2f
    )

    fun toNormalized(containerLocal: Offset): Offset {
        if (displayedSize.width <= 0f || displayedSize.height <= 0f) return Offset.Zero
        return Offset(
            ((containerLocal.x - topLeft.x) / displayedSize.width).coerceIn(0f, 1f),
            ((containerLocal.y - topLeft.y) / displayedSize.height).coerceIn(0f, 1f)
        )
    }

    fun toContainerLocal(normalized: Offset): Offset = Offset(
        topLeft.x + normalized.x * displayedSize.width,
        topLeft.y + normalized.y * displayedSize.height
    )
}
