package com.loop.launcher.wallpaper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Movie
import android.graphics.SurfaceTexture
import android.graphics.drawable.Animatable
import android.media.MediaPlayer
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wallpaper behind the home screen: image, GIF or muted looping video. Animated ones only run
 * between [resume] and [pause], so nothing plays while another app is open. Any file that fails
 * calls [onBroken] and leaves the theme background showing.
 */
class WallpaperLayer(
    context: Context,
    private val scope: CoroutineScope,
    private val onBroken: () -> Unit,
) : FrameLayout(context) {
    private var content: View? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var videoFile: File? = null
    private var active = false
    private var videoReady = false
    private var shownCount = 0
    private val dim = View(context)

    init {
        addView(dim, LayoutParams(MATCH, MATCH))
    }

    fun show(choice: Wallpaper, files: WallpaperFiles, level: DimLevel) {
        clear()
        dim.setBackgroundColor(Color.argb(level.alpha, 0, 0, 0))
        visibility = if (choice == Wallpaper.None) GONE else VISIBLE
        when (choice) {
            Wallpaper.None -> Unit
            is Wallpaper.Bundled -> showImage { decodeAsset(choice.asset) }
            is Wallpaper.Imported -> showImported(choice.name, files)
        }
    }

    private fun showImported(name: String, files: WallpaperFiles) {
        val file = files.resolve(name)
        if (file == null) {
            onBroken()
            return
        }
        when (WallpaperKind.of(name)) {
            WallpaperKind.IMAGE -> showImage { decodeFile(file) }
            WallpaperKind.GIF -> showGif(file)
            WallpaperKind.VIDEO -> showVideo(file)
            null -> onBroken()
        }
    }

    private fun place(view: View) {
        content = view
        addView(view, 0, LayoutParams(MATCH, MATCH))
    }

    private fun showImage(decode: () -> Bitmap?) {
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        place(image)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { runCatching(decode).getOrNull() }
            if (content !== image) return@launch
            if (bitmap == null) onBroken() else image.setImageBitmap(bitmap)
        }
    }

    private fun decodeAsset(asset: String): Bitmap? {
        val path = "${BundledWallpapers.FOLDER}/$asset"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) }
        return context.assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** Never keeps more pixels than the screen shows, which saves a lot of memory on weak boxes. */
    private fun sampleFor(bounds: BitmapFactory.Options): Int {
        val screen = resources.displayMetrics
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= screen.widthPixels && bounds.outHeight / (sample * 2) >= screen.heightPixels) {
            sample *= 2
        }
        return sample
    }

    private fun showGif(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) showModernGif(file) else showOldGif(file)
    }

    /** Decodes on its own thread and draws with the graphics chip, so navigation stays smooth. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun showModernGif(file: File) {
        val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        place(image)
        scope.launch {
            val drawable = withContext(Dispatchers.IO) {
                runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull()
            }
            if (content !== image) return@launch
            if (drawable == null) {
                onBroken()
                return@launch
            }
            image.setImageDrawable(drawable)
            if (active) (drawable as? Animatable)?.start()
        }
    }

    @Suppress("DEPRECATION")
    private fun showOldGif(file: File) {
        val asked = shownCount
        scope.launch {
            val movie = withContext(Dispatchers.IO) { runCatching { Movie.decodeFile(file.path) }.getOrNull() }
            // Another wallpaper was chosen while this one was being read.
            if (asked != shownCount) return@launch
            if (movie == null || movie.width() <= 0) {
                onBroken()
                return@launch
            }
            place(GifView(context, movie).also { it.running = active })
        }
    }

    private fun showVideo(file: File) {
        videoFile = file
        val texture = TextureView(context)
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(st)
                videoReady = true
                if (active) startVideo(texture)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                stopVideo()
                surface?.release()
                surface = null
                videoReady = false
                return true
            }
        }
        place(texture)
    }

    private fun startVideo(texture: TextureView) {
        val file = videoFile ?: return
        val target = surface ?: return
        stopVideo()
        val media = MediaPlayer()
        player = media
        try {
            media.setDataSource(file.path)
            media.setSurface(target)
            media.isLooping = true
            media.setVolume(0f, 0f)
            media.setOnVideoSizeChangedListener { _, w, h -> cropToFill(texture, w, h) }
            media.setOnPreparedListener { it.start() }
            media.setOnErrorListener { _, _, _ ->
                stopVideo()
                onBroken()
                true
            }
            media.prepareAsync()
        } catch (_: IOException) {
            stopVideo()
            onBroken()
        } catch (_: IllegalStateException) {
            stopVideo()
            onBroken()
        }
    }

    private fun cropToFill(texture: TextureView, videoWidth: Int, videoHeight: Int) {
        if (videoWidth <= 0 || videoHeight <= 0 || texture.width <= 0) return
        val viewWidth = texture.width.toFloat()
        val viewHeight = texture.height.toFloat()
        val scale = maxOf(viewWidth / videoWidth, viewHeight / videoHeight)
        val matrix = Matrix()
        matrix.setScale(videoWidth * scale / viewWidth, videoHeight * scale / viewHeight, viewWidth / 2, viewHeight / 2)
        texture.setTransform(matrix)
    }

    private fun stopVideo() {
        player?.let { runCatching { it.release() } }
        player = null
    }

    fun resume() {
        active = true
        (content as? GifView)?.running = true
        ((content as? ImageView)?.drawable as? Animatable)?.start()
        (content as? TextureView)?.let { if (videoReady && player == null) startVideo(it) }
    }

    fun pause() {
        active = false
        (content as? GifView)?.running = false
        ((content as? ImageView)?.drawable as? Animatable)?.stop()
        stopVideo()
    }

    private fun clear() {
        shownCount++
        stopVideo()
        videoFile = null
        content?.let(::removeView)
        content = null
    }

    fun release() = clear()

    /** Plays an animated GIF, scaled to fill the screen. */
    @Suppress("DEPRECATION")
    @SuppressLint("ViewConstructor")
    private class GifView(context: Context, private val movie: Movie) : View(context) {
        private val start = SystemClock.uptimeMillis()
        var running = false
            set(value) {
                field = value
                if (value) postInvalidateOnAnimation()
            }

        init {
            // Movie draws through the software path.
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val duration = movie.duration().takeIf { it > 0 } ?: DEFAULT_FRAME_MS
            movie.setTime(((SystemClock.uptimeMillis() - start) % duration).toInt())
            val scale = maxOf(width.toFloat() / movie.width(), height.toFloat() / movie.height())
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate((width / scale - movie.width()) / 2, (height / scale - movie.height()) / 2)
            movie.draw(canvas, 0f, 0f)
            canvas.restore()
            if (running) postInvalidateDelayed(FRAME_MS)
        }

        private companion object {
            const val FRAME_MS = 40L
            const val DEFAULT_FRAME_MS = 1000
        }
    }

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
    }
}
