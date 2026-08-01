package com.sketchstudio.app.ui.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.sketchstudio.app.model.Adjustments
import com.sketchstudio.app.model.DrawTool
import com.sketchstudio.app.model.EditorTab
import com.sketchstudio.app.model.EditorUiState
import com.sketchstudio.app.model.FilterPreset
import com.sketchstudio.app.model.HistorySnapshot
import com.sketchstudio.app.model.MarkupElement
import com.sketchstudio.app.model.MarkupMode
import com.sketchstudio.app.model.ShapeType
import com.sketchstudio.app.model.remapForCrop
import com.sketchstudio.app.model.remapForFlip
import com.sketchstudio.app.model.remapForRotation90CW
import com.sketchstudio.app.util.ImageIO
import com.sketchstudio.app.util.ImageProcessing
import com.sketchstudio.app.util.MarkupRenderer
import com.sketchstudio.app.util.MediaStoreSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns all editor state for a single editing session. Kept as a plain class
 * (rather than androidx ViewModel) so it can be created directly per
 * composition; MainActivity disables configuration-change recreation so this
 * survives rotation without needing SavedState plumbing for bitmaps.
 */
class EditorViewModel(
    private val context: Context,
    private val imageUri: Uri
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow<EditorUiState?>(null)
    val uiState: StateFlow<EditorUiState?> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private var previewJob: Job? = null

    init {
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ImageIO.loadScaledBitmap(context, imageUri, EDIT_MAX_DIMENSION)
            }
            _uiState.value = EditorUiState(baseBitmap = bitmap, previewBitmap = bitmap)
        }
    }

    fun dispose() {
        scope.cancel()
    }

    // ---- History -----------------------------------------------------

    fun beginEdit() {
        val s = _uiState.value ?: return
        undoStack.addLast(snapshotOf(s))
        redoStack.clear()
        updateHistoryFlags()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        val s = _uiState.value ?: return
        redoStack.addLast(snapshotOf(s))
        applySnapshot(prev)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val s = _uiState.value ?: return
        undoStack.addLast(snapshotOf(s))
        applySnapshot(next)
    }

    private fun snapshotOf(s: EditorUiState) = HistorySnapshot(
        baseBitmap = s.baseBitmap,
        elements = s.elements,
        adjustments = s.adjustments,
        filter = s.filter,
        filterIntensity = s.filterIntensity
    )

    private fun applySnapshot(snap: HistorySnapshot) {
        _uiState.update {
            it?.copy(
                baseBitmap = snap.baseBitmap,
                elements = snap.elements,
                adjustments = snap.adjustments,
                filter = snap.filter,
                filterIntensity = snap.filterIntensity
            )
        }
        updateHistoryFlags()
        schedulePreviewRecompute()
    }

    private fun updateHistoryFlags() {
        _uiState.update { it?.copy(canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
    }

    // ---- Markup --------------------------------------------------------

    fun addElement(element: MarkupElement) {
        _uiState.update { it?.copy(elements = it.elements + element) }
    }

    fun removeElements(ids: Set<String>) {
        _uiState.update { it?.copy(elements = it.elements.filterNot { e -> ids.contains(e.id) }) }
    }

    fun updateActiveTab(tab: EditorTab) {
        _uiState.update { it?.copy(activeTab = tab) }
    }

    fun updateDrawTool(tool: DrawTool) {
        _uiState.update { it?.copy(activeDrawTool = tool, markupMode = MarkupMode.DRAW) }
    }

    fun updateShapeType(type: ShapeType) {
        _uiState.update { it?.copy(activeShapeType = type, markupMode = MarkupMode.SHAPE) }
    }

    fun updateMarkupMode(mode: MarkupMode) {
        _uiState.update { it?.copy(markupMode = mode) }
    }

    fun updateColor(color: Color) {
        _uiState.update { it?.copy(currentColor = color) }
    }

    fun updateStrokeWidth(fraction: Float) {
        _uiState.update { it?.copy(currentStrokeWidthFraction = fraction) }
    }

    fun updateOpacity(opacity: Float) {
        _uiState.update { it?.copy(currentOpacity = opacity) }
    }

    // ---- Adjustments / Filters ------------------------------------------

    fun updateAdjustments(adjustments: Adjustments) {
        _uiState.update { it?.copy(adjustments = adjustments) }
        schedulePreviewRecompute()
    }

    fun updateFilter(filter: FilterPreset) {
        _uiState.update { it?.copy(filter = filter) }
        schedulePreviewRecompute()
    }

    fun updateFilterIntensity(intensity: Float) {
        _uiState.update { it?.copy(filterIntensity = intensity) }
        schedulePreviewRecompute()
    }

    // ---- Crop ------------------------------------------------------------

    fun applyCrop(newBaseBitmap: Bitmap, cropRectNormalized: Rect) {
        val s = _uiState.value ?: return
        val remapped = s.elements.map { it.remapForCrop(cropRectNormalized) }
        _uiState.update { it?.copy(baseBitmap = newBaseBitmap, elements = remapped) }
        schedulePreviewRecompute()
    }

    fun applyRotation90(newBaseBitmap: Bitmap) {
        val s = _uiState.value ?: return
        val remapped = s.elements.map { it.remapForRotation90CW() }
        _uiState.update { it?.copy(baseBitmap = newBaseBitmap, elements = remapped) }
        schedulePreviewRecompute()
    }

    fun applyFlip(newBaseBitmap: Bitmap, horizontal: Boolean) {
        val s = _uiState.value ?: return
        val remapped = s.elements.map { it.remapForFlip(horizontal) }
        _uiState.update { it?.copy(baseBitmap = newBaseBitmap, elements = remapped) }
        schedulePreviewRecompute()
    }

    // ---- Save --------------------------------------------------------

    fun setSaving(saving: Boolean) {
        _uiState.update { it?.copy(isSaving = saving) }
    }

    fun setSavedMessage(message: String?) {
        _uiState.update { it?.copy(lastSavedMessage = message) }
    }

    suspend fun exportAndSave(): Uri? {
        val s = _uiState.value ?: return null
        return withContext(Dispatchers.Default) {
            val processed = ImageProcessing.applyAll(s.baseBitmap, s.adjustments, s.filter, s.filterIntensity)
            val flattened = MarkupRenderer.renderElements(processed, s.elements)
            val name = "SketchStudio_${System.currentTimeMillis()}.png"
            MediaStoreSaver.saveImageToGallery(context, flattened, name)
        }
    }

    // ---- Preview -------------------------------------------------------

    private fun schedulePreviewRecompute() {
        previewJob?.cancel()
        previewJob = scope.launch {
            delay(50)
            val s = _uiState.value ?: return@launch
            _uiState.update { it?.copy(isProcessing = true) }
            val result = if (s.adjustments.isNeutral && s.filter == FilterPreset.NONE) {
                s.baseBitmap
            } else {
                withContext(Dispatchers.Default) {
                    ImageProcessing.applyAll(s.baseBitmap, s.adjustments, s.filter, s.filterIntensity)
                }
            }
            _uiState.update { it?.copy(previewBitmap = result, isProcessing = false) }
        }
    }

    companion object {
        const val EDIT_MAX_DIMENSION = 1600
    }
}
