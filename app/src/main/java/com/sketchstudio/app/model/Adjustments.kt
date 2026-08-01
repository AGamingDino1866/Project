package com.sketchstudio.app.model

/**
 * All values range -100..100 (vignette is 0..100). 0 means "no change" for
 * every field, matching the neutral position of an iPadOS Photos slider.
 */
data class Adjustments(
    val exposure: Float = 0f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f
) {
    val isNeutral: Boolean
        get() = exposure == 0f && brightness == 0f && contrast == 0f &&
            highlights == 0f && shadows == 0f && saturation == 0f &&
            warmth == 0f && tint == 0f && vignette == 0f

    companion object {
        val Neutral = Adjustments()
    }
}

enum class AdjustmentKey(val label: String) {
    EXPOSURE("Exposure"),
    BRIGHTNESS("Brightness"),
    CONTRAST("Contrast"),
    HIGHLIGHTS("Highlights"),
    SHADOWS("Shadows"),
    SATURATION("Saturation"),
    WARMTH("Warmth"),
    TINT("Tint"),
    VIGNETTE("Vignette")
}

fun Adjustments.get(key: AdjustmentKey): Float = when (key) {
    AdjustmentKey.EXPOSURE -> exposure
    AdjustmentKey.BRIGHTNESS -> brightness
    AdjustmentKey.CONTRAST -> contrast
    AdjustmentKey.HIGHLIGHTS -> highlights
    AdjustmentKey.SHADOWS -> shadows
    AdjustmentKey.SATURATION -> saturation
    AdjustmentKey.WARMTH -> warmth
    AdjustmentKey.TINT -> tint
    AdjustmentKey.VIGNETTE -> vignette
}

fun Adjustments.with(key: AdjustmentKey, value: Float): Adjustments = when (key) {
    AdjustmentKey.EXPOSURE -> copy(exposure = value)
    AdjustmentKey.BRIGHTNESS -> copy(brightness = value)
    AdjustmentKey.CONTRAST -> copy(contrast = value)
    AdjustmentKey.HIGHLIGHTS -> copy(highlights = value)
    AdjustmentKey.SHADOWS -> copy(shadows = value)
    AdjustmentKey.SATURATION -> copy(saturation = value)
    AdjustmentKey.WARMTH -> copy(warmth = value)
    AdjustmentKey.TINT -> copy(tint = value)
    AdjustmentKey.VIGNETTE -> copy(vignette = value)
}
