package com.ravager.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.ravager.launcher.apps.AppEntry
import com.ravager.launcher.apps.AppSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CardArt(val banner: Drawable?, val icon: Drawable?, val accent: Int)

/** Loads banners and icons away from the UI thread and keeps them in memory while the launcher runs. */
class CardArtLoader(
    private val source: AppSource,
    private val scope: CoroutineScope,
) {
    private val cache = HashMap<String, CardArt>()
    private val waiting = HashMap<String, MutableList<(CardArt) -> Unit>>()

    fun peek(pkg: String): CardArt? = cache[pkg]

    fun load(entry: AppEntry, onReady: (CardArt) -> Unit) {
        cache[entry.packageName]?.let { return onReady(it) }
        val callbacks = waiting[entry.packageName]
        if (callbacks != null) {
            callbacks.add(onReady)
            return
        }
        waiting[entry.packageName] = mutableListOf(onReady)
        scope.launch {
            val art = withContext(Dispatchers.Default) {
                // One app with a broken image must not affect the rest of the screen.
                runCatching { read(entry) }.getOrDefault(CardArt(null, null, AccentColor.NEUTRAL))
            }
            cache[entry.packageName] = art
            waiting.remove(entry.packageName)?.forEach { it(art) }
        }
    }

    fun forget(pkg: String) {
        cache.remove(pkg)
    }

    private fun read(entry: AppEntry): CardArt {
        val banner = source.banner(entry)
        val icon = if (banner == null) source.icon(entry) else null
        val accent = (banner ?: icon)?.let { AccentColor.dominant(samplePixels(it)) } ?: AccentColor.NEUTRAL
        return CardArt(banner, icon, accent)
    }

    private fun samplePixels(drawable: Drawable): IntArray {
        val width = 24
        val height = 14
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val previous = drawable.copyBounds()
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = previous
        return IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }
            .also { bitmap.recycle() }
    }
}
