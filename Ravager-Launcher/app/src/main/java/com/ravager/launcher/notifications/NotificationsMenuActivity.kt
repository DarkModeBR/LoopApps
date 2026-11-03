package com.ravager.launcher.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.ravager.launcher.R
import com.ravager.launcher.privileged.PrivilegedGateway
import com.ravager.launcher.privileged.ShizukuGateway
import com.ravager.launcher.ui.ScreenErrors
import com.ravager.launcher.ui.menu.MenuPage
import com.ravager.launcher.ui.menu.MenuRow
import com.ravager.launcher.ui.menu.SideMenuActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Shared access to the notifications that should appear in the launcher. */
object NotificationCenter {
    private const val PREFS = "notifications"
    private const val KEY_DISMISSED = "dismissed"

    fun dismissed(context: Context): DismissedNotifications {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DismissedNotifications(prefs.getStringSet(KEY_DISMISSED, emptySet()).orEmpty()) {
            prefs.edit().putStringSet(KEY_DISMISSED, it).apply()
        }
    }

    fun visible(context: Context): List<NotificationItem> = dismissed(context).filter(NotificationStore.visible())

    fun commands(context: Context, gateway: PrivilegedGateway) = NotificationCommands(
        RavagerNotificationListener.current(),
        dismissed(context),
        { pkg -> launch(context, pkg) },
        { item -> NotificationTargets.open(gateway, item.pressRecord) },
    )

    /** The app itself or, for system parts that have no main screen, the page about them. */
    private fun launch(context: Context, pkg: String): Boolean {
        val manager = context.packageManager
        val intent = manager.getLeanbackLaunchIntentForPackage(pkg)
            ?: manager.getLaunchIntentForPackage(pkg)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

/** The notification panel: open, remove, clear all, and any extra actions a notification offers. */
class NotificationsMenuActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val gateway by lazy { ShizukuGateway(this) }
    private val changed = { runOnUiThread { refresh() } }

    override fun rootPage() = MenuPage(getString(R.string.notifications_title), { subtitle() }) {
        val items = NotificationCenter.visible(this)
        if (items.isEmpty()) {
            listOf(MenuRow.Note(getString(R.string.notifications_empty)))
        } else {
            val clearAll = if (items.any { !it.ongoing }) {
                listOf(
                    MenuRow.Action(getString(R.string.notifications_clear_all), R.drawable.ic_close) {
                        NotificationCenter.commands(this, gateway).clearAll(items)
                        refresh()
                        NotificationStore.notifyListeners()
                    },
                )
            } else {
                emptyList()
            }
            clearAll + items.map { item ->
                MenuRow.Nav(item.app, R.drawable.ic_bell, detail = item.text) { open(itemPage(item)) }
            }
        }
    }

    private fun subtitle(): String? {
        if (RavagerNotificationListener.current() != null || NotificationCenter.visible(this).isEmpty()) return null
        return getString(R.string.notifications_limited)
    }

    private fun itemPage(item: NotificationItem) = MenuPage(item.app) {
        val commands = NotificationCenter.commands(this, gateway)
        val extraActions = commands.actions(item).mapIndexed { index, title ->
            MenuRow.Action(title) {
                if (!commands.runAction(item, index)) showFailure()
                closeMenu()
            }
        }
        val remove = if (item.ongoing) {
            emptyList()
        } else {
            listOf(
                MenuRow.Action(getString(R.string.notifications_remove), R.drawable.ic_trash, danger = true) {
                    commands.remove(item)
                    NotificationStore.notifyListeners()
                    goBack()
                },
            )
        }
        listOf(
            MenuRow.Note(item.text),
            MenuRow.Action(getString(R.string.menu_open), R.drawable.ic_open) {
                scope.launch {
                    val opened = commands.open(item)
                    gateway.release()
                    if (opened) closeMenu() else showFailure()
                }
            },
        ) + extraActions + remove
    }

    private fun showFailure() = Toast.makeText(this, R.string.notifications_action_failed, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationStore.addListener(changed)
        if (RavagerNotificationListener.current() == null) {
            scope.launch {
                DumpsysNotifications.read(gateway)?.let { items ->
                    NotificationStore.replaceAll(items.map { it.copy(app = appLabel(it.pkg)) })
                }
                gateway.release()
            }
        }
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    override fun onDestroy() {
        super.onDestroy()
        NotificationStore.removeListener(changed)
        scope.cancel()
    }
}
