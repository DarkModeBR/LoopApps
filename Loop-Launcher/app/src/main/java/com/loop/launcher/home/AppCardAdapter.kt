package com.loop.launcher.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.loop.launcher.apps.AppEntry
import com.loop.launcher.theme.ThemeDefinition
import com.loop.launcher.ui.face

interface CardListener {
    fun onCardFocused(trayIndex: Int, column: Int, entry: AppEntry, accent: Int?)
    fun onCardClicked(entry: AppEntry)
    fun onCardMenu(entry: AppEntry)

    /** True for the card being moved by hand, which stands out from the others. */
    fun isMoving(entry: AppEntry): Boolean = false
}

class AppCardAdapter(
    private val metrics: HomeMetrics,
    private val art: CardArtLoader,
    private val listener: CardListener,
    private val theme: ThemeDefinition,
) : RecyclerView.Adapter<AppCardAdapter.CardHolder>() {
    var trayIndex = 0
    private var entries: List<AppEntry> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submit(index: Int, apps: List<AppEntry>) {
        trayIndex = index
        entries = apps
        notifyDataSetChanged()
    }

    override fun getItemCount() = entries.size
    override fun getItemId(position: Int) = entries[position].packageName.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardHolder {
        val radius = metrics.cwf(theme.cardRadius)
        val image = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val icon = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val label = TextView(parent.context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(1.1f))
            typeface = theme.face()
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(metrics.cw(0.4f), 0, metrics.cw(0.4f), 0)
        }
        val iconSize = metrics.cardHeight / 2
        val card = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(metrics.cardWidth, metrics.cardHeight)
            isFocusable = true
            isClickable = true
            clipToOutline = radius > 0f
            background = GradientDrawable().apply { cornerRadius = radius }
            addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                icon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER).apply { bottomMargin = metrics.cw(1f) },
            )
            addView(
                label,
                FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = metrics.cw(0.6f) },
            )
            foreground = GradientDrawable().apply {
                cornerRadius = radius
                setStroke(metrics.cw(theme.focusStroke).coerceAtLeast(2), theme.focusColor)
                setColor(Color.TRANSPARENT)
            }
            foreground.alpha = 0
        }
        return CardHolder(card, image, icon, label)
    }

    override fun onBindViewHolder(holder: CardHolder, position: Int) =
        holder.bind(entries[position], trayIndex, position)

    inner class CardHolder(
        private val card: FrameLayout,
        private val image: ImageView,
        private val icon: ImageView,
        private val label: TextView,
    ) : RecyclerView.ViewHolder(card) {
        private var bound: AppEntry? = null
        private var boundTray = 0
        private var boundColumn = 0

        init {
            card.setOnFocusChangeListener { _, focused ->
                val scale = scaleFor(focused)
                card.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIM_MS).start()
                card.foreground.alpha = if (focused) 255 else 0
                if (focused) reportFocus()
            }
            card.setOnClickListener { bound?.let(listener::onCardClicked) }
            card.setOnLongClickListener {
                bound?.let(listener::onCardMenu)
                bound != null
            }
            card.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
                    bound?.let(listener::onCardMenu)
                    true
                } else {
                    false
                }
            }
        }

        /** All trays share a pool of cards, so the position comes from the tray doing the binding. */
        fun bind(entry: AppEntry, tray: Int, column: Int) {
            bound = entry
            boundTray = tray
            boundColumn = column
            val scale = scaleFor(card.isFocused)
            card.scaleX = scale
            card.scaleY = scale
            label.text = entry.label
            label.visibility = View.GONE
            image.setImageDrawable(null)
            icon.setImageDrawable(null)
            paintBackground(AccentColor.NEUTRAL)
            art.load(entry) { loaded -> if (bound == entry) show(entry, loaded) }
        }

        private fun scaleFor(focused: Boolean): Float = when {
            !focused -> 1f
            bound?.let(listener::isMoving) == true -> MOVING_SCALE
            else -> theme.focusScale
        }

        private fun show(entry: AppEntry, loaded: CardArt) {
            paintBackground(loaded.accent)
            if (loaded.banner != null) {
                image.setImageDrawable(loaded.banner)
                icon.setImageDrawable(null)
                label.visibility = View.GONE
            } else {
                image.setImageDrawable(null)
                icon.setImageDrawable(loaded.icon)
                label.text = entry.label
                label.visibility = View.VISIBLE
            }
            if (card.isFocused) reportFocus()
        }

        private fun paintBackground(color: Int) {
            (card.background as GradientDrawable).setColor(color)
        }

        private fun reportFocus() {
            val entry = bound ?: return
            listener.onCardFocused(boundTray, boundColumn, entry, art.peek(entry.packageName)?.accent)
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val FOCUS_ANIM_MS = 110L
        const val MOVING_SCALE = 1.14f
    }
}
