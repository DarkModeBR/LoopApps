package com.loop.explorer.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/** Small pictures for photos, videos, songs with a cover and APKs, read off the main thread. */
class Thumbs(context: Context, private val size: Int) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    fun hasPicture(kind: FileKind) = kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.AUDIO || kind == FileKind.APK

    /** Answers on the main thread; right away when it is already known. */
    fun load(item: FileItem, done: (Bitmap?) -> Unit) {
        if (!hasPicture(item.kind)) return done(null)
        val key = "${item.file.path}|${item.modified}"
        cache.get(key)?.let { return done(it) }
        if (key in missing) return done(null)
        worker.execute {
            val bitmap = runCatching { read(item) }.getOrNull()
            main.post {
                if (bitmap == null) missing += key else cache.put(key, bitmap)
                done(bitmap)
            }
        }
    }

    private fun read(item: FileItem): Bitmap? = when (item.kind) {
        FileKind.IMAGE -> decode(item.file)
        FileKind.VIDEO -> frame(item.file)
        FileKind.AUDIO -> cover(item.file)
        FileKind.APK -> apkIcon(item.file)
        else -> null
    }

    private fun decode(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun frame(file: File): Bitmap? = retrieve(file) { it.getFrameAtTime(FRAME_US)?.let(::shrink) }

    private fun cover(file: File): Bitmap? = retrieve(file) { retriever ->
        retriever.embeddedPicture?.let { bytes ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) })
        }
    }

    private fun <T> retrieve(file: File, read: (MediaMetadataRetriever) -> T?): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            read(retriever)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun apkIcon(file: File): Bitmap? {
        val manager = app.packageManager
        val info = manager.getPackageArchiveInfo(file.path, 0)?.applicationInfo ?: return null
        // The archive is not installed, so the icon has to be read from the file itself.
        info.sourceDir = file.path
        info.publicSourceDir = file.path
        return toBitmap(info.loadIcon(manager))
    }

    private fun toBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return shrink(drawable.bitmap)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun shrink(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= size * 2) return bitmap
        val height = (bitmap.height * size * 2f / bitmap.width).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, size * 2, height, true)
    }

    private fun sample(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= size * 2 && height / (sample * 2) >= size) sample *= 2
        return sample
    }

    private companion object {
        const val CACHE_BYTES = 16 * 1024 * 1024
        const val FRAME_US = 2_000_000L
        val worker = Executors.newFixedThreadPool(2)
    }
}
