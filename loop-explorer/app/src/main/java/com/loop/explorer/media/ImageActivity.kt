package com.loop.explorer.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.loop.explorer.R
import com.loop.explorer.files.FileKind
import com.loop.explorer.files.Listing
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.ScreenErrors
import com.loop.explorer.ui.face
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** Full-screen photos. Left and right go through the photos of the folder; OK shows the name. */
class ImageActivity : BaseActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private lateinit var image: ImageView
    private lateinit var caption: TextView
    private var photos: List<File> = emptyList()
    private var index = 0
    private var job: Job? = null
    private val hideCaption = Runnable { caption.animate().alpha(0f).setDuration(300).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = AppSettings(this)
        val theme = Themes.byId(settings.themeId)
        val unit = resources.displayMetrics.widthPixels / 100f
        image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        caption = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.2f)
            typeface = theme.face()
            val pad = (unit * 0.8f).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            background = GradientDrawable().apply {
                setColor(0xB3000000.toInt())
                cornerRadius = unit * 2f
            }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                caption,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (unit * 2.4f).toInt() },
            )
        }
        setContentView(root)
        val start = File(intent.getStringExtra(EXTRA_FILE).orEmpty())
        photos = Listing.siblings(start, settings.showHidden, settings.sortOrder, setOf(FileKind.IMAGE))
        index = photos.indexOf(start).coerceAtLeast(0)
        show()
    }

    private fun show() {
        val file = photos.getOrNull(index) ?: return finish()
        caption.text = getString(R.string.photo_caption, file.name, index + 1, photos.size)
        caption.alpha = 1f
        caption.removeCallbacks(hideCaption)
        caption.postDelayed(hideCaption, CAPTION_MS)
        job?.cancel()
        image.setImageDrawable(null)
        job = scope.launch {
            val drawable = withContext(Dispatchers.IO) { runCatching { decode(file) }.getOrNull() }
            if (drawable == null) {
                caption.text = getString(R.string.photo_failed, file.name)
                return@launch
            }
            image.setImageDrawable(drawable)
            (drawable as? Animatable)?.start()
        }
    }

    /** Never keeps more pixels than the screen shows; animated GIFs play on Android 9 or later. */
    private fun decode(file: File): Drawable? {
        val screen = resources.displayMetrics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = maxOf(info.size.width / screen.widthPixels.toFloat(), info.size.height / screen.heightPixels.toFloat())
                if (scale > 1f) decoder.setTargetSize((info.size.width / scale).toInt(), (info.size.height / scale).toInt())
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= screen.widthPixels || bounds.outHeight / (sample * 2) >= screen.heightPixels) sample *= 2
        val bitmap: Bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> step(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> step(-1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                caption.alpha = 1f
                caption.removeCallbacks(hideCaption)
                caption.postDelayed(hideCaption, CAPTION_MS)
            }
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    private fun step(delta: Int) {
        if (photos.size < 2) return
        index = (index + delta + photos.size) % photos.size
        show()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val CAPTION_MS = 4_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun intent(context: Context, file: File): Intent = Intent(context, ImageActivity::class.java).putExtra(EXTRA_FILE, file.path)
    }
}
