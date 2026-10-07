package com.huimao.compositor.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.compositor.core.BlendMode
import com.huimao.compositor.core.BrushTip
import com.huimao.compositor.core.Layer
import com.huimao.compositor.core.LayerKind
import com.huimao.compositor.core.PixelHistory
import com.huimao.compositor.core.PixelSession
import com.huimao.compositor.core.RasterImage
import com.huimao.compositor.core.StrokePoint
import com.huimao.compositor.core.addGroup
import com.huimao.compositor.core.addRasterLayer
import com.huimao.compositor.core.applyStroke
import com.huimao.compositor.core.argb
import com.huimao.compositor.core.compositeDocument
import com.huimao.compositor.core.compositeDocumentReplacing
import com.huimao.compositor.core.deleteLayer
import com.huimao.compositor.core.newPixelSession
import com.huimao.compositor.core.paintStroke
import com.huimao.compositor.core.renameLayer
import com.huimao.compositor.core.setActiveLayer
import com.huimao.compositor.core.setLayerBlendMode
import com.huimao.compositor.core.setLayerOpacity
import com.huimao.compositor.core.setLayerVisible
import com.huimao.compositor.core.moveLayer
import com.huimao.compositor.core.toLayerPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTool { BRUSH, HAND }

/**
 * UI state holder. Translates gestures and panel actions into :core calls;
 * owns the undo history and the composited display image.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private var history = PixelHistory(newPixelSession(1920, 1080))

    var session by mutableStateOf(history.current)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    var tool by mutableStateOf(EditorTool.BRUSH)
    var brushTip by mutableStateOf(
        BrushTip(diameter = 24f, hardness = 0.5f, opacity = 1f, color = argb(255, 25, 25, 25)),
    )

    var viewScale by mutableStateOf(1f)
        private set
    var viewOffset by mutableStateOf(Offset.Zero)
        private set
    /** Bumped when the canvas should refit the document (new/open). */
    var fitNonce by mutableStateOf(0)
        private set

    private val _display = MutableStateFlow<ImageBitmap?>(null)
    val display: StateFlow<ImageBitmap?> = _display

    /** One-shot user-facing message (error or info). */
    var message by mutableStateOf<String?>(null)

    var compDirUri: Uri? by mutableStateOf(null)
        private set
    var docTitle by mutableStateOf("未命名")
        private set

    // In-progress stroke.
    private var strokeLayer: Layer? = null
    private var strokeRaster: RasterImage? = null
    private var strokeTip: BrushTip? = null
    private val strokePoints = mutableListOf<StrokePoint>()
    private var renderJob: Job? = null
    private var baseCache: Pair<PixelSession, RasterImage>? = null

    init {
        refreshDisplay()
    }

    // ---------- history ----------

    private fun afterHistoryChange() {
        session = history.current
        canUndo = history.canUndo
        canRedo = history.canRedo
        baseCache = null
        refreshDisplay()
    }

    private fun commit(next: PixelSession) {
        if (next == session) return
        history.commit(next)
        afterHistoryChange()
    }

    fun undo() {
        if (history.undo()) afterHistoryChange()
    }

    fun redo() {
        if (history.redo()) afterHistoryChange()
    }

    fun newDocument(w: Int, h: Int) {
        history = PixelHistory(newPixelSession(w, h))
        compDirUri = null
        docTitle = "未命名"
        afterHistoryChange()
        fitNonce++
    }

    fun openSession(loaded: PixelSession, title: String, dirUri: Uri?) {
        history = PixelHistory(loaded)
        compDirUri = dirUri
        docTitle = title
        afterHistoryChange()
        fitNonce++
    }

    fun setCompDir(uri: Uri, title: String) {
        compDirUri = uri
        docTitle = title
    }

    // ---------- view transform ----------

    fun fitToView(viewW: Float, viewH: Float) {
        if (viewW <= 0f || viewH <= 0f) return
        val doc = session.document
        val s = minOf(viewW / doc.width, viewH / doc.height).coerceIn(0.05f, 8f)
        viewScale = s
        viewOffset = Offset((viewW - doc.width * s) / 2f, (viewH - doc.height * s) / 2f)
    }

    fun applyZoomPan(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (viewScale * zoom).coerceIn(0.05f, 32f)
        val k = newScale / viewScale
        viewOffset = centroid + (viewOffset - centroid) * k + pan
        viewScale = newScale
    }

    /** Screen pixels → document pixels. */
    fun toDoc(p: Offset): Offset = (p - viewOffset) / viewScale

    // ---------- layers ----------

    fun addLayer() {
        val doc = session.document
        commit(
            session.addRasterLayer(
                "图层 ${doc.layers.size + 1}",
                RasterImage.transparent(doc.width, doc.height),
            ),
        )
    }

    fun addGroup() {
        commit(session.addGroup("分组 ${session.document.layers.size + 1}"))
    }

    fun deleteLayer(id: String) = commit(session.deleteLayer(id))

    fun renameLayer(id: String, name: String) {
        try {
            commit(session.renameLayer(id, name))
        } catch (e: IllegalArgumentException) {
            message = e.message
        }
    }

    /** Moves toward the top (display up) or bottom. */
    fun moveLayer(id: String, towardTop: Boolean) {
        val layers = session.document.layers
        val idx = layers.indexOfFirst { it.id == id }
        if (idx < 0) return
        commit(session.moveLayer(id, if (towardTop) idx + 1 else idx - 1))
    }

    fun setLayerVisible(id: String, visible: Boolean) =
        commit(session.setLayerVisible(id, visible))

    fun setLayerOpacity(id: String, opacity: Float) =
        commit(session.setLayerOpacity(id, opacity.toDouble()))

    fun setLayerBlendMode(id: String, mode: BlendMode) =
        commit(session.setLayerBlendMode(id, mode))

    fun selectLayer(id: String) = commit(session.setActiveLayer(id))

    // ---------- brush strokes ----------

    fun beginStroke(screen: Offset, pressure: Float, stylus: Boolean) {
        val doc = toDoc(screen)
        val layerId = session.document.activeLayerId ?: return
        val layer = session.document.layers.firstOrNull { it.id == layerId } ?: return
        if (layer.kind != LayerKind.RASTER) {
            message = "请选择栅格图层进行绘画"
            return
        }
        val raster = session.rasters[layerId] ?: return
        val lp = layer.toLayerPixels(doc.x.toDouble(), doc.y.toDouble(), raster.width, raster.height)
            ?: return
        strokeLayer = layer
        strokeRaster = raster
        strokeTip = brushTip
        strokePoints.clear()
        strokePoints.add(StrokePoint(lp.first.toFloat(), lp.second.toFloat(), pressure))
        renderFrame()
    }

    fun addStrokePoint(screen: Offset, pressure: Float) {
        val layer = strokeLayer ?: return
        val raster = strokeRaster ?: return
        val doc = toDoc(screen)
        val lp = layer.toLayerPixels(doc.x.toDouble(), doc.y.toDouble(), raster.width, raster.height)
            ?: return
        strokePoints.add(StrokePoint(lp.first.toFloat(), lp.second.toFloat(), pressure))
        renderFrame()
    }

    fun endStroke(commitStroke: Boolean) {
        renderJob?.cancel()
        val layer = strokeLayer
        val tip = strokeTip
        strokeLayer = null
        strokeTip = null
        strokeRaster = null
        if (commitStroke && layer != null && tip != null && strokePoints.isNotEmpty()) {
            commit(session.paintStroke(layer.id, strokePoints.toList(), tip))
        } else {
            refreshDisplay()
        }
        strokePoints.clear()
    }

    // ---------- display ----------

    private fun refreshDisplay() {
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val base = baseCache?.takeIf { it.first == session }?.second
                ?: compositeDocument(session.document, session.rasters)
                    .also { baseCache = session to it }
            _display.value = base.toAndroidBitmap().asImageBitmap()
        }
    }

    /** Re-renders with the in-progress stroke composited exactly (preview). */
    private fun renderFrame() {
        val layer = strokeLayer
        val base = strokeRaster
        val tip = strokeTip
        if (layer == null || base == null || tip == null || strokePoints.isEmpty()) {
            refreshDisplay()
            return
        }
        val points = strokePoints.toList()
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val stroked = applyStroke(base, points, tip)
            val frame = compositeDocumentReplacing(session.document, session.rasters, layer.id, stroked)
            _display.value = frame.toAndroidBitmap().asImageBitmap()
        }
    }

    // ---------- file IO (called from EditorScreen with SAF uris) ----------

    fun importImage(name: String, raster: RasterImage) {
        commit(session.addRasterLayer(name, raster))
        message = "已导入 $name"
    }

    suspend fun saveToCurrentDir(): Boolean {
        val uri = compDirUri ?: return false
        return try {
            withContext(Dispatchers.IO) {
                saveComp(getApplication(), uri, session)
            }
            message = "已保存"
            true
        } catch (e: Exception) {
            message = "保存失败：${e.message}"
            false
        }
    }
}
