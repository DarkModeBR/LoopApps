package com.ravager.launcher.trays

import com.ravager.launcher.apps.AppEntry

object TrayLoader {
    /** Reads the saved layout, rebuilding and saving it when the file is missing or unreadable. */
    fun load(store: TrayStore, apps: List<AppEntry>): TrayLayout {
        val saved = store.load()
        val next = TrayReconcile.apply(saved ?: TrayBootstrap.build(apps), apps)
        if (next != saved) store.save(next)
        return next
    }
}
