package com.ravager.launcher.trays

data class FocusTarget(val trayId: String, val column: Int)

object FocusResolver {
    /** [lastTrayIndex] counts visible trays. Returns null only when no card exists to focus. */
    fun next(layout: TrayLayout, lastPackage: String?, lastTrayIndex: Int, lastColumn: Int): FocusTarget? {
        val visible = TrayLayouts.visibleTrays(layout)
        if (visible.isEmpty()) return null
        visible.forEach { tray ->
            val column = tray.packages.indexOf(lastPackage)
            if (column >= 0) return FocusTarget(tray.id, column)
        }
        val tray = visible[lastTrayIndex.coerceIn(0, visible.lastIndex)]
        return FocusTarget(tray.id, lastColumn.coerceIn(0, tray.packages.lastIndex))
    }
}
