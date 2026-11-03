package com.loop.launcher.home

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.loop.launcher.theme.ThemeDefinition
import com.loop.launcher.ui.face

/** Button of the top bar: optional icon, text and count, shaped by the theme. */
class ChipView(context: Context, metrics: HomeMetrics, private val theme: ThemeDefinition) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val label = TextView(context)
    private val badge = TextView(context)
    private val iconGap = metrics.cw(0.6f)
    private val square = theme.radius == 0f
    private val shape = GradientDrawable().apply { cornerRadius = if (square) 0f else metrics.cwf(5f) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = shape
        setPadding(metrics.cw(1.1f), metrics.cw(0.65f), metrics.cw(1.1f), metrics.cw(0.65f))

        val iconSize = metrics.cw(1.5f)
        addView(icon, LayoutParams(iconSize, iconSize).apply { marginEnd = iconGap })
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(1.15f))
        label.typeface = theme.face()
        label.maxLines = 1
        addView(label)
        badge.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(0.95f))
        badge.typeface = theme.face()
        badge.setPadding(metrics.cw(0.45f), 0, metrics.cw(0.45f), 0)
        badge.background = GradientDrawable().apply {
            setColor(theme.accent)
            cornerRadius = if (square) 0f else metrics.cwf(5f)
        }
        badge.setTextColor(theme.onAccent)
        addView(badge, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        badge.visibility = View.GONE
        paint(false)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        paint(gainFocus)
    }

    fun setIcon(resId: Int) {
        icon.setImageResource(resId)
    }

    fun setLabel(text: CharSequence?) {
        label.text = text
        label.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        syncIconMargin()
    }

    fun setBadge(count: Int) {
        badge.text = count.toString()
        badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        syncIconMargin()
    }

    /** The gap after the icon only exists when something follows it. */
    private fun syncIconMargin() {
        val hasContent = label.visibility == View.VISIBLE || badge.visibility == View.VISIBLE
        (icon.layoutParams as LayoutParams).marginEnd = if (hasContent) iconGap else 0
        icon.requestLayout()
    }

    private fun paint(focused: Boolean) {
        val content = if (focused) theme.onAccent else theme.text
        shape.setColor(if (focused) theme.accent else CHIP_FILL)
        shape.setStroke(STROKE, if (focused) theme.accent else if (square) theme.accent else CHIP_STROKE)
        label.setTextColor(content)
        icon.setColorFilter(content)
        badge.setTextColor(if (focused) theme.accent else theme.onAccent)
        (badge.background as GradientDrawable).setColor(if (focused) theme.onAccent else theme.accent)
    }

    private companion object {
        const val CHIP_FILL = 0x1FFFFFFF
        const val CHIP_STROKE = 0x47FFFFFF
        const val STROKE = 2
    }
}
