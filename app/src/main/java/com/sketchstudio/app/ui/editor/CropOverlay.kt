package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val MIN_CROP_SIZE = 0.12f

private enum class CropCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Composable
fun CropOverlay(
    metrics: ImageDisplayMetrics,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val handleTouchPx = with(density) { 44.dp.toPx() }
    val handleVisualDp = 16.dp
    val latestCropRect = rememberUpdatedState(cropRect)
    val latestOnChange = rememberUpdatedState(onCropRectChange)

    val topLeftPx = metrics.toContainerLocal(Offset(cropRect.left, cropRect.top))
    val bottomRightPx = metrics.toContainerLocal(Offset(cropRect.right, cropRect.bottom))
    val rectPx = Rect(topLeftPx.x, topLeftPx.y, bottomRightPx.x, bottomRightPx.y)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val overlayPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRect(rectPx)
            }
            drawPath(overlayPath, color = Color.Black.copy(alpha = 0.6f))
            drawRect(
                color = Color.White,
                topLeft = rectPx.topLeft,
                size = rectPx.size,
                style = Stroke(width = 2.dp.toPx())
            )

            val thirdW = rectPx.width / 3f
            val thirdH = rectPx.height / 3f
            val gridColor = Color.White.copy(alpha = 0.5f)
            for (i in 1..2) {
                drawLine(
                    gridColor,
                    Offset(rectPx.left + thirdW * i, rectPx.top),
                    Offset(rectPx.left + thirdW * i, rectPx.bottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    gridColor,
                    Offset(rectPx.left, rectPx.top + thirdH * i),
                    Offset(rectPx.right, rectPx.top + thirdH * i),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        listOf(
            CropCorner.TOP_LEFT to rectPx.topLeft,
            CropCorner.TOP_RIGHT to Offset(rectPx.right, rectPx.top),
            CropCorner.BOTTOM_LEFT to Offset(rectPx.left, rectPx.bottom),
            CropCorner.BOTTOM_RIGHT to rectPx.bottomRight
        ).forEach { (corner, point) ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (point.x - handleTouchPx / 2f).roundToInt(),
                            (point.y - handleTouchPx / 2f).roundToInt()
                        )
                    }
                    .size(with(density) { handleTouchPx.toDp() })
                    .pointerInput(corner, metrics) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val dx = dragAmount.x / metrics.displayedSize.width
                            val dy = dragAmount.y / metrics.displayedSize.height
                            latestOnChange.value(resizedRect(latestCropRect.value, corner, dx, dy))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(handleVisualDp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color.Black.copy(alpha = 0.25f), CircleShape)
                )
            }
        }
    }
}

private fun resizedRect(rect: Rect, corner: CropCorner, dx: Float, dy: Float): Rect {
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    when (corner) {
        CropCorner.TOP_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP_SIZE)
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP_SIZE)
        }
        CropCorner.TOP_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP_SIZE, 1f)
            top = (top + dy).coerceIn(0f, bottom - MIN_CROP_SIZE)
        }
        CropCorner.BOTTOM_LEFT -> {
            left = (left + dx).coerceIn(0f, right - MIN_CROP_SIZE)
            bottom = (bottom + dy).coerceIn(top + MIN_CROP_SIZE, 1f)
        }
        CropCorner.BOTTOM_RIGHT -> {
            right = (right + dx).coerceIn(left + MIN_CROP_SIZE, 1f)
            bottom = (bottom + dy).coerceIn(top + MIN_CROP_SIZE, 1f)
        }
    }
    return Rect(left, top, right, bottom)
}
