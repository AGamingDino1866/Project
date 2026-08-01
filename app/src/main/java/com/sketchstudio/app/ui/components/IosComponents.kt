package com.sketchstudio.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.ui.theme.IosColors
import com.sketchstudio.app.ui.theme.LocalIosPalette

/**
 * A translucent surface that mimics iPadOS's UIVisualEffectView bars.
 *
 * Note: this only applies translucency, not a true backdrop blur — blurring
 * only what's *behind* a panel (without blurring the panel's own content,
 * like iOS's UIVisualEffectView) needs a separate captured background layer,
 * which isn't worth the complexity here. An earlier version blurred this
 * Box directly, which blurred its buttons/text/icons too — that was a bug,
 * not a stylistic choice.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalIosPalette.current
    val shape = if (cornerRadius > 0.dp) RoundedCornerShape(cornerRadius) else RoundedCornerShape(0.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(palette.elevatedSurface),
        content = content
    )
}

/** iOS-style segmented control, e.g. used to switch between Photo / Markup source tabs. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIosPalette.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (palette.isDark) IosColors.SystemGray5Dark else IosColors.SystemGray5)
            .padding(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) (if (palette.isDark) IosColors.SystemGray3Dark else Color.White) else Color.Transparent,
                label = "segmentBg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = palette.label
                )
            }
        }
    }
}

/** A circular iPadOS-style toolbar icon button, highlights with a filled pill when active. */
@Composable
fun ToolIconButton(
    icon: ImageVector,
    label: String? = null,
    selected: Boolean = false,
    tint: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalIosPalette.current
    val bg by animateColorAsState(
        if (selected) palette.accent.copy(alpha = 0.16f) else Color.Transparent,
        label = "toolBg"
    )
    val iconColor = tint ?: if (selected) palette.accent else palette.label
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(22.dp))
            if (label != null) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = iconColor
                )
            }
        }
    }
}

/** Rounded pill button used for primary actions like "Done". */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    color: Color = IosColors.SystemBlue
) {
    val palette = LocalIosPalette.current
    val bg = if (filled) color else Color.Transparent
    val fg = if (filled) Color.White else color
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) bg else palette.tertiaryLabel.copy(alpha = 0.15f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) fg else palette.tertiaryLabel,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Small circular swatch used to represent a color choice. */
@Composable
fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val ringColor = LocalIosPalette.current.label
    val ringWidth by animateDpAsState(if (selected) 2.5.dp else 0.dp, label = "ring")
    Box(
        modifier = modifier
            .size(size + 10.dp)
            .clip(CircleShape)
            .border(ringWidth, ringColor.copy(alpha = 0.85f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (color == Color.Transparent) {
                        Brush.sweepGradient(
                            listOf(
                                Color.Red, Color.Magenta, Color.Blue,
                                Color.Cyan, Color.Green, Color.Yellow, Color.Red
                            )
                        )
                    } else Brush.linearGradient(listOf(color, color))
                )
        )
    }
}
