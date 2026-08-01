package com.sketchstudio.app.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.ui.geometry.Rect

object BitmapTransforms {

    fun rotate90Clockwise(src: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val matrix = Matrix().apply {
            if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun crop(src: Bitmap, rectNormalized: Rect): Bitmap {
        val x = (rectNormalized.left * src.width).toInt().coerceIn(0, src.width - 1)
        val y = (rectNormalized.top * src.height).toInt().coerceIn(0, src.height - 1)
        val w = (rectNormalized.width * src.width).toInt().coerceIn(1, src.width - x)
        val h = (rectNormalized.height * src.height).toInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }
}
