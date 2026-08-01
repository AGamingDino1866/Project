package com.sketchstudio.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) { hsvOf(initialColor) }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hexText by remember { mutableStateOf(hexOf(initialColor)) }

    val currentColor = Color.hsv(hue.coerceIn(0f, 359.999f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Color") },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                )

                Text("Hue", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 14.dp))
                GradientSlider(
                    value = hue,
                    onValueChange = { hue = it; hexText = hexOf(currentColorFrom(it, saturation, brightness)) },
                    valueRange = 0f..360f,
                    gradientColors = HueGradientColors
                )

                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    value = saturation,
                    onValueChange = { saturation = it; hexText = hexOf(currentColorFrom(hue, it, brightness)) },
                    valueRange = 0f..1f,
                    gradientColors = listOf(Color.hsv(hue, 0f, brightness), Color.hsv(hue, 1f, brightness))
                )

                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    value = brightness,
                    onValueChange = { brightness = it; hexText = hexOf(currentColorFrom(hue, saturation, it)) },
                    valueRange = 0f..1f,
                    gradientColors = listOf(Color.hsv(hue, saturation, 0f), Color.hsv(hue, saturation, 1f))
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { hexText = it },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val parsed = parseHexColor(hexText)
                        if (parsed != null) {
                            val hsv = hsvOf(parsed)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    }) {
                        Icon(Icons.Outlined.Check, contentDescription = "Apply hex")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("Add Color") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    gradientColors: List<Color>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.horizontalGradient(gradientColors))
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

private val HueGradientColors = listOf(
    Color.hsv(0f, 1f, 1f),
    Color.hsv(60f, 1f, 1f),
    Color.hsv(120f, 1f, 1f),
    Color.hsv(180f, 1f, 1f),
    Color.hsv(240f, 1f, 1f),
    Color.hsv(300f, 1f, 1f),
    Color.hsv(359.999f, 1f, 1f)
)

private fun currentColorFrom(hue: Float, saturation: Float, brightness: Float): Color =
    Color.hsv(hue.coerceIn(0f, 359.999f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))

private fun hsvOf(color: Color): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv
}

private fun hexOf(color: Color): String =
    String.format("#%06X", 0xFFFFFF and color.toArgb())

private fun parseHexColor(text: String): Color? = try {
    val normalized = if (text.startsWith("#")) text else "#$text"
    Color(android.graphics.Color.parseColor(normalized))
} catch (e: IllegalArgumentException) {
    null
}
