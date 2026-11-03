package com.loop.launcher.ui.menu

/** Where the user is inside a side menu, and which row to focus when coming back. */
class MenuStack<T>(root: T) {
    private class Entry<T>(val page: T, var row: Int = 0)

    private val entries = mutableListOf(Entry(root))

    val current: T get() = entries.last().page
    val currentRow: Int get() = entries.last().row

    fun open(page: T) {
        entries += Entry(page)
    }

    fun replace(page: T) {
        entries[entries.lastIndex] = Entry(page)
    }

    /** False when already on the first page, which means the menu should close. */
    fun back(): Boolean {
        if (entries.size == 1) return false
        entries.removeAt(entries.lastIndex)
        return true
    }

    fun rememberRow(row: Int) {
        entries.last().row = row
    }
}
