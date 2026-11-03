package com.loop.explorer.media

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import com.loop.explorer.R
import com.loop.explorer.files.FileKind
import com.loop.explorer.files.Listing
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.face
import java.io.File
import java.util.Locale

/**
 * Plays videos and music with the box's own decoder. OK pauses, left and right jump 10 seconds,
 * and songs go on to the next one in the folder.
 */
class PlayerActivity : BaseActivity() {
    private lateinit var video: VideoView
    private lateinit var panel: LinearLayout
    private lateinit var name: TextView
    private lateinit var time: TextView
    private lateinit var bar: ProgressBar
    private lateinit var cover: ImageView
    private lateinit var state: ImageView
    private val handler = Handler(Looper.getMainLooper())
    private var playlist: List<File> = emptyList()
    private var index = 0
    private var audio = false
    private var resumeAt = 0
    private var failed = false

    /** Songs that failed in a row; when every one fails, the player closes instead of going round forever. */
    private var failures = 0

    private val tick = object : Runnable {
        override fun run() {
            showTime()
            handler.postDelayed(this, TICK_MS)
        }
    }
    private val hidePanel = Runnable { if (!audio && video.isPlaying) panel.animate().alpha(0f).setDuration(300).start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val settings = AppSettings(this)
        val theme = Themes.byId(settings.themeId)
        val unit = resources.displayMetrics.widthPixels / 100f
        video = VideoView(this)
        cover = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        state = ImageView(this).apply { setColorFilter(Color.WHITE) }
        name = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.6f)
            typeface = theme.face(FontWeight.MEDIUM)
            maxLines = 1
        }
        time = TextView(this).apply {
            setTextColor(0xFFCCCCCC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.1f)
            typeface = theme.face()
        }
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        }
        val hint = TextView(this).apply {
            text = getString(R.string.player_hint)
            setTextColor(0xFF9A9A9A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.95f)
            typeface = theme.face()
        }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (unit * 2f).toInt()
            setPadding(pad * 2, pad, pad * 2, pad)
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xE6000000.toInt(), Color.TRANSPARENT))
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(state, LinearLayout.LayoutParams((unit * 2f).toInt(), (unit * 2f).toInt()).apply { marginEnd = (unit * 1f).toInt() })
                addView(name, LinearLayout.LayoutParams(0, WRAP, 1f))
                addView(time)
            }
            addView(line)
            addView(bar, LinearLayout.LayoutParams(MATCH, (unit * 0.6f).toInt()).apply { topMargin = (unit * 0.6f).toInt() })
            addView(hint, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (unit * 0.5f).toInt() })
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(video, FrameLayout.LayoutParams(MATCH, MATCH, Gravity.CENTER))
            addView(cover, FrameLayout.LayoutParams((unit * 24).toInt(), (unit * 24).toInt(), Gravity.CENTER).apply { bottomMargin = (unit * 4).toInt() })
            addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        }
        setContentView(root)

        video.setOnPreparedListener {
            failed = false
            failures = 0
            if (resumeAt > 0) video.seekTo(resumeAt)
            resumeAt = 0
            video.start()
            showState()
            peek()
        }
        video.setOnCompletionListener { if (audio && playlist.size > 1) step(1) else finish() }
        video.setOnErrorListener { _, _, _ ->
            failed = true
            failures++
            Toast.makeText(this, getString(R.string.player_failed, playlist.getOrNull(index)?.name.orEmpty()), Toast.LENGTH_LONG).show()
            if (audio && playlist.size > 1 && failures < playlist.size) step(1) else finish()
            true
        }

        val start = File(intent.getStringExtra(EXTRA_FILE).orEmpty())
        audio = FileKind.of(start.name) == FileKind.AUDIO
        playlist = Listing.siblings(start, settings.showHidden, settings.sortOrder, setOf(if (audio) FileKind.AUDIO else FileKind.VIDEO))
        index = playlist.indexOf(start).coerceAtLeast(0)
        play()
    }

    private fun play() {
        val file = playlist.getOrNull(index) ?: return finish()
        name.text = file.name
        cover.visibility = if (audio) View.VISIBLE else View.GONE
        if (audio) showCover(file)
        video.setVideoPath(file.path)
    }

    /** Songs show their album cover when the file has one. */
    private fun showCover(file: File) {
        cover.setImageResource(R.drawable.ic_music)
        cover.setColorFilter(0x66FFFFFF)
        Thread {
            val picture = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.path)
                    retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } finally {
                    runCatching { retriever.release() }
                }
            }.getOrNull()
            runOnUiThread {
                if (picture != null && !isDestroyed && playlist.getOrNull(index) == file) {
                    cover.clearColorFilter()
                    cover.setImageBitmap(picture)
                }
            }
        }.start()
    }

    private fun step(delta: Int) {
        if (playlist.size < 2) return
        index = (index + delta + playlist.size) % playlist.size
        video.stopPlayback()
        play()
    }

    private fun showTime() {
        val duration = video.duration
        val position = video.currentPosition
        time.text = if (duration > 0) "${clock(position)} / ${clock(duration)}" else clock(position)
        bar.progress = if (duration > 0) (position.toLong() * 1000 / duration).toInt() else 0
    }

    private fun showState() {
        state.setImageResource(if (video.isPlaying) R.drawable.ic_play else R.drawable.ic_pause)
    }

    /** Shows the panel for a moment; videos hide it again so nothing covers the picture. */
    private fun peek() {
        panel.animate().cancel()
        panel.alpha = 1f
        handler.removeCallbacks(hidePanel)
        handler.postDelayed(hidePanel, PANEL_MS)
        showTime()
    }

    private fun clock(ms: Int): String {
        val total = ms / 1000
        val hours = total / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, total / 60 % 60, total % 60)
        } else {
            String.format(Locale.US, "%d:%02d", total / 60, total % 60)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            -> {
                if (video.isPlaying) video.pause() else video.start()
                showState()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(SEEK_MS)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_UP -> step(1)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_DOWN -> step(-1)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.onKeyDown(keyCode, event)
        }
        peek()
        return true
    }

    private fun seek(delta: Int) {
        val duration = video.duration
        if (duration <= 0) return
        video.seekTo((video.currentPosition + delta).coerceIn(0, duration - 1))
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    /** The picture goes away while the app is in the background, so the position is kept for the return. */
    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        if (!failed) resumeAt = video.currentPosition
        video.pause()
    }

    override fun onRestart() {
        super.onRestart()
        if (!failed) play()
    }

    override fun onDestroy() {
        super.onDestroy()
        video.stopPlayback()
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val SEEK_MS = 10_000
        private const val TICK_MS = 500L
        private const val PANEL_MS = 4_000L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        fun intent(context: Context, file: File): Intent = Intent(context, PlayerActivity::class.java).putExtra(EXTRA_FILE, file.path)
    }
}
