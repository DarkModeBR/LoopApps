package com.loop.launcher.ui.menu

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

/** A small picture for a row. It answers on the main thread, right away when it is already loaded. */
fun interface RowThumb {
    fun load(done: (Bitmap?) -> Unit)
}

/** One page of a side menu. Rows are rebuilt each time the page is shown, so values stay current. */
class MenuPage(
    val title: () -> String,
    val subtitle: () -> String? = { null },
    /** Shows the preview area beside the menu while this page is open. */
    val hasPreview: Boolean = false,
    /** Picture beside the title, for a page about one thing. */
    val icon: Drawable? = null,
    val rows: () -> List<MenuRow>,
) {
    constructor(
        title: String,
        subtitle: () -> String? = { null },
        hasPreview: Boolean = false,
        icon: Drawable? = null,
        rows: () -> List<MenuRow>,
    ) : this({ title }, subtitle, hasPreview, icon, rows)
}

sealed interface MenuRow {
    /** Name of a group of rows. */
    data class Section(val title: String) : MenuRow

    /** Opens another page. */
    data class Nav(
        val title: String,
        val icon: Int? = null,
        val value: String? = null,
        val detail: String? = null,
        val thumb: RowThumb? = null,
        val open: () -> Unit,
    ) : MenuRow

    /** Does something right away. */
    data class Action(
        val title: String,
        val icon: Int? = null,
        val value: String? = null,
        val danger: Boolean = false,
        val onFocus: (() -> Unit)? = null,
        val run: () -> Unit,
    ) : MenuRow

    data class Toggle(
        val title: String,
        val icon: Int? = null,
        val isOn: Boolean,
        val detail: String? = null,
        /** Second action of the row, on the right arrow or when holding OK. */
        val more: (() -> Unit)? = null,
        val set: (Boolean) -> Unit,
    ) : MenuRow

    /** One choice among several; [swatch] previews a theme with its colors. */
    data class Option(
        val title: String,
        val selected: Boolean,
        val swatch: List<Int> = emptyList(),
        val thumb: RowThumb? = null,
        /** Runs when the focus reaches this row, for showing a preview. */
        val onFocus: (() -> Unit)? = null,
        val pick: () -> Unit,
    ) : MenuRow

    /** Text to read. It can take focus so long pages can be scrolled with the remote. */
    data class Note(val text: String) : MenuRow

    /** A command to type, shown exactly as it must be written. */
    data class Code(val text: String) : MenuRow

    /** Text field. */
    data class Input(val hint: String, val initial: String, val onChange: (String) -> Unit, val submit: () -> Unit) : MenuRow

    /** A reading with an optional bar. */
    data class Meter(val title: String, val value: String, val fraction: Double? = null) : MenuRow
}
