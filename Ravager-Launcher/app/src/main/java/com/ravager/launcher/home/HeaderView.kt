package com.ravager.launcher.home

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.ravager.launcher.settings.LayoutMode
import com.ravager.launcher.theme.ThemeDefinition
import com.ravager.launcher.ui.FontWeight
import com.ravager.launcher.ui.face

/** Top of the home screen: clock, date and weather on the left, buttons on the right. */
class HeaderView(
    context: Context,
    private val metrics: HomeMetrics,
    private val theme: ThemeDefinition,
    showClock: Boolean = true,
    showDate: Boolean = true,
    showWeather: Boolean = true,
) : FrameLayout(context) {
    private val clock = text(metrics.clockSize).apply {
        typeface = theme.face(FontWeight.LIGHT)
        includeFontPadding = false
    }
    private val date = text(metrics.dateSize).apply { letterSpacing = if (theme.mono) 0f else 0.06f }
    private val weather = text(metrics.dateSize).apply {
        letterSpacing = if (theme.mono) 0f else 0.06f
        visibility = View.GONE
    }

    /** Buttons of the top bar, in display order. */
    val chips = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }

    init {
        if (!showClock) clock.visibility = View.GONE
        if (!showDate) date.visibility = View.GONE
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val details = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        details.addView(date, LinearLayout.LayoutParams(WRAP, WRAP))
        details.addView(weather, LinearLayout.LayoutParams(WRAP, WRAP))
        if (metrics.mode == LayoutMode.HIGHLIGHT) {
            left.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
            left.addView(details, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = metrics.cw(1f) })
        } else {
            val line = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP))
            val separator = View(context).apply {
                setBackgroundColor(theme.muted)
                if (!showClock || !(showDate || showWeather)) visibility = View.GONE
            }
            line.addView(
                separator,
                LinearLayout.LayoutParams(1, metrics.cw(3f)).apply {
                    marginStart = metrics.cw(1.5f)
                    marginEnd = metrics.cw(1.5f)
                },
            )
            line.addView(details, LinearLayout.LayoutParams(WRAP, WRAP))
            left.addView(line, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        addView(left, LayoutParams(WRAP, WRAP))
        addView(chips, LayoutParams(WRAP, WRAP, Gravity.END or Gravity.TOP))
    }

    fun setClock(time: String, dateText: String) {
        clock.text = withSmallPeriod(time)
        date.text = dateText
    }

    /** Shows AM or PM smaller than the digits. */
    private fun withSmallPeriod(time: String): CharSequence {
        val space = time.indexOf(' ')
        if (space < 0) return time
        return SpannableString(time).apply {
            setSpan(RelativeSizeSpan(0.3f), space, time.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Null hides the weather line. */
    fun setWeather(text: String?) {
        weather.text = text
        weather.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    fun addChip(chip: ChipView, index: Int = chips.childCount) {
        val params = LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = metrics.cw(0.8f) }
        chips.addView(chip, index, params)
    }

    fun visibleChips(): List<View> =
        (0 until chips.childCount).map { chips.getChildAt(it) }.filter { it.visibility == View.VISIBLE }

    private fun text(size: Float) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        setTextColor(theme.text)
        typeface = theme.face()
        maxLines = 1
        setShadowLayer(metrics.cwf(0.4f), 0f, metrics.cwf(0.08f), TEXT_SHADOW)
    }

    private companion object {
        const val WRAP = LayoutParams.WRAP_CONTENT
        const val TEXT_SHADOW = 0x99000000.toInt()
    }
}
