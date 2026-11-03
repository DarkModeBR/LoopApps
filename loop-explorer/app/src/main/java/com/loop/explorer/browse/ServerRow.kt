package com.loop.explorer.browse

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.loop.explorer.R
import com.loop.explorer.server.ServerService
import com.loop.explorer.theme.ThemeDefinition
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.face

/** The HTTP server, first in the side panel, showing whether it is on and at which address. */
class ServerRow(context: Context, private val theme: ThemeDefinition, unit: Float) : LinearLayout(context) {
    private val shape = GradientDrawable().apply { cornerRadius = unit * theme.radius }
    private val icon = ImageView(context).apply { setImageResource(R.drawable.ic_server) }
    private val dot = android.view.View(context)
    private val label = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.2f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        text = context.getString(R.string.tool_server)
    }
    private val detail = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.92f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        typeface = theme.face()
    }
    private var focused = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        background = shape
        val px = { percent: Float -> (unit * percent).toInt() }
        setPadding(px(1.1f), px(0.8f), px(1.2f), px(0.8f))
        val iconBox = android.widget.FrameLayout(context)
        iconBox.addView(icon, android.widget.FrameLayout.LayoutParams(px(1.7f), px(1.7f)))
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ON_COLOR)
        }
        iconBox.addView(dot, android.widget.FrameLayout.LayoutParams(px(0.6f), px(0.6f), Gravity.TOP or Gravity.END))
        addView(iconBox, LayoutParams(px(1.7f), px(1.7f)).apply { marginEnd = px(1.3f) })
        val texts = LinearLayout(context).apply { orientation = VERTICAL }
        texts.addView(label)
        texts.addView(detail)
        addView(texts, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        update()
    }

    /** Reads the server state again; called when it starts or stops. */
    fun update() {
        val address = ServerService.urls.firstOrNull()?.removePrefix("http://")
        val on = ServerService.running
        detail.text = if (on && address != null) {
            context.getString(R.string.server_row_on, address)
        } else if (on) {
            context.getString(R.string.server_row_on, "")
        } else {
            context.getString(R.string.server_row_off)
        }
        dot.visibility = if (on) VISIBLE else GONE
        paint()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        focused = gainFocus
        paint()
    }

    private fun paint() {
        shape.setColor(if (focused) theme.accent else Color.TRANSPARENT)
        val content = if (focused) theme.onAccent else theme.text
        label.setTextColor(content)
        label.typeface = theme.face(if (focused) FontWeight.MEDIUM else FontWeight.REGULAR)
        detail.setTextColor(if (focused) theme.onAccent else if (ServerService.running) onText else theme.muted)
        icon.setColorFilter(if (focused) theme.onAccent else theme.muted)
    }

    private val onText get() = if (theme.dark) 0xFF69F0AE.toInt() else 0xFF2E7D32.toInt()

    private companion object {
        const val ON_COLOR = 0xFF00C853.toInt()
    }
}
