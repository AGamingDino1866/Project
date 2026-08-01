package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.AdjustmentKey
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.get
import com.sketchstudio.app.model.with
import com.sketchstudio.app.ui.components.PillButton
import com.sketchstudio.app.ui.theme.IosColors
import com.sketchstudio.app.ui.theme.LocalIosPalette
import kotlin.math.roundToInt

@Composable
fun AdjustPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val palette = LocalIosPalette.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Adjust", style = MaterialTheme.typography.titleMedium, color = palette.label)
            if (!state.adjustments.isNeutral) {
                PillButton(
                    text = "Reset",
                    filled = false,
                    color = IosColors.SystemRed,
                    onClick = {
                        viewModel.beginEdit()
                        viewModel.updateAdjustments(com.sketchstudio.app.model.Adjustments.Neutral)
                    }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            items(AdjustmentKey.values().toList()) { key ->
                val range = if (key == AdjustmentKey.VIGNETTE) 0f..100f else -100f..100f
                AdjustRow(
                    label = key.label,
                    value = state.adjustments.get(key),
                    range = range,
                    onDragStart = { viewModel.beginEdit() },
                    onValueChange = { newValue ->
                        viewModel.updateAdjustments(state.adjustments.with(key, newValue))
                    }
                )
            }
        }
    }
}

@Composable
private fun AdjustRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onDragStart: () -> Unit,
    onValueChange: (Float) -> Unit
) {
    val palette = LocalIosPalette.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) onDragStart()
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.label)
            Text(
                value.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.secondaryLabel
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = Color(0x33808080)
            )
        )
    }
}
