package com.ravager.launcher.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import com.ravager.launcher.ui.menu.RowThumb
import java.io.File
import java.util.concurrent.Executors

/** Small pictures of the wallpapers for the menu lists, read away from the main thread. */
class WallpaperThumbs(private val context: Context) {
    private val cache = HashMap<String, Bitmap?>()
    private val waiting = HashMap<String, MutableList<(Bitmap?) -> Unit>>()
    private val main = Handler(Looper.getMainLooper())

    fun bundled(asset: String) = RowThumb { done ->
        load("asset:$asset", done) { readAsset("${BundledWallpapers.FOLDER}/$asset") }
    }

    fun imported(name: String, file: File?) = RowThumb { done ->
        load("file:$name", done) {
            when {
                file == null -> null
                WallpaperKind.of(name) == WallpaperKind.VIDEO -> readVideoFrame(file)
                else -> readFile(file)
            }
        }
    }

    private fun load(key: String, done: (Bitmap?) -> Unit, read: () -> Bitmap?) {
        if (cache.containsKey(key)) return done(cache[key])
        waiting[key]?.let {
            it += done
            return
        }
        waiting[key] = mutableListOf(done)
        worker.execute {
            // A bad file just stays without a picture.
            val bitmap = runCatching(read).getOrNull()
            main.post {
                cache[key] = bitmap
                waiting.remove(key)?.forEach { it(bitmap) }
            }
        }
    }

    private fun readAsset(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) }
        return context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    }

    /** For a GIF this reads its first frame. */
    private fun readFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) })
    }

    private fun readVideoFrame(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            val frame = retriever.frameAtTime ?: return null
            val height = THUMB_WIDTH * frame.height / frame.width.coerceAtLeast(1)
            Bitmap.createScaledBitmap(frame, THUMB_WIDTH, height.coerceAtLeast(1), true).also {
                if (it !== frame) frame.recycle()
            }
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun sampleFor(width: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= THUMB_WIDTH) sample *= 2
        return sample
    }

    private companion object {
        const val THUMB_WIDTH = 240
        val worker = Executors.newSingleThreadExecutor()
    }
}
