package com.ravager.launcher.home

import android.content.Context
import com.ravager.launcher.settings.LayoutMode

object CardFit {
    /** Width that makes exactly [cards] whole cards fill the tray, so none is cut at rest. */
    fun width(innerWidth: Int, gap: Int, cards: Int): Int = (innerWidth - gap * (cards - 1)) / cards
}

/** All sizes are fractions of the screen width, so every TV resolution gets the same layout. */
class HomeMetrics(
    context: Context,
    val mode: LayoutMode,
    size: CardSize = CardSize.MEDIUM,
    showTrayNames: Boolean = true,
) {
    private val unit = context.resources.displayMetrics.widthPixels / 100f

    fun cw(percent: Float): Int = (unit * percent).toInt()
    fun cwf(percent: Float): Float = unit * percent

    private val highlight = mode == LayoutMode.HIGHLIGHT

    val cardGap = cw(1.1f)

    /** Leaves room for a focused card to grow without being cut. */
    val trayPadding = cw(1.1f)
    val sidePadding = cw(4f)
    val cardWidth = CardFit.width(
        context.resources.displayMetrics.widthPixels - (sidePadding + trayPadding) * 2,
        cardGap,
        size.cardsPerRow(if (highlight) 5 else 6),
    )
    val cardHeight = cardWidth * 9 / 16
    val labelHeight = if (showTrayNames) cw(2f) else 0
    val rowGap = cw(if (highlight) 0.7f else 0.5f)
    val rowHeight = labelHeight + trayPadding * 2 + cardHeight + rowGap
    val topPadding = cw(3f)

    /** Large cards need the space of a big clock to keep two rows on screen. */
    private val clockPercent = when {
        !highlight -> 4f
        size == CardSize.LARGE -> 6.5f
        else -> 8.5f
    }
    val clockSize = cwf(clockPercent)
    val dateSize = cwf(if (highlight) 1.6f else 1.25f)

    /** Rows that fit below the clock and the name of the focused app. */
    val visibleRows = RowFit.rows(
        context.resources.displayMetrics.heightPixels - topPadding -
            cw(if (highlight) clockPercent * 1.18f + HIGHLIGHT_DETAILS else COMPACT_HEADER) - cw(APP_NAME_AREA),
        rowHeight,
        if (highlight) 2 else 3,
    )

    private companion object {
        const val HIGHLIGHT_DETAILS = 5.3f
        const val COMPACT_HEADER = 5.5f
        const val APP_NAME_AREA = 4.8f
    }
}
