package com.sketchstudio.app.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

enum class EditorTab { ADJUST, FILTERS, CROP, MARKUP }

enum class MarkupMode { DRAW, SHAPE, TEXT }

data class EditorUiState(
    val baseBitmap: Bitmap,
    val previewBitmap: Bitmap,
    val elements: List<MarkupElement> = emptyList(),
    val adjustments: Adjustments = Adjustments.Neutral,
    val filter: FilterPreset = FilterPreset.NONE,
    val filterIntensity: Float = 1f,
    val activeTab: EditorTab = EditorTab.MARKUP,
    val activeDrawTool: DrawTool = DrawTool.PEN,
    val activeShapeType: ShapeType = ShapeType.RECTANGLE,
    val markupMode: MarkupMode = MarkupMode.DRAW,
    val currentColor: Color = Color(0xFFFF3B30),
    val currentStrokeWidthFraction: Float = 0.008f,
    val currentOpacity: Float = 1f,
    val isProcessing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val lastSavedMessage: String? = null
)

internal data class HistorySnapshot(
    val baseBitmap: Bitmap,
    val elements: List<MarkupElement>,
    val adjustments: Adjustments,
    val filter: FilterPreset,
    val filterIntensity: Float
)
