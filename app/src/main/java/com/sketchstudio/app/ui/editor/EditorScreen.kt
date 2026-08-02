package com.sketchstudio.app.ui.editor

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sketchstudio.app.model.EditorTab
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.ui.components.GlassSurface
import com.sketchstudio.app.ui.components.PillButton
import com.sketchstudio.app.ui.components.ToolIconButton
import com.sketchstudio.app.util.BitmapTransforms
import kotlinx.coroutines.launch
import java.util.UUID

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 6f

@Composable
fun EditorScreen(imageUri: Uri, onClose: () -> Unit) {
    val context = LocalContext.current
    val viewModel = remember(imageUri) { EditorViewModel(context.applicationContext, imageUri) }
    DisposableEffect(viewModel) { onDispose { viewModel.dispose() } }

    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var cropRect by remember(state?.baseBitmap) { mutableStateOf(Rect(0.05f, 0.05f, 0.95f, 0.95f)) }
    var showTextDialog by remember { mutableStateOf(false) }
    var pendingTextPosition by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var textFieldValue by remember { mutableStateOf("") }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomScale by remember(state?.baseBitmap) { mutableStateOf(1f) }
    var zoomOffset by remember(state?.baseBitmap) { mutableStateOf(Offset.Zero) }

    val currentState = state

    LaunchedEffect(currentState?.lastSavedMessage) {
        val message = currentState?.lastSavedMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.setSavedMessage(null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
    ) {
        if (currentState == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            return@Box
        }

        val s = currentState

        Column(modifier = Modifier.fillMaxSize()) {
            EditorTopBar(
                canUndo = s.canUndo,
                canRedo = s.canRedo,
                isSaving = s.isSaving,
                onClose = onClose,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onSave = {
                    viewModel.setSaving(true)
                    scope.launch {
                        val uri = viewModel.exportAndSave()
                        viewModel.setSaving(false)
                        viewModel.setSavedMessage(if (uri != null) "Saved to Photos" else "Couldn't save image")
                    }
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { containerSize = it }
                        .twoFingerTransformGesture { centroid, pan, zoom ->
                            val newScale = (zoomScale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            val scaleFactor = newScale / zoomScale
                            zoomOffset = (zoomOffset - centroid) * scaleFactor + centroid + pan
                            zoomScale = newScale
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = zoomOffset.x,
                                translationY = zoomOffset.y
                            )
                    ) {
                        Image(
                            bitmap = s.previewBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val metrics = ImageDisplayMetrics(
                                containerSize = Size(containerSize.width.toFloat(), containerSize.height.toFloat()),
                                imageSize = Size(s.baseBitmap.width.toFloat(), s.baseBitmap.height.toFloat())
                            )

                            when (s.activeTab) {
                                EditorTab.MARKUP -> MarkupCanvas(
                                    metrics = metrics,
                                    state = s,
                                    onBeginStroke = { viewModel.beginEdit() },
                                    onStrokeFinished = { element -> viewModel.addElement(element) },
                                    onElementsErased = { ids -> viewModel.removeElements(ids) },
                                    onTextPlace = { pos ->
                                        pendingTextPosition = pos
                                        textFieldValue = ""
                                        showTextDialog = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                EditorTab.CROP -> CropOverlay(
                                    metrics = metrics,
                                    cropRect = cropRect,
                                    onCropRectChange = { cropRect = it },
                                    modifier = Modifier.fillMaxSize()
                                )
                                else -> {}
                            }
                        }
                    }

                    if (zoomScale > 1.01f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            PillButton(
                                text = "Reset Zoom",
                                onClick = {
                                    zoomScale = 1f
                                    zoomOffset = Offset.Zero
                                }
                            )
                        }
                    }
                }

                EditorSidePanel(
                    state = s,
                    viewModel = viewModel,
                    cropRect = cropRect,
                    onCropRectChange = { cropRect = it },
                    onApplyCrop = {
                        viewModel.beginEdit()
                        val cropped = BitmapTransforms.crop(s.baseBitmap, cropRect)
                        viewModel.applyCrop(cropped, cropRect)
                        cropRect = Rect(0.05f, 0.05f, 0.95f, 0.95f)
                    },
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }

        if (showTextDialog) {
            AlertDialog(
                onDismissRequest = { showTextDialog = false },
                title = { Text("Add Text") },
                text = {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        placeholder = { Text("Type something") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (textFieldValue.isNotBlank()) {
                            viewModel.beginEdit()
                            viewModel.addElement(
                                MarkupElement.TextElement(
                                    id = UUID.randomUUID().toString(),
                                    text = textFieldValue,
                                    color = s.currentColor,
                                    fontSizeFraction = 0.06f,
                                    center = pendingTextPosition
                                )
                            )
                        }
                        showTextDialog = false
                    }) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showTextDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    isSaving: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit
) {
    GlassSurface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton(text = "Cancel", filled = false, onClick = onClose)

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolIconButton(icon = Icons.Outlined.Undo, onClick = onUndo, tint = if (canUndo) null else Color.Gray)
                ToolIconButton(icon = Icons.Outlined.Redo, onClick = onRedo, tint = if (canRedo) null else Color.Gray)
            }

            if (isSaving) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            } else {
                PillButton(text = "Save", onClick = onSave)
            }
        }
    }
}

/**
 * Tool panel docked to the side (rather than a bottom sheet), so the photo
 * keeps the full screen height — the layout tablets have room for and a
 * bottom sheet would otherwise waste.
 */
@Composable
private fun EditorSidePanel(
    state: EditorUiState,
    viewModel: EditorViewModel,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
    onApplyCrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val panelWidth = (maxWidth * 0.42f).coerceIn(220.dp, 300.dp)
        GlassSurface(modifier = Modifier.width(panelWidth).fillMaxHeight()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                EditorSideTabRow(
                    activeTab = state.activeTab,
                    onSelect = { viewModel.updateActiveTab(it) }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    when (state.activeTab) {
                        EditorTab.ADJUST -> AdjustPanel(state = state, viewModel = viewModel)
                        EditorTab.FILTERS -> FiltersPanel(state = state, viewModel = viewModel)
                        EditorTab.CROP -> CropControlPanel(
                            state = state,
                            cropRect = cropRect,
                            onCropRectChange = onCropRectChange,
                            viewModel = viewModel,
                            onApply = onApplyCrop
                        )
                        EditorTab.MARKUP -> MarkupToolPanel(state = state, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSideTabRow(activeTab: EditorTab, onSelect: (EditorTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolIconButton(
            icon = Icons.Outlined.Tune,
            selected = activeTab == EditorTab.ADJUST,
            onClick = { onSelect(EditorTab.ADJUST) }
        )
        ToolIconButton(
            icon = Icons.Outlined.PhotoFilter,
            selected = activeTab == EditorTab.FILTERS,
            onClick = { onSelect(EditorTab.FILTERS) }
        )
        ToolIconButton(
            icon = Icons.Outlined.Crop,
            selected = activeTab == EditorTab.CROP,
            onClick = { onSelect(EditorTab.CROP) }
        )
        ToolIconButton(
            icon = Icons.Outlined.Draw,
            selected = activeTab == EditorTab.MARKUP,
            onClick = { onSelect(EditorTab.MARKUP) }
        )
    }
}
