package com.huimao.compositor.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huimao.compositor.core.BlendMode
import com.huimao.compositor.core.BrushTip
import com.huimao.compositor.core.Dab
import com.huimao.compositor.core.Document
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
import com.huimao.compositor.core.compositeLayerOver
import com.huimao.compositor.core.dabAt
import com.huimao.compositor.core.dabCoverage
import com.huimao.compositor.core.dabSpacing
import com.huimao.compositor.core.deleteLayer
import com.huimao.compositor.core.docScale
import com.huimao.compositor.core.newPixelSession
import com.huimao.compositor.core.paintStroke
import com.huimao.compositor.core.renameLayer
import com.huimao.compositor.core.setActiveLayer
import com.huimao.compositor.core.setLayerBlendMode
import com.huimao.compositor.core.setLayerOpacity
import com.huimao.compositor.core.setLayerVisible
import com.huimao.compositor.core.moveLayer
import com.huimao.compositor.core.toDocPixels
import com.huimao.compositor.core.toLayerPixels
import com.huimao.compositor.core.visibleLayers
import com.huimao.compositor.core.walkSegment
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditorTool { BRUSH, HAND }

/**
 * One dab of the live stroke preview, in document pixels.
 * Drawn by the GPU over the last committed composite; [alpha] folds the
 * paint alpha and the tip opacity (exact for opaque tips).
 */
