package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PanoramaFishEye
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.MarkupMode
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.ui.components.ColorSwatch
import com.sketchstudio.app.ui.components.ToolIconButton
import com.sketchstudio.app.ui.theme.LocalIosPalette
import com.sketchstudio.app.ui.theme.IosColors

private val MarkupPalette = listOf(
    Color(0xFF1C1C1E), Color.White, Color(0xFFFF3B30), Color(0xFFFF9500),
    Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFF5E5CE6),
    Color(0xFFFF2D55)
)

@Composable
fun MarkupToolPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val palette = LocalIosPalette.current
    val haptics = LocalHapticFeedback.current
    fun tap(action: () -> Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        action()
    }

    var showColorPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showBrushPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Markup", style = MaterialTheme.typography.titleMedium, color = palette.label)
            if (state.elements.isNotEmpty()) {
                ToolIconButton(
                    icon = Icons.Outlined.DeleteSweep,
                    label = "Clear All",
                    tint = IosColors.SystemRed,
                    onClick = { showClearConfirm = true }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolIconButton(
                icon = toolIcon(state.activeDrawTool),
                label = brushLabel(state.activeDrawTool),
                selected = state.markupMode == MarkupMode.DRAW,
                onClick = { tap { showBrushPicker = true } }
            )
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = "Choose brush",
                tint = palette.secondaryLabel,
                modifier = Modifier.size(16.dp).padding(top = 2.dp)
            )
            Box(modifier = Modifier.weight(1f))
            ToolIconButton(
                icon = Icons.Outlined.Delete,
                selected = state.markupMode == MarkupMode.DRAW && state.activeDrawTool == DrawTool.ERASER,
                onClick = { tap { viewModel.updateDrawTool(DrawTool.ERASER) } }
            )
            ToolIconButton(
                icon = shapeIcon(state.activeShapeType),
                selected = state.markupMode == MarkupMode.SHAPE,
                onClick = { tap { viewModel.updateMarkupMode(MarkupMode.SHAPE) } }
            )
            ToolIconButton(
                icon = Icons.Outlined.TextFields,
                selected = state.markupMode == MarkupMode.TEXT,
                onClick = { tap { viewModel.updateMarkupMode(MarkupMode.TEXT) } }
            )
        }

        if (state.markupMode == MarkupMode.SHAPE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
            ) {
                ShapeType.values().forEach { type ->
                    ToolIconButton(
                        icon = shapeIcon(type),
                        selected = state.activeShapeType == type,
                        onClick = { tap { viewModel.updateShapeType(type) } }
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MarkupPalette) { color ->
                ColorSwatch(
                    color = color,
                    selected = state.currentColor == color,
                    onClick = { tap { viewModel.updateColor(color) } }
                )
            }
            items(state.customColors) { color ->
                ColorSwatch(
                    color = color,
                    selected = state.currentColor == color,
                    onClick = { tap { viewModel.updateColor(color) } }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, palette.secondaryLabel, CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showColorPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Custom color", tint = palette.secondaryLabel)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.HorizontalRule, contentDescription = "Width", tint = palette.secondaryLabel, modifier = Modifier)
            Slider(
                value = state.currentStrokeWidthFraction,
                onValueChange = { viewModel.updateStrokeWidth(it) },
                valueRange = 0.002f..0.035f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
            BrushSizePreview(state.currentStrokeWidthFraction, state.currentColor)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Opacity, contentDescription = "Opacity", tint = palette.secondaryLabel, modifier = Modifier)
            Slider(
                value = state.currentOpacity,
                onValueChange = { viewModel.updateOpacity(it) },
                valueRange = 0.1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
            )
        }
    }

    if (showBrushPicker) {
        BrushPickerDialog(
            selectedTool = state.activeDrawTool,
            onSelect = { tool ->
                tap { viewModel.updateDrawTool(tool) }
                showBrushPicker = false
            },
            onDismiss = { showBrushPicker = false }
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = state.currentColor,
            onConfirm = { color ->
                viewModel.addCustomColor(color)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all markup?") },
            text = { Text("Removes every pen, shape and text mark on this photo. You can still undo it afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.beginEdit()
                    viewModel.clearAllElements()
                    showClearConfirm = false
                }) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BrushSizePreview(strokeWidthFraction: Float, color: Color) {
    val minDp = 6f
    val maxDp = 30f
    val t = ((strokeWidthFraction - 0.002f) / (0.035f - 0.002f)).coerceIn(0f, 1f)
    val sizeDp = minDp + t * (maxDp - minDp)
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(maxDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

fun toolIcon(tool: DrawTool): ImageVector = when (tool) {
    DrawTool.PEN -> Icons.Outlined.Edit
    DrawTool.INK -> Icons.Outlined.Draw
    DrawTool.PENCIL -> Icons.Outlined.Create
    DrawTool.MARKER -> Icons.Outlined.Brush
    DrawTool.WATERCOLOR -> Icons.Outlined.Opacity
    DrawTool.AIRBRUSH -> Icons.Outlined.BlurOn
    DrawTool.CRAYON -> Icons.Outlined.Texture
    DrawTool.CHALK -> Icons.Outlined.Grain
    DrawTool.CALLIGRAPHY -> Icons.Outlined.LineWeight
    DrawTool.RIBBON -> Icons.Outlined.Waves
    DrawTool.NEON -> Icons.Outlined.FlashOn
    DrawTool.RAINBOW -> Icons.Outlined.Palette
    DrawTool.DASHED -> Icons.Outlined.MoreHoriz
    DrawTool.STAR -> Icons.Outlined.Star
    DrawTool.CONFETTI -> Icons.Outlined.AutoAwesome
    DrawTool.ERASER -> Icons.Outlined.Delete
}

private fun brushLabel(tool: DrawTool): String = when (tool) {
    DrawTool.PEN -> "Pen"
    DrawTool.INK -> "Ink"
    DrawTool.PENCIL -> "Pencil"
    DrawTool.MARKER -> "Marker"
    DrawTool.WATERCOLOR -> "Watercolor"
    DrawTool.AIRBRUSH -> "Airbrush"
    DrawTool.CRAYON -> "Crayon"
    DrawTool.CHALK -> "Chalk"
    DrawTool.CALLIGRAPHY -> "Calligraphy"
    DrawTool.RIBBON -> "Ribbon"
    DrawTool.NEON -> "Neon"
    DrawTool.RAINBOW -> "Rainbow"
    DrawTool.DASHED -> "Dashed"
    DrawTool.STAR -> "Star Stamp"
    DrawTool.CONFETTI -> "Confetti"
    DrawTool.ERASER -> "Eraser"
}

private fun shapeIcon(type: ShapeType): ImageVector = when (type) {
    ShapeType.LINE -> Icons.Outlined.HorizontalRule
    ShapeType.ARROW -> Icons.Outlined.ArrowForward
    ShapeType.RECTANGLE -> Icons.Outlined.CropSquare
    ShapeType.OVAL -> Icons.Outlined.PanoramaFishEye
}
