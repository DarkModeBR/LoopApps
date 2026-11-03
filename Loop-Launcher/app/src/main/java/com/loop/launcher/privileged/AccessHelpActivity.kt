package com.loop.launcher.privileged

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.loop.launcher.R
import com.loop.launcher.ui.ScreenErrors
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import com.loop.launcher.ui.menu.SideMenuActivity
import com.loop.launcher.ui.openOrWarn
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

enum class AccessKind { DEFAULT_LAUNCHER, NOTIFICATIONS }

/**
 * Tries the system screen first. When that fails or changes nothing, it stays open
 * and offers the two alternatives: Shizuku and the ADB command.
 */
class AccessHelpActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val handler = Handler(Looper.getMainLooper())
    private val system by lazy { AndroidHomeSystem(this) }
    private val notificationSystem by lazy { AndroidNotificationSystem(this) }
    private val gateway by lazy { ShizukuGateway(this) }
    private val homeFlow by lazy { DefaultLauncherFlow(system, gateway, settings) }
    private val flow: AccessFlow by lazy {
        if (kind == AccessKind.DEFAULT_LAUNCHER) homeFlow else NotificationAccessFlow(notificationSystem, gateway)
    }
    private val kind by lazy { AccessKind.entries[intent.getIntExtra(EXTRA_KIND, 0)] }
    private var leftForSystemScreen = false
    private var trying = false

    override val sheetWidth = 46f

    private val nothingHappened = Runnable {
        if (!leftForSystemScreen && flow.onNothingHappened() == ButtonResult.SYSTEM_SCREEN_OPENED) {
            watchSystemScreen()
        } else if (!leftForSystemScreen) {
            trying = false
        }
    }

    override fun rootPage(): MenuPage {
        val isLauncher = kind == AccessKind.DEFAULT_LAUNCHER
        return MenuPage(
            getString(if (isLauncher) R.string.access_default_title else R.string.access_notifications_title),
            { getString(if (isLauncher) R.string.access_default_intro else R.string.access_notifications_intro) },
        ) { overrideRows() + shizukuRows() + adbRows() + googleTvRows() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_TRY_SYSTEM, true)) {
            when (flow.onButtonPressed()) {
                ButtonResult.ALREADY_DEFAULT -> if (system.homeOverride() == null) finishDone()
                ButtonResult.SYSTEM_SCREEN_OPENED -> watchSystemScreen()
                ButtonResult.SHOW_ALTERNATIVES -> Unit
            }
        }
    }

    private fun watchSystemScreen() {
        trying = true
        handler.removeCallbacks(nothingHappened)
        handler.postDelayed(nothingHappened, SYSTEM_SCREEN_WAIT_MS)
    }

    override fun onPause() {
        super.onPause()
        if (trying) leftForSystemScreen = true
    }

    override fun onResume() {
        super.onResume()
        if (leftForSystemScreen) {
            leftForSystemScreen = false
            trying = false
            handler.removeCallbacks(nothingHappened)
            if (flow.onReturnedFromSystem() == ReturnResult.NOW_DEFAULT) finishDone()
        }
    }

    private fun finishDone() {
        if (kind == AccessKind.DEFAULT_LAUNCHER) settings.wasDefaultHome = true
        Toast.makeText(this, R.string.access_done, Toast.LENGTH_SHORT).show()
        closeMenu()
    }

    /** The launcher is the default, but another one catches the Home button first. */
    private fun overrideRows(): List<MenuRow> {
        if (kind != AccessKind.DEFAULT_LAUNCHER) return emptyList()
        val override = system.homeOverride() ?: return emptyList()
        val remaining = HomeOverride.without(system.enabledAccessibilityServices(), override.service)
        val command = AdbCommands.setAccessibilityServices(remaining)
        return listOfNotNull(
            MenuRow.Section(getString(R.string.override_title)),
            MenuRow.Note(getString(R.string.override_note, system.appLabel(override.pkg))),
            MenuRow.Action(getString(R.string.override_open_settings), R.drawable.ic_settings) {
                openOrWarn(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            if (gateway.state() == ShizukuState.READY) {
                MenuRow.Action(getString(R.string.override_turn_off), R.drawable.ic_check) {
                    scope.launch {
                        gateway.run(command.argv)
                        gateway.release()
                        if (system.homeOverride() == null) finishDone() else showFailure()
                    }
                }
            } else {
                null
            },
            MenuRow.Note(getString(R.string.access_adb_run)),
            MenuRow.Code(command.display),
        )
    }

    private fun shizukuRows(): List<MenuRow> {
        val state = gateway.state()
        val status = getString(ShizukuStatus.textRes(state))
        val button: MenuRow? = when (state) {
            ShizukuState.NOT_INSTALLED -> null
            ShizukuState.NEEDS_PERMISSION -> MenuRow.Action(getString(R.string.shizuku_authorize), R.drawable.ic_check) {
                scope.launch {
                    gateway.requestPermission()
                    refresh()
                }
            }
            ShizukuState.READY -> MenuRow.Action(getString(R.string.shizuku_apply), R.drawable.ic_check) {
                scope.launch {
                    if (flow.applyWithShizuku()) finishDone() else showFailure()
                    gateway.release()
                }
            }
            else -> MenuRow.Action(getString(R.string.shizuku_open), R.drawable.ic_open) {
                ShizukuStatus.openApp(this)
            }
        }
        return listOfNotNull(MenuRow.Section(getString(R.string.access_shizuku_title)), MenuRow.Note(status), button)
    }

    private fun adbRows(): List<MenuRow> {
        val rows = mutableListOf<MenuRow>(MenuRow.Section(getString(R.string.access_adb_title)))
        if (kind == AccessKind.NOTIFICATIONS) {
            if (notificationSystem.blockedByDevice()) {
                rows += MenuRow.Note(getString(R.string.notifications_blocked_note))
            } else {
                rows += MenuRow.Note(getString(R.string.access_adb_run))
                rows += MenuRow.Code(AdbCommands.allowNotifications.display)
                rows += MenuRow.Note(getString(R.string.access_adb_undo))
                rows += MenuRow.Code(AdbCommands.disallowNotifications.display)
            }
            return rows
        }
        rows += MenuRow.Note(getString(R.string.access_adb_run))
        rows += MenuRow.Code(AdbCommands.setHome.display)
        val previous = system.otherHomeComponent()?.also { settings.previousHome = it } ?: settings.previousHome
        previous?.let {
            rows += MenuRow.Note(getString(R.string.access_adb_undo))
            rows += MenuRow.Code(AdbCommands.setHomeTo(it).display)
        }
        return rows
    }

    private fun googleTvRows(): List<MenuRow> {
        if (kind != AccessKind.DEFAULT_LAUNCHER || !system.hasStockGoogleTvLauncher()) return emptyList()
        return listOf(
            MenuRow.Section(getString(R.string.googletv_title)),
            MenuRow.Note(getString(R.string.access_adb_run)),
            MenuRow.Code(AdbCommands.disableGoogleTvLauncher.display),
            MenuRow.Note(getString(R.string.access_adb_undo)),
            MenuRow.Code(AdbCommands.enableGoogleTvLauncher.display),
            MenuRow.Nav(getString(R.string.googletv_disable), R.drawable.ic_trash) { open(googleTvConfirmPage()) },
        )
    }

    private fun googleTvConfirmPage() = MenuPage(getString(R.string.googletv_title)) {
        listOf(
            MenuRow.Note(getString(R.string.googletv_warning)),
            MenuRow.Action(getString(R.string.googletv_disable), R.drawable.ic_trash, danger = true) {
                scope.launch {
                    if (homeFlow.disableGoogleTvLauncher()) finishDone() else showFailure()
                    gateway.release()
                }
            },
            MenuRow.Action(getString(R.string.dialog_cancel), R.drawable.ic_close) { goBack() },
        )
    }

    private fun showFailure() = Toast.makeText(this, R.string.access_failed, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    companion object {
        private const val EXTRA_TRY_SYSTEM = "try_system"
        private const val EXTRA_KIND = "kind"
        private const val SYSTEM_SCREEN_WAIT_MS = 1500L

        fun intent(context: Context, kind: AccessKind, trySystemScreen: Boolean = true): Intent =
            Intent(context, AccessHelpActivity::class.java)
                .putExtra(EXTRA_KIND, kind.ordinal)
                .putExtra(EXTRA_TRY_SYSTEM, trySystemScreen)
    }
}