data class PreviewDab(
    val docX: Float,
    val docY: Float,
    val docDiameter: Float,
    val alpha: Float,
)

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
    private var baseCache: Pair<Pair<PixelSession, Map<String, Float>>, RasterImage>? = null
    // Composite of the visible layers strictly below a layer, cached so
    // incremental previews (stroke on non-top layer, opacity drags) only
    // redraw the layers at/above it.
    private data class BelowKey(
        val session: PixelSession,
        val layerId: String,
        val opacityPreview: Map<String, Float>,
    )
    private var belowCache: Pair<BelowKey, RasterImage>? = null

    /**
     * Live stroke preview, drawn by the GPU. Dabs are appended incrementally
     * per pointer move (no CPU recomposite); the exact CPU composite lands
     * on stroke commit. Only used when painting the topmost visible layer;
     * otherwise the CPU preview path ([renderFrame]) is used.
     */
    val previewDabs = mutableStateListOf<PreviewDab>()
    var previewDabColor by mutableStateOf(0)
        private set
    var previewDabBlend by mutableStateOf(BlendMode.NORMAL)
        private set
    private var dabCarry = 0f
    private var dabLast = StrokePoint(0f, 0f)
    private var dabSpacingVal = 0.5f
    private var dabDocScale = 1.0
    private var useGpuPreview = false
    private var dabSpriteCache: Pair<Float, ImageBitmap>? = null
    /**
     * Bumped per stroke. A committed stroke's preview dabs are cleared
     * atomically with the new base image ([clearPreviewToken]); a newer
     * stroke's dabs are never cleared by an older commit.
     */
    private var previewToken = 0
    private var clearPreviewToken = -1

    /** Live opacity preview while a slider drags; committed on release. */
    var opacityPreview by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    init {
        refreshDisplay()
    }

    // ---------- history ----------

    private fun afterHistoryChange() {
        session = history.current
        canUndo = history.canUndo
        canRedo = history.canRedo
        baseCache = null
        belowCache = null
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
        previewDabs.clear()
        afterHistoryChange()
        fitNonce++
    }

    fun openSession(loaded: PixelSession, title: String, dirUri: Uri?) {
        history = PixelHistory(loaded)
        compDirUri = dirUri
        docTitle = title
        previewDabs.clear()
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

    /**
     * Slider drag: preview only, no history entry and no blocking — the
     * display refresh is coalesced by cancelling the in-flight job.
     */
    fun previewOpacity(id: String, opacity: Float) {
        opacityPreview = opacityPreview + (id to opacity)
        refreshOpacityPreview(id)
    }

    /** Slider release: the previewed value becomes one undo step. */
    fun commitOpacity(id: String) {
        val o = opacityPreview[id] ?: return
        opacityPreview = opacityPreview - id
        commit(session.setLayerOpacity(id, o.toDouble()))
    }

    /**
     * Incremental opacity preview: only the dragged layer and the layers
     * above it are redrawn; everything below is cached. No history entry
     * until [commitOpacity].
     */
    private fun refreshOpacityPreview(layerId: String) {
        val doc = displayDocument()
        val layers = doc.visibleLayers()
        val idx = layers.indexOfFirst { it.id == layerId }
        if (idx < 0) {
            refreshDisplay()
            return
        }
        val key = BelowKey(session, layerId, opacityPreview)
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val below = belowCache?.takeIf { it.first == key }?.second
                ?: RasterImage.transparent(doc.width, doc.height).also { img ->
                    drawLayers(img, doc, layers, session.rasters, 0, idx)
                    belowCache = key to img
                }
            val frame = below.copy()
            val raster = session.rasters[layerId]
            if (raster != null) compositeLayerOver(frame, doc, layers[idx], raster)
            drawLayers(frame, doc, layers, session.rasters, idx + 1, layers.size)
            _display.value = frame.toAndroidBitmap().asImageBitmap()
        }
    }

    private fun drawLayers(
        dst: RasterImage,
        doc: Document,
        layers: List<Layer>,
        rasters: Map<String, RasterImage>,
        from: Int,
        to: Int,
    ) {
        for (i in from until to) {
            val l = layers[i]
            compositeLayerOver(dst, doc, l, rasters[l.id] ?: continue)
        }
    }

    /** The document as currently displayed (opacity previews applied). */
    private fun displayDocument(): Document {
        if (opacityPreview.isEmpty()) return session.document
        return session.document.copy(
            layers = session.document.layers.map { l ->
                opacityPreview[l.id]?.let { l.copy(opacity = it.toDouble()) } ?: l
            },
        )
    }

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
        val tip = brushTip
        strokeLayer = layer
        strokeRaster = raster
        strokeTip = tip
        strokePoints.clear()
        val first = StrokePoint(lp.first.toFloat(), lp.second.toFloat(), pressure)
        strokePoints.add(first)

        // GPU preview when painting the topmost visible layer: dabs are drawn
        // by the Canvas at 60fps; the CPU stays out of the per-frame path.
        useGpuPreview = session.document.visibleLayers().lastOrNull()?.id == layer.id
        previewToken++
        if (useGpuPreview) {
            previewDabs.clear()
            previewDabColor = tip.color
            previewDabBlend = layer.blendMode
            dabDocScale = layer.docScale(raster.width, raster.height)
            dabSpacingVal = dabSpacing(tip).coerceAtLeast(0.5f)
            previewDabs.add(toPreviewDab(dabAt(first, tip), layer, raster, tip))
            dabLast = first
            dabCarry = 0f
        } else {
            belowCache = null
            renderFrame()
        }
    }

    fun addStrokePoint(screen: Offset, pressure: Float) {
        val layer = strokeLayer ?: return
        val raster = strokeRaster ?: return
        val tip = strokeTip ?: return
        val doc = toDoc(screen)
        val lp = layer.toLayerPixels(doc.x.toDouble(), doc.y.toDouble(), raster.width, raster.height)
            ?: return
        val p = StrokePoint(lp.first.toFloat(), lp.second.toFloat(), pressure)
        strokePoints.add(p)
        if (useGpuPreview) {
            dabCarry = walkSegment(dabLast, p, dabCarry, dabSpacingVal) { sp ->
                previewDabs.add(toPreviewDab(dabAt(sp, tip), layer, raster, tip))
            }
            dabLast = p
        } else {
            renderFrame()
        }
    }

    fun endStroke(commitStroke: Boolean) {
        renderJob?.cancel()
        val layer = strokeLayer
        val tip = strokeTip
        strokeLayer = null
        strokeTip = null
        strokeRaster = null
        belowCache = null
        if (commitStroke && layer != null && tip != null && strokePoints.isNotEmpty()) {
            // Clear the GPU preview atomically with the new base image so the
            // stroke never flickers out between commit and recomposite.
            clearPreviewToken = previewToken
            commit(session.paintStroke(layer.id, strokePoints.toList(), tip))
        } else {
            previewDabs.clear()
            refreshDisplay()
        }
        strokePoints.clear()
    }

    private fun toPreviewDab(dab: Dab, layer: Layer, raster: RasterImage, tip: BrushTip): PreviewDab {
        val (dx, dy) = layer.toDocPixels(dab.x.toDouble(), dab.y.toDouble(), raster.width, raster.height)
        return PreviewDab(
            docX = dx.toFloat(),
            docY = dy.toFloat(),
            docDiameter = (dab.diameter * dabDocScale).toFloat(),
            alpha = dab.alpha * tip.opacity,
        )
    }

    /**
     * White radial dab sprite; tinted with the paint color at draw time.
     * The alpha profile matches [dabCoverage], so the GPU preview agrees
     * with the CPU stroke for opaque tips.
     */
    fun dabSprite(hardness: Float): ImageBitmap {
        val h = (hardness * 20).roundToInt() / 20f
        dabSpriteCache?.takeIf { it.first == h }?.let { return it.second }
        val size = 64
        val px = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val d = hypot(x + 0.5f - size / 2f, y + 0.5f - size / 2f) / (size / 2f)
                val cov = dabCoverage(d, h, size.toFloat())
                px[y * size + x] = ((cov * 255 + 0.5f).toInt() shl 24) or 0x00FFFFFF
            }
        }
        val bmp = android.graphics.Bitmap.createBitmap(
            px, size, size, android.graphics.Bitmap.Config.ARGB_8888,
        )
        return bmp.asImageBitmap().also { dabSpriteCache = h to it }
    }

    // ---------- display ----------

    private fun refreshDisplay() {
        renderJob?.cancel()
        // The cache key includes live opacity previews.
        val key = session to opacityPreview
        val clearToken = clearPreviewToken
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val base = baseCache?.takeIf { it.first == key }?.second
                ?: compositeDocument(displayDocument(), session.rasters)
                    .also { baseCache = key to it }
            val bmp = base.toAndroidBitmap().asImageBitmap()
            withContext(Dispatchers.Main) {
                if (clearToken >= 0 && clearToken == previewToken) {
                    previewDabs.clear()
                    clearPreviewToken = -1
                }
                _display.value = bmp
            }
        }
    }

    /**
     * Re-renders with the in-progress stroke composited exactly (preview).
     * Only the layers at/above the stroking layer are redrawn per frame;
     * everything below is cached for the stroke's lifetime.
     */
    private fun renderFrame() {
        val layer = strokeLayer
        val base = strokeRaster
        val tip = strokeTip
        if (layer == null || base == null || tip == null || strokePoints.isEmpty()) {
            refreshDisplay()
            return
        }
        val points = strokePoints.toList()
        val doc = displayDocument()
        val layers = doc.visibleLayers()
        val idx = layers.indexOfFirst { it.id == layer.id }
        if (idx < 0) {
            refreshDisplay()
            return
        }
        val cacheKey = BelowKey(session, layer.id, opacityPreview)
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val below = belowCache?.takeIf { it.first == cacheKey }?.second
                ?: RasterImage.transparent(doc.width, doc.height).also { img ->
                    drawLayers(img, doc, layers, session.rasters, 0, idx)
                    belowCache = cacheKey to img
                }
            val stroked = applyStroke(base, points, tip)
            val frame = below.copy()
            compositeLayerOver(frame, doc, layers[idx], stroked)
            drawLayers(frame, doc, layers, session.rasters, idx + 1, layers.size)
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
