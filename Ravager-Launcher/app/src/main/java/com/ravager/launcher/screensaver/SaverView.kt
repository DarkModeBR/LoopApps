package com.ravager.launcher.screensaver

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ravager.launcher.home.ClockFormatter
import com.ravager.launcher.net.UrlConnectionHttpGet
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.theme.Themes
import com.ravager.launcher.ui.BaseActivity
import com.ravager.launcher.ui.FontWeight
import com.ravager.launcher.ui.face
import com.ravager.launcher.weather.WeatherRepository
import com.ravager.launcher.weather.WeatherText
import java.io.File
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/**
 * Pure black screen with a large clock, date and weather. Everything fades out, changes place
 * and fades back in every half minute, so nothing marks the TV (burn-in). In a [preview] it is
 * smaller and moves often, so the movement can be seen.
 */
class SaverView(context: Context, preview: Boolean = false) : FrameLayout(context) {
    private val unit = resources.displayMetrics.widthPixels / 100f * if (preview) SaverViews.PREVIEW_SCALE else 1f
    private val moveInterval = if (preview) PREVIEW_MOVE_INTERVAL_MS else MOVE_INTERVAL_MS
    private val handler = Handler(Looper.getMainLooper())
    private val theme = Themes.byId(AppSettings(context).themeId)
    private val time = text(8.5f, CLOCK_COLOR, FontWeight.LIGHT).apply { includeFontPadding = false }
    private val date = text(1.7f, DETAIL_COLOR, FontWeight.REGULAR).apply { letterSpacing = 0.06f }
    private val weather = text(1.7f, DETAIL_COLOR, FontWeight.REGULAR).apply { letterSpacing = 0.06f }
    private val block = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        alpha = 0f
        addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (unit * 1f).toInt() })
        addView(weather, LinearLayout.LayoutParams(WRAP, WRAP))
    }
    private var place = 0 to 0
    private var shown = false

    private val clockTick = object : Runnable {
        override fun run() {
            refreshTexts()
            handler.postDelayed(this, MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
        }
    }

    private val moveTick = object : Runnable {
        override fun run() {
            move()
            handler.postDelayed(this, moveInterval)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(block, LayoutParams(WRAP, WRAP))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(clockTick)
        shown = false
    }

    /** The first place depends on the real size, which only exists after the first layout. */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (shown || block.width == 0) return
        shown = true
        jumpToNewPlace()
        block.animate().alpha(1f).setDuration(FADE_MS).start()
        handler.postDelayed(moveTick, moveInterval)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(moveTick)
        block.animate().cancel()
    }

    private fun move() {
        block.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            jumpToNewPlace()
            block.animate().alpha(1f).setDuration(FADE_MS).start()
        }.start()
    }

    private fun jumpToNewPlace() {
        place = SaverPositioner.next(Random.Default, width, height, block.width, block.height, place)
        block.translationX = place.first.toFloat()
        block.translationY = place.second.toFloat()
    }

    private fun refreshTexts() {
        val now = Calendar.getInstance()
        val language = BaseActivity.currentLanguage(context)
        time.text = withSmallPeriod(
            ClockFormatter.time(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), DateFormat.is24HourFormat(context)),
        )
        date.text = ClockFormatter.date(now.timeInMillis, TimeZone.getDefault(), language)
        val settings = AppSettings(context)
        val current = if (settings.weatherEnabled) {
            WeatherRepository(UrlConnectionHttpGet(), File(context.filesDir, "weather.json")).cached()
        } else {
            null
        }
        weather.text = current?.let { WeatherText.display(context, it) }
        weather.visibility = if (current == null) View.GONE else View.VISIBLE
    }

    private fun withSmallPeriod(value: String): CharSequence {
        val space = value.indexOf(' ')
        if (space < 0) return value
        return SpannableString(value).apply {
            setSpan(RelativeSizeSpan(0.3f), space, value.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun text(sizePercent: Float, color: Int, weight: FontWeight) = TextView(context).apply {
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * sizePercent)
        typeface = theme.face(weight)
        maxLines = 1
    }

    private companion object {
        const val WRAP = LayoutParams.WRAP_CONTENT
        const val MINUTE_MS = 60_000L
        const val MOVE_INTERVAL_MS = 30_000L
        const val PREVIEW_MOVE_INTERVAL_MS = 4_000L
        const val FADE_MS = 700L

        // Soft grays burn the screen less than pure white, and still read well on black.
        const val CLOCK_COLOR = 0xFFC4C4C4.toInt()
        const val DETAIL_COLOR = 0xFF8E8E8E.toInt()
    }
}
