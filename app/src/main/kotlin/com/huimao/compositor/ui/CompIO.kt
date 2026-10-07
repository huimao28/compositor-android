package com.huimao.compositor.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.huimao.compositor.core.LayerKind
import com.huimao.compositor.core.PixelSession
import com.huimao.compositor.core.RasterImage
import com.huimao.compositor.core.alphaOf
import com.huimao.compositor.core.argb
import com.huimao.compositor.core.blueOf
import com.huimao.compositor.core.compositeDocument
import com.huimao.compositor.core.documentFromManifestJson
import com.huimao.compositor.core.greenOf
import com.huimao.compositor.core.redOf
import com.huimao.compositor.core.toManifestJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `.comp` package + image file IO over Storage Access Framework.
 *
 * A `.comp` is a directory: `manifest.json` + `images/<layerId>.png`.
 * Manifest read/write lives in `:core`; this file handles Android storage.
 */

/** Premultiplied Android bitmap → straight-alpha [RasterImage]. */
fun Bitmap.toRasterImage(): RasterImage {
    val w = width
    val h = height
    val px = IntArray(w * h)
    getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val p = px[i]
        val a = p ushr 24
        px[i] = when (a) {
            0 -> 0
            255 -> p
            else -> argb(
                a,
                (p ushr 16 and 0xff) * 255 / a,
                (p ushr 8 and 0xff) * 255 / a,
                (p and 0xff) * 255 / a,
            )
        }
    }
    return RasterImage(w, h, px)
}

/** Opens a `.comp` directory picked with `OpenDocumentTree`. */
suspend fun openComp(context: Context, treeUri: Uri): PixelSession =
    withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("无法打开目录")
        val manifestFile = tree.findFile("manifest.json") ?: error("目录中没有 manifest.json")
        val text = context.contentResolver.openInputStream(manifestFile.uri)
            ?.bufferedReader()?.readText() ?: error("无法读取 manifest.json")
        val document = try {
            documentFromManifestJson(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("不是有效的 .comp 工程：${e.message}")
        }
        val imagesDir = tree.findFile("images")
        val rasters = mutableMapOf<String, RasterImage>()
        for (layer in document.layers) {
            if (layer.kind != LayerKind.RASTER) continue
            var bmp: Bitmap? = null
            val file = layer.imageFile
            if (file != null && imagesDir != null) {
                val img = imagesDir.findFile(file)
                if (img != null) {
                    bmp = context.contentResolver.openInputStream(img.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
            }
            rasters[layer.id] = if (bmp != null) {
                try {
                    bmp.toRasterImage()
                } finally {
                    bmp.recycle()
                }
            } else {
                // Blank layer: transparent raster sized to its transform.
                RasterImage.transparent(
                    layer.transform.width.toInt().coerceAtLeast(1),
                    layer.transform.height.toInt().coerceAtLeast(1),
                )
            }
        }
        PixelSession(document, rasters)
    }

/** Saves the session as a `.comp` directory. */
suspend fun saveComp(context: Context, treeUri: Uri, session: PixelSession) =
    withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: error("无法打开目录")
        val doc = session.document.copy(
            layers = session.document.layers.map { l ->
                if (l.kind == LayerKind.RASTER && session.rasters.containsKey(l.id)) {
                    l.copy(imageFile = "${l.id}.png")
                } else {
                    l.copy(imageFile = null)
                }
            },
        )
        writeTextFile(context, tree, "manifest.json", "application/json", doc.toManifestJson())
        var imagesDir = tree.findFile("images")
        if (imagesDir == null || !imagesDir.isDirectory) {
            imagesDir = tree.createDirectory("images") ?: error("无法创建 images 目录")
        }
        val keep = mutableSetOf<String>()
        for ((id, raster) in session.rasters) {
            val name = "$id.png"
            keep.add(name)
            val bmp = raster.toAndroidBitmap()
            try {
                writeBitmap(context, imagesDir, name, bmp)
            } finally {
                bmp.recycle()
            }
        }
        // Drop PNGs of deleted layers.
        imagesDir.listFiles().forEach { f ->
            if (f.isFile && f.name?.endsWith(".png") == true && f.name !in keep) {
                f.delete()
            }
        }
    }

/** Imports a PNG/JPEG picked with `OpenDocument` as a new raster layer. */
suspend fun importImage(context: Context, uri: Uri): Pair<String, RasterImage> =
    withContext(Dispatchers.IO) {
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: error("无法解码图片")
        try {
            val name = queryDisplayName(context, uri)?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() } ?: "导入图层"
            name to bmp.toRasterImage()
        } finally {
            bmp.recycle()
        }
    }

/** Exports the flattened composite as PNG or JPEG. */
suspend fun exportFlattened(context: Context, uri: Uri, session: PixelSession, jpeg: Boolean) =
    withContext(Dispatchers.IO) {
        var bmp = compositeDocument(session.document, session.rasters).toAndroidBitmap()
        try {
            if (jpeg) {
                // JPEG has no alpha: flatten over white first.
                val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(bmp, 0f, 0f, null)
                }
                bmp.recycle()
                bmp = flat
            }
            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (jpeg) bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                else bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: error("无法写入文件")
        } finally {
            bmp.recycle()
        }
    }

private fun queryDisplayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
    return null
}

private fun writeTextFile(context: Context, dir: DocumentFile, name: String, mime: String, text: String) {
    var file = dir.findFile(name)
    if (file == null || !file.isFile) {
        file = dir.createFile(mime, name) ?: error("无法创建 $name")
    }
    context.contentResolver.openOutputStream(file.uri, "wt")?.bufferedWriter()?.use {
        it.write(text)
    } ?: error("无法写入 $name")
}

private fun writeBitmap(context: Context, dir: DocumentFile, name: String, bmp: Bitmap) {
    var file = dir.findFile(name)
    if (file == null || !file.isFile) {
        file = dir.createFile("image/png", name) ?: error("无法创建 $name")
    }
    context.contentResolver.openOutputStream(file.uri, "wt")?.use { out ->
        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) error("PNG 编码失败")
    } ?: error("无法写入 $name")
}
