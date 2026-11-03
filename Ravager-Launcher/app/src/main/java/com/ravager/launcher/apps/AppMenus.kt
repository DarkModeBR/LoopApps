package com.ravager.launcher.apps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.ravager.launcher.R
import com.ravager.launcher.home.HomeActivity
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.settings.SettingsMenuActivity
import com.ravager.launcher.trays.TrayLayout
import com.ravager.launcher.trays.TrayLayouts
import com.ravager.launcher.trays.TrayNames
import com.ravager.launcher.trays.TrayReconcile
import com.ravager.launcher.trays.TrayStore
import com.ravager.launcher.trays.categoryRows
import com.ravager.launcher.ui.menu.MenuPage
import com.ravager.launcher.ui.menu.MenuRow
import com.ravager.launcher.ui.menu.SideMenuActivity
import com.ravager.launcher.ui.openOrWarn
import java.io.File

/** Pages about one app, and the list of all apps. */
class AppMenus(private val menu: SideMenuActivity) {
    private val store = TrayStore(File(menu.filesDir, "trays.json"))
    private val source = AppSource(menu)
    private val settings = AppSettings(menu)

    private fun layout() = store.load() ?: TrayLayout(emptyList(), emptySet())
    private fun text(id: Int) = menu.getString(id)

    fun allAppsPage() = MenuPage(text(R.string.settings_all_apps)) {
        val hidden = layout().hidden
        source.loadApps().map { app ->
            MenuRow.Nav(app.label, value = if (app.packageName in hidden) text(R.string.app_hidden_mark) else null) {
                menu.open(appPage(app) { menu.goBack() })
            }
        }
    }

    /** [done] runs after a change that makes this page pointless to keep open. */
    fun appPage(app: AppEntry, done: () -> Unit) = MenuPage(app.label, { summary(app) }, icon = source.icon(app)) {
        val isHidden = app.packageName in layout().hidden
        listOf(
            MenuRow.Action(text(R.string.menu_open), R.drawable.ic_open) {
                if (!source.launch(app)) {
                    Toast.makeText(menu, R.string.app_could_not_open, Toast.LENGTH_SHORT).show()
                }
                menu.closeMenu()
            },
            MenuRow.Nav(text(R.string.menu_move), R.drawable.ic_move) { menu.open(movePage(app)) },
            MenuRow.Nav(text(R.string.menu_rename), R.drawable.ic_edit) { menu.open(renamePage(app, done)) },
            if (isHidden) {
                MenuRow.Action(text(R.string.menu_show), R.drawable.ic_eye) {
                    val current = layout()
                    store.save(TrayReconcile.apply(current.copy(hidden = current.hidden - app.packageName), source.loadApps()))
                    done()
                }
            } else {
                MenuRow.Action(text(R.string.menu_hide), R.drawable.ic_eye_off) {
                    store.save(TrayLayouts.hideApp(layout(), app.packageName))
                    done()
                }
            },
            MenuRow.Action(text(R.string.menu_info), R.drawable.ic_info) {
                menu.openOrWarn(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")),
                )
            },
            MenuRow.Action(text(R.string.menu_uninstall), R.drawable.ic_trash, danger = true) {
                menu.openOrWarn(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
            },
            // Keeps the settings reachable even when their button is hidden from the top bar.
            MenuRow.Action(text(R.string.menu_launcher_settings), R.drawable.ic_settings) {
                menu.startActivity(Intent(menu, SettingsMenuActivity::class.java))
                menu.closeMenu()
            },
        )
    }

    private fun summary(app: AppEntry): String? = listOfNotNull(
        source.version(app)?.let { menu.getString(R.string.app_version, it) },
        currentTrayName(app),
    ).joinToString(" · ").ifEmpty { null }

    private fun currentTrayName(app: AppEntry): String? =
        layout().trays.firstOrNull { app.packageName in it.packages }?.let { TrayNames.label(menu, it) }

    private fun movePage(app: AppEntry) = MenuPage(text(R.string.menu_move), { app.label }) {
        val placed = layout().trays.any { app.packageName in it.packages }
        val free = if (placed) {
            listOf(
                MenuRow.Action(text(R.string.move_free), R.drawable.ic_move) {
                    menu.startActivity(HomeActivity.moveIntent(menu, app.packageName))
                },
            )
        } else {
            emptyList()
        }
        free + MenuRow.Section(text(R.string.move_to_category)) + categoryRows(menu, store, app.packageName)
    }

    private fun renamePage(app: AppEntry, done: () -> Unit): MenuPage {
        var typed = app.label
        fun save(name: String) {
            settings.appNames = CustomNames.with(settings.appNames, app.packageName, name)
            menu.goBack()
            done()
        }
        return MenuPage(text(R.string.menu_rename), { text(R.string.rename_hint) }) {
            buildList {
                add(MenuRow.Input(text(R.string.rename_field), typed, { typed = it }) { save(typed) })
                add(MenuRow.Action(text(R.string.action_save), R.drawable.ic_check) { save(typed) })
                if (app.packageName in settings.appNames) {
                    add(MenuRow.Action(text(R.string.rename_restore), R.drawable.ic_close) { save("") })
                }
            }
        }
    }
}

/** Menu shown when holding OK or pressing Menu on an app. */
class AppMenuActivity : SideMenuActivity() {
    override fun rootPage(): MenuPage {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val app = AppSource(this).loadApps().firstOrNull { it.packageName == pkg }
        return if (app == null) {
            MenuPage(getString(R.string.app_could_not_open)) { emptyList() }
        } else {
            AppMenus(this).appPage(app) { closeMenu() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_PACKAGE) == null) finish()
    }

    /** The app may have been uninstalled from this menu. */
    override fun onResume() {
        super.onResume()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        if (AppSource(this).loadApps().none { it.packageName == pkg }) closeMenu()
    }

    companion object {
        private const val EXTRA_PACKAGE = "package"

        fun intent(context: android.content.Context, pkg: String): Intent =
            Intent(context, AppMenuActivity::class.java).putExtra(EXTRA_PACKAGE, pkg)
    }
}
