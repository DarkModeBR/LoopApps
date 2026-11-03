package com.loop.launcher.home

import com.loop.launcher.R

/** How large the app cards are; the layout says how many fit at the medium size. */
enum class CardSize(val nameRes: Int, private val extraCards: Int) {
    SMALL(R.string.card_size_small, 1),
    MEDIUM(R.string.card_size_medium, 0),
    LARGE(R.string.card_size_large, -1);

    fun cardsPerRow(medium: Int) = medium + extraCards
}

object RowFit {
    /** How many rows of trays fit, so large cards never push the bottom row off the screen. */
    fun rows(available: Int, rowHeight: Int, max: Int): Int = (available / rowHeight).coerceIn(1, max)
}
