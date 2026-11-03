package com.loop.explorer.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.loop.explorer.theme.ThemeDefinition

/** Rounded text button on the page, like the material buttons of AnExplorer. */
class Chip(context: Context, private val theme: ThemeDefinition, unit: Float) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val label = TextView(context)
    private val shape = GradientDrawable().apply { cornerRadius = unit * theme.radius }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = shape
        val padH = (unit * 1.1f).toInt()
        val padV = (unit * 0.6f).toInt()
        setPadding(padH, padV, padH, padV)
        val iconSize = (unit * 1.4f).toInt()
        addView(icon, LayoutParams(iconSize, iconSize))
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.1f)
        label.typeface = theme.face(FontWeight.MEDIUM)
        label.maxLines = 1
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = (unit * 0.6f).toInt() })
        paint(false)
    }

    fun set(iconRes: Int, text: CharSequence?): Chip {
        icon.setImageResource(iconRes)
        label.text = text
        label.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        return this
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        paint(gainFocus)
    }

    private fun paint(focused: Boolean) {
        shape.setColor(if (focused) theme.accent else theme.raised)
        val content = if (focused) theme.onAccent else theme.text
        label.setTextColor(content)
        icon.setColorFilter(content)
    }
}

/** Button of the top bar, with its icon and its name always visible. */
class ToolButton(context: Context, private val theme: ThemeDefinition, unit: Float) : LinearLayout(context) {
    private val icon = ImageView(context)
    private val label = TextView(context)
    private val shape = GradientDrawable().apply { cornerRadius = unit * theme.radius }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = shape
        val pad = (unit * 0.75f).toInt()
        setPadding(pad, pad, pad, pad)
        val iconSize = (unit * 1.7f).toInt()
        addView(icon, LayoutParams(iconSize, iconSize))
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.1f)
        label.typeface = theme.face(FontWeight.MEDIUM)
        label.maxLines = 1
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = (unit * 0.6f).toInt() })
        paint(false)
    }

    fun set(iconRes: Int, text: CharSequence): ToolButton {
        icon.setImageResource(iconRes)
        label.text = text
        contentDescription = text
        return this
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        paint(gainFocus)
    }

    private fun paint(focused: Boolean) {
        // On the bar the focus is the inverse: a pill in the text color with the content in the bar color.
        shape.setColor(if (focused) theme.onBar else 0)
        val content = if (focused) theme.bar else theme.onBar
        icon.setColorFilter(content)
        label.setTextColor(content)
    }
}

/** The round floating button in the corner, AnExplorer's way to create something new. */
class Fab(context: Context, private val theme: ThemeDefinition, unit: Float) : FrameLayout(context) {
    private val icon = ImageView(context)
    private val shape = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(theme.accent)
    }
    private val ring = (unit * 0.3f).toInt()

    init {
        isFocusable = true
        isClickable = true
        background = shape
        elevation = unit * 0.6f
        val size = (unit * 2.2f).toInt()
        addView(icon, LayoutParams(size, size, Gravity.CENTER))
        icon.setColorFilter(theme.onAccent)
        paint(false)
    }

    fun set(iconRes: Int, text: CharSequence): Fab {
        icon.setImageResource(iconRes)
        contentDescription = text
        return this
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        paint(gainFocus)
    }

    private fun paint(focused: Boolean) {
        shape.setStroke(ring, if (focused) theme.text else theme.accent)
        val scale = if (focused) 1.12f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(110).start()
    }
}
