package com.sketchstudio.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.FilterPreset
import com.sketchstudio.app.ui.theme.LocalIosPalette
import com.sketchstudio.app.util.ImageProcessing

@Composable
fun FiltersPanel(state: EditorUiState, viewModel: EditorViewModel) {
    val palette = LocalIosPalette.current

    val thumbnails = remember(state.baseBitmap) {
        val thumbBase = Bitmap.createScaledBitmap(state.baseBitmap, 96, 96, true)
        FilterPreset.values().associateWith { preset -> ImageProcessing.applyFilter(thumbBase, preset, 1f) }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text("Filters", style = MaterialTheme.typography.titleMedium, color = palette.label)

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(FilterPreset.values().toList()) { preset ->
                FilterThumb(
                    preset = preset,
                    bitmap = thumbnails[preset] ?: state.baseBitmap,
                    selected = state.filter == preset,
                    onClick = {
                        viewModel.beginEdit()
                        viewModel.updateFilter(preset)
                    }
                )
            }
        }

        if (state.filter != FilterPreset.NONE) {
            val interactionSource = remember { MutableInteractionSource() }
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Press) viewModel.beginEdit()
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Intensity", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
                Slider(
                    value = state.filterIntensity,
                    onValueChange = { viewModel.updateFilterIntensity(it) },
                    valueRange = 0f..1f,
                    interactionSource = interactionSource,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = palette.accent, activeTrackColor = palette.accent)
                )
            }
        }
    }
}

@Composable
private fun FilterThumb(
    preset: FilterPreset,
    bitmap: Bitmap,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalIosPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = preset.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (selected) 2.5.dp else 0.dp,
                    color = if (selected) palette.accent else androidx.compose.ui.graphics.Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        )
        Text(
            preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else palette.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
