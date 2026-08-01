package com.sketchstudio.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.sketchstudio.app.model.Adjustments
import com.sketchstudio.app.model.FilterPreset
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Pure pixel-level image editing: tone adjustments, filter presets and
 * vignette. Operates on plain android.graphics.Bitmap so it can be reused
 * identically for the live (downsampled) preview and the full-resolution
 * export.
 */
object ImageProcessing {

    fun applyAll(
        src: Bitmap,
        adjustments: Adjustments,
        filter: FilterPreset,
        filterIntensity: Float
    ): Bitmap {
        var result = applyAdjustments(src, adjustments)
        result = applyFilter(result, filter, filterIntensity)
        result = applyVignette(result, adjustments.vignette)
        return result
    }

    fun applyAdjustments(src: Bitmap, adjustments: Adjustments): Bitmap {
        if (adjustments.isNeutral) return src

        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val toneLut = buildToneLut(adjustments)
        val warmthOffset = adjustments.warmth / 100f * 40f
        val tintOffset = adjustments.tint / 100f * 50f
        val saturationFactor = 1f + adjustments.saturation / 100f

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = (pixel ushr 24) and 0xFF
            var r = toneLut[(pixel ushr 16) and 0xFF].toFloat()
            var g = toneLut[(pixel ushr 8) and 0xFF].toFloat()
            var b = toneLut[pixel and 0xFF].toFloat()

            r += warmthOffset
            b -= warmthOffset
            g -= tintOffset
            r += tintOffset * 0.5f
            b += tintOffset * 0.5f

            val gray = 0.299f * r + 0.587f * g + 0.114f * b
            r = gray + (r - gray) * saturationFactor
            g = gray + (g - gray) * saturationFactor
            b = gray + (b - gray) * saturationFactor

            val ri = r.toInt().coerceIn(0, 255)
            val gi = g.toInt().coerceIn(0, 255)
            val bi = b.toInt().coerceIn(0, 255)
            pixels[i] = (alpha shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun buildToneLut(adjustments: Adjustments): IntArray {
        val exposureFactor = 2.0.pow((adjustments.exposure / 100.0) * 1.5).toFloat()
        val brightnessOffset = adjustments.brightness / 100f * 80f
        val contrastFactor = 1f + adjustments.contrast / 100f * 0.9f

        val lut = IntArray(256)
        for (i in 0..255) {
            var v = i * exposureFactor
            v += brightnessOffset
            v = (v - 128f) * contrastFactor + 128f

            val highlightWeight = (i / 255f) * (i / 255f)
            v += (adjustments.highlights / 100f) * -70f * highlightWeight

            val shadowWeight = (1f - i / 255f) * (1f - i / 255f)
            v += (adjustments.shadows / 100f) * 70f * shadowWeight

            lut[i] = v.toInt().coerceIn(0, 255)
        }
        return lut
    }

    fun applyFilter(src: Bitmap, filter: FilterPreset, intensity: Float): Bitmap {
        val matrix = filterMatrix(filter) ?: return src
        val blended = blendWithIdentity(matrix, intensity.coerceIn(0f, 1f))
        if (blended == null) return src

        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(blended)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    fun applyVignette(src: Bitmap, amount: Float): Bitmap {
        if (amount <= 0f) return src
        val strength = (amount / 100f).coerceIn(0f, 1f)

        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val cx = result.width / 2f
        val cy = result.height / 2f
        val radius = hypot(cx, cy)

        val shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(Color.TRANSPARENT, Color.argb((strength * 190).toInt(), 0, 0, 0)),
            floatArrayOf(0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), paint)
        return result
    }

    private fun filterMatrix(filter: FilterPreset): ColorMatrix? = when (filter) {
        FilterPreset.NONE -> null
        FilterPreset.VIVID -> combine(saturationMatrix(1.35f), contrastMatrix(1.12f))
        FilterPreset.VIVID_WARM -> combine(saturationMatrix(1.35f), contrastMatrix(1.1f), warmthMatrix(18f))
        FilterPreset.VIVID_COOL -> combine(saturationMatrix(1.3f), contrastMatrix(1.1f), warmthMatrix(-18f))
        FilterPreset.DRAMATIC -> combine(saturationMatrix(1.15f), contrastMatrix(1.35f), brightnessMatrix(-8f))
        FilterPreset.DRAMATIC_WARM -> combine(saturationMatrix(1.15f), contrastMatrix(1.3f), warmthMatrix(14f))
        FilterPreset.DRAMATIC_COOL -> combine(saturationMatrix(1.1f), contrastMatrix(1.3f), warmthMatrix(-14f))
        FilterPreset.MONO -> combine(saturationMatrix(0f), contrastMatrix(1.08f))
        FilterPreset.SILVERTONE -> combine(saturationMatrix(0f), contrastMatrix(0.9f), brightnessMatrix(14f))
        FilterPreset.NOIR -> combine(saturationMatrix(0f), contrastMatrix(1.45f), brightnessMatrix(-12f))
    }

    private fun saturationMatrix(saturation: Float): ColorMatrix =
        ColorMatrix().apply { setSaturation(saturation) }

    private fun contrastMatrix(contrast: Float): ColorMatrix {
        val translate = 128f * (1f - contrast)
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun brightnessMatrix(offset: Float): ColorMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun warmthMatrix(shift: Float): ColorMatrix = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, shift,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, -shift,
            0f, 0f, 0f, 1f, 0f
        )
    )

    private fun combine(vararg matrices: ColorMatrix): ColorMatrix =
        matrices.reduce { acc, next ->
            ColorMatrix().apply { setConcat(next, acc) }
        }

    private fun blendWithIdentity(matrix: ColorMatrix, intensity: Float): ColorMatrix? {
        if (intensity >= 1f) return matrix
        if (intensity <= 0f) return null
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        val source = matrix.array
        val out = FloatArray(20)
        for (i in 0..19) {
            out[i] = identity[i] * (1f - intensity) + source[i] * intensity
        }
        return ColorMatrix(out)
    }
}
