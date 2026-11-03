package com.ravager.launcher.trays

import com.ravager.launcher.R
import com.ravager.launcher.apps.AppEntry
import com.ravager.launcher.apps.AppSource
import com.ravager.launcher.ui.menu.MenuPage
import com.ravager.launcher.ui.menu.MenuRow
import com.ravager.launcher.ui.menu.SideMenuActivity
import java.io.File

/** One row per category; choosing one puts [pkg] there, bringing it back if it was hidden. */
fun categoryRows(menu: SideMenuActivity, store: TrayStore, pkg: String): List<MenuRow> {
    val current = store.load() ?: return emptyList()
    return current.trays.map { tray ->
        MenuRow.Option(TrayNames.label(menu, tray), pkg in tray.packages) {
            store.save(TrayLayouts.unhideApp(current, pkg, tray.id))
            menu.goBack()
        }
    }
}

/** Pages for creating, renaming, ordering and deleting trays. */
class TrayMenus(private val menu: SideMenuActivity) {
    private val store = TrayStore(File(menu.filesDir, "trays.json"))
    private val source = AppSource(menu)

    private fun layout() = store.load() ?: TrayLayout(emptyList(), emptySet())

    private fun change(transform: (TrayLayout) -> TrayLayout) = store.save(transform(layout()))

    private fun text(id: Int) = menu.getString(id)

    fun rootPage() = MenuPage(text(R.string.settings_organize_trays), { text(R.string.trays_hint) }) {
        layout().trays.map { tray ->
            MenuRow.Nav(
                TrayNames.label(menu, tray),
                value = menu.getString(R.string.tray_app_count, tray.packages.size),
            ) { menu.open(trayPage(tray.id)) }
        } + MenuRow.Action(text(R.string.tray_create), R.drawable.ic_plus) {
            menu.open(namePage(text(R.string.tray_create), "") { name -> change { TrayLayouts.createTray(it, name) } })
        }
    }

    private fun trayPage(id: String): MenuPage {
        fun tray() = layout().trays.firstOrNull { it.id == id }
        return MenuPage({ tray()?.let { TrayNames.label(menu, it) }.orEmpty() }) {
            val current = tray() ?: return@MenuPage emptyList()
            listOf(
                MenuRow.Nav(text(R.string.tray_apps), R.drawable.ic_apps) { menu.open(appsPage(id)) },
                MenuRow.Action(text(R.string.tray_rename), R.drawable.ic_edit) {
                    menu.open(
                        namePage(text(R.string.tray_rename), TrayNames.label(menu, current)) { name ->
                            change { TrayLayouts.renameTray(it, id, name) }
                        },
                    )
                },
                MenuRow.Action(text(R.string.tray_up), R.drawable.ic_arrow_up) {
                    change { TrayLayouts.moveTray(it, id, -1) }
                    menu.refresh()
                },
                MenuRow.Action(text(R.string.tray_down), R.drawable.ic_arrow_down) {
                    change { TrayLayouts.moveTray(it, id, 1) }
                    menu.refresh()
                },
                MenuRow.Action(text(R.string.tray_delete), R.drawable.ic_trash, danger = true) {
                    menu.open(deletePage(id, TrayNames.label(menu, current)))
                },
            )
        }
    }

    private fun isOther(id: String) = layout().trays.firstOrNull { it.id == id }?.category == AppCategory.OTHER

    /** Ticks the apps of a category, so an empty new one can be filled from here. */
    private fun appsPage(id: String) = MenuPage(
        text(R.string.tray_apps),
        { text(if (isOther(id)) R.string.tray_apps_hint_other else R.string.tray_apps_hint) },
    ) {
        val current = layout()
        val tray = current.trays.firstOrNull { it.id == id } ?: return@MenuPage emptyList()
        source.loadApps().map { app ->
            val here = app.packageName in tray.packages
            MenuRow.Toggle(
                app.label,
                isOn = here,
                detail = if (here) null else whereIs(current, app),
                more = { menu.open(categoryPage(app)) },
            ) { on ->
                // Every app lives somewhere, and Outros is where the others send theirs.
                if (!on && tray.category == AppCategory.OTHER) {
                    menu.open(categoryPage(app))
                } else {
                    change {
                        if (on) TrayLayouts.unhideApp(it, app.packageName, id) else TrayLayouts.moveToOther(it, app.packageName)
                    }
                    menu.refresh()
                }
            }
        }
    }

    private fun whereIs(layout: TrayLayout, app: AppEntry): String? = if (app.packageName in layout.hidden) {
        text(R.string.app_hidden_mark)
    } else {
        layout.trays.firstOrNull { app.packageName in it.packages }?.let { TrayNames.label(menu, it) }
    }

    private fun categoryPage(app: AppEntry) = MenuPage(app.label, { text(R.string.tray_change_category) }) {
        categoryRows(menu, store, app.packageName)
    }

    private fun deletePage(id: String, name: String) = MenuPage(name) {
        listOf(
            MenuRow.Note(text(R.string.tray_delete_note)),
            MenuRow.Action(text(R.string.tray_delete), R.drawable.ic_trash, danger = true) {
                change { TrayLayouts.deleteTray(it, id) }
                menu.goBack()
                menu.goBack()
            },
            MenuRow.Action(text(R.string.dialog_cancel), R.drawable.ic_close) { menu.goBack() },
        )
    }

    private fun namePage(title: String, initial: String, save: (String) -> Unit): MenuPage {
        var typed = initial
        fun submit() {
            val name = typed.trim()
            if (name.isEmpty()) return
            save(name)
            menu.goBack()
        }
        return MenuPage(title) {
            listOf(
                MenuRow.Input(text(R.string.tray_name_hint), typed, { typed = it }, ::submit),
                MenuRow.Action(text(R.string.action_save), R.drawable.ic_check, run = ::submit),
            )
        }
    }
}
