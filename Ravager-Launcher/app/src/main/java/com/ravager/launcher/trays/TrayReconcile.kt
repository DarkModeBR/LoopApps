package com.ravager.launcher.trays

import com.ravager.launcher.apps.AppEntry
import com.ravager.launcher.apps.CategoryResolver

object TrayReconcile {
    /** Drops uninstalled apps and places new ones; anything the user already placed stays put. */
    fun apply(layout: TrayLayout, apps: List<AppEntry>): TrayLayout {
        val installed = apps.map { it.packageName }.toSet()
        var result = TrayLayout(
            trays = layout.trays.map { tray -> tray.copy(packages = tray.packages.filter { it in installed }) },
            hidden = layout.hidden.filter { it in installed }.toSet(),
        )
        val placed = result.trays.flatMap { it.packages }.toSet() + result.hidden
        for (app in apps.filter { it.packageName !in placed }) {
            val category = CategoryResolver.resolve(app.packageName, app.declaredCategory)
            val target = result.trays.firstOrNull { it.category == category }
            result = if (target != null) {
                TrayLayouts.moveApp(result, app.packageName, target.id)
            } else {
                TrayLayouts.appendToOther(result, listOf(app.packageName))
            }
        }
        return result
    }
}
