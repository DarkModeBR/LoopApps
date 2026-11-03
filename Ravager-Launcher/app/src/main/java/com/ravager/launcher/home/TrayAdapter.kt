package com.ravager.launcher.home

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ravager.launcher.apps.AppEntry
import com.ravager.launcher.theme.ThemeDefinition
import com.ravager.launcher.theme.TrayStyle
import com.ravager.launcher.ui.face

data class TrayRow(val trayId: String, val label: String, val apps: List<AppEntry>)

class TrayAdapter(
    private val metrics: HomeMetrics,
    private val art: CardArtLoader,
    private val listener: CardListener,
    private val theme: ThemeDefinition,
    /** The color behind the name of a window-style tray, so its border looks cut. */
    private val legendBackground: Int,
) : RecyclerView.Adapter<TrayAdapter.RowHolder>() {
    var rows: List<TrayRow> = emptyList()
        private set

    private val pool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 24) }

    fun submit(newRows: List<TrayRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val context = parent.context
        val window = theme.trayStyle == TrayStyle.WINDOW
        val label = TextView(context).apply {
            setTextColor(if (window) theme.accent else theme.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(1.1f))
            typeface = theme.face()
            maxLines = 1
            if (window) {
                setBackgroundColor(legendBackground)
                setPadding(metrics.cw(0.5f), 0, metrics.cw(0.5f), 0)
            } else {
                setPadding(metrics.cw(0.3f), 0, 0, 0)
            }
        }
        if (metrics.labelHeight == 0) label.visibility = View.GONE
        val adapter = AppCardAdapter(metrics, art, listener, theme)
        val list = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false).apply {
                initialPrefetchItemCount = 6
            }
            this.adapter = adapter
            setHasFixedSize(true)
            setRecycledViewPool(pool)
            itemAnimator = null
            clipToPadding = false
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setPadding(metrics.trayPadding, metrics.trayPadding, metrics.trayPadding, metrics.trayPadding)
            background = trayBackground()
            // Keeps cards inside the rounded tray while they scroll past its edges.
            clipToOutline = theme.trayStyle == TrayStyle.PANEL
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    if (parent.getChildAdapterPosition(view) > 0) out.left = metrics.cardGap
                }
            })
        }
        val listHeight = metrics.cardHeight + metrics.trayPadding * 2
        val row = FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, metrics.rowHeight)
            addView(
                list,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight).apply {
                    topMargin = if (window) metrics.labelHeight / 2 else metrics.labelHeight
                },
            )
            addView(
                label,
                FrameLayout.LayoutParams(
                    if (window) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                    metrics.labelHeight,
                ).apply { if (window) marginStart = metrics.cw(1.2f) },
            )
        }
        return RowHolder(row, label, list, adapter)
    }

    private fun trayBackground(): GradientDrawable? = when (theme.trayStyle) {
        TrayStyle.NONE -> null
        TrayStyle.PANEL -> GradientDrawable().apply {
            setColor(0x12FFFFFF)
            cornerRadius = metrics.cwf(maxOf(theme.cardRadius * 1.4f, 0f))
        }
        TrayStyle.WINDOW -> GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(2, theme.raised)
        }
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) = holder.bind(rows[position], position)

    class RowHolder(
        view: View,
        private val label: TextView,
        private val list: RecyclerView,
        private val adapter: AppCardAdapter,
    ) : RecyclerView.ViewHolder(view) {
        private var shownTray: String? = null

        fun bind(row: TrayRow, index: Int) {
            label.text = row.label
            // This holder may have been scrolled sideways while it showed another tray.
            if (shownTray != row.trayId) list.scrollToPosition(0)
            shownTray = row.trayId
            adapter.submit(index, row.apps)
        }

        /** Focuses a card, scrolling to it first when it is outside the screen. */
        fun focusColumn(column: Int, attempts: Int = 3) {
            val card = list.findViewHolderForAdapterPosition(column)?.itemView
            if (card != null) {
                // Android starts in touch mode until the first key, and that mode ignores a plain request.
                card.requestFocusFromTouch()
            } else if (attempts > 0) {
                list.scrollToPosition(column)
                list.post { focusColumn(column, attempts - 1) }
            }
        }
    }
}
