package com.sketchstudio.app.ui.home

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sketchstudio.app.ui.theme.IosColors
import com.sketchstudio.app.ui.theme.LocalIosPalette
import java.io.File
import java.io.FileOutputStream

@Composable
fun HomeScreen(onImagePicked: (Uri) -> Unit) {
    val context = LocalContext.current
    val palette = LocalIosPalette.current

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onImagePicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(IosColors.AccentGradientStart, IosColors.AccentGradientEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Brush,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Sketch Studio",
            style = MaterialTheme.typography.displayLarge,
            color = palette.label
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Markup, adjust and draw on any photo\nwith an iPadOS-inspired toolset.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.secondaryLabel,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        HomeActionRow(
            icon = Icons.Outlined.AddPhotoAlternate,
            title = "Choose Photo",
            subtitle = "Pick any picture from your gallery",
            onClick = {
                pickMediaLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }
        )

        Spacer(Modifier.height(14.dp))

        HomeActionRow(
            icon = Icons.Outlined.Brush,
            title = "New Blank Canvas",
            subtitle = "Start sketching on a blank page",
            onClick = { onImagePicked(createBlankCanvasUri(context)) }
        )
    }
}

@Composable
private fun HomeActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val palette = LocalIosPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.secondaryBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(PaddingValues(horizontal = 18.dp, vertical = 16.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(24.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = palette.label, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryLabel)
        }
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = palette.tertiaryLabel,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun createBlankCanvasUri(context: android.content.Context): Uri {
    val width = 1536
    val height = 2048
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(AndroidColor.WHITE)

    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "blank_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    bitmap.recycle()

    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
