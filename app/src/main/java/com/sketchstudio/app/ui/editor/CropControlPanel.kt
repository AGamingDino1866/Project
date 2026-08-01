package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.ui.components.PillButton
import com.sketchstudio.app.ui.components.ToolIconButton
import com.sketchstudio.app.ui.theme.LocalIosPalette
import com.sketchstudio.app.util.BitmapTransforms

private data class AspectPreset(val label: String, val ratio: Float?)

private val AspectPresets = listOf(
    AspectPreset("Free", null),
    AspectPreset("Square", 1f),
    AspectPreset("4:3", 4f / 3f),
    AspectPreset("3:2", 3f / 2f),
    AspectPreset("16:9", 16f / 9f)
)

private val DEFAULT_CROP_RECT = Rect(0.05f, 0.05f, 0.95f, 0.95f)

fun aspectPresetRect(targetRatio: Float?, imageAspect: Float): Rect {
    if (targetRatio == null) return DEFAULT_CROP_RECT
    val normAspect = targetRatio / imageAspect
    val widthNorm: Float
    val heightNorm: Float
    if (normAspect <= 1f) {
        heightNorm = 0.9f
        widthNorm = (normAspect * heightNorm).coerceAtMost(0.98f)
    } else {
        widthNorm = 0.9f
        heightNorm = (widthNorm / normAspect).coerceAtMost(0.98f)
    }
    val left = (1f - widthNorm) / 2f
    val top = (1f - heightNorm) / 2f
    return Rect(left, top, left + widthNorm, top + heightNorm)
}

@Composable
fun CropControlPanel(
    state: EditorUiState,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
    viewModel: EditorViewModel,
    onApply: () -> Unit
) {
    val palette = LocalIosPalette.current
    val imageAspect = state.baseBitmap.width.toFloat() / state.baseBitmap.height.toFloat()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Crop & Rotate", style = MaterialTheme.typography.titleMedium, color = palette.label)
            PillButton(text = "Apply", onClick = onApply)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(AspectPresets) { preset ->
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.tertiaryBackground,
                    onClick = { onCropRectChange(aspectPresetRect(preset.ratio, imageAspect)) }
                ) {
                    Text(
                        preset.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolIconButton(
                icon = Icons.Outlined.RotateRight,
                label = "Rotate",
                onClick = {
                    viewModel.beginEdit()
                    val rotated = BitmapTransforms.rotate90Clockwise(state.baseBitmap)
                    viewModel.applyRotation90(rotated)
                    onCropRectChange(DEFAULT_CROP_RECT)
                }
            )
            ToolIconButton(
                icon = Icons.Outlined.Flip,
                label = "Flip H",
                onClick = {
                    viewModel.beginEdit()
                    val flipped = BitmapTransforms.flip(state.baseBitmap, horizontal = true)
                    viewModel.applyFlip(flipped, horizontal = true)
                }
            )
            ToolIconButton(
                icon = Icons.Outlined.Flip,
                label = "Flip V",
                onClick = {
                    viewModel.beginEdit()
                    val flipped = BitmapTransforms.flip(state.baseBitmap, horizontal = false)
                    viewModel.applyFlip(flipped, horizontal = false)
                }
            )
        }
    }
}
