package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketchstudio.app.model.BrushCatalog
import com.sketchstudio.app.model.BrushInfo
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.ui.theme.LocalIosPalette

@Composable
fun BrushPickerDialog(
    selectedTool: DrawTool,
    onSelect: (DrawTool) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalIosPalette.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = palette.secondaryBackground
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Brushes",
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.label,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .padding(horizontal = 10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
                ) {
                    BrushCatalog.groupBy { it.category }.forEach { (category, brushes) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                category,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryLabel,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 6.dp)
                            )
                        }
                        items(brushes) { info ->
                            BrushGridItem(
                                info = info,
                                selected = info.tool == selectedTool,
                                onClick = { onSelect(info.tool) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrushGridItem(info: BrushInfo, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalIosPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Icon(
            toolIcon(info.tool),
            contentDescription = info.label,
            tint = if (selected) palette.accent else palette.label,
            modifier = Modifier.size(26.dp)
        )
        Text(
            info.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else palette.secondaryLabel,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}
