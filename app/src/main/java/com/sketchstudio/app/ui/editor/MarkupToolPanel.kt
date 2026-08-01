package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.PanoramaFishEye
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.MarkupMode
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.ui.components.ColorSwatch
import com.sketchstudio.app.ui.components.ToolIconButton
import com.sketchstudio.app.ui.theme.LocalIosPalette

private val MarkupPalette = listOf(
    Color(0xFF1C1C1E), Color.White, Color(0xFFFF3B30), Color(0xFFFF9500),
    Color(0xFFFFCC00), Color(0xFF34C759), Color(0xFF0A84FF), Color(0xFF5E5CE6),
    Color(0xFFFF2D55)
)

@Composable
fun MarkupToolPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val palette = LocalIosPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ToolIconButton(
                icon = Icons.Outlined.Edit,
                selected = state.markupMode == MarkupMode.DRAW && state.activeDrawTool == DrawTool.PEN,
                onClick = { viewModel.updateDrawTool(DrawTool.PEN) }
            )
            ToolIconButton(
                icon = Icons.Outlined.Brush,
                selected = state.markupMode == MarkupMode.DRAW && state.activeDrawTool == DrawTool.MARKER,
                onClick = { viewModel.updateDrawTool(DrawTool.MARKER) }
            )
            ToolIconButton(
                icon = Icons.Outlined.Create,
                selected = state.markupMode == MarkupMode.DRAW && state.activeDrawTool == DrawTool.PENCIL,
                onClick = { viewModel.updateDrawTool(DrawTool.PENCIL) }
            )
            ToolIconButton(
                icon = Icons.Outlined.Delete,
                selected = state.markupMode == MarkupMode.DRAW && state.activeDrawTool == DrawTool.ERASER,
                onClick = { viewModel.updateDrawTool(DrawTool.ERASER) }
            )
            ToolIconButton(
                icon = shapeIcon(state.activeShapeType),
                selected = state.markupMode == MarkupMode.SHAPE,
                onClick = { viewModel.updateMarkupMode(MarkupMode.SHAPE) }
            )
            ToolIconButton(
                icon = Icons.Outlined.TextFields,
                selected = state.markupMode == MarkupMode.TEXT,
                onClick = { viewModel.updateMarkupMode(MarkupMode.TEXT) }
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
                        onClick = { viewModel.updateShapeType(type) }
                    )
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(MarkupPalette) { color ->
                ColorSwatch(
                    color = color,
                    selected = state.currentColor == color,
                    onClick = { viewModel.updateColor(color) }
                )
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
}

private fun shapeIcon(type: ShapeType): ImageVector = when (type) {
    ShapeType.LINE -> Icons.Outlined.HorizontalRule
    ShapeType.ARROW -> Icons.Outlined.ArrowForward
    ShapeType.RECTANGLE -> Icons.Outlined.CropSquare
    ShapeType.OVAL -> Icons.Outlined.PanoramaFishEye
}
