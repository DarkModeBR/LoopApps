package com.ravager.launcher.privileged

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.ravager.launcher.R
import com.ravager.launcher.ui.ScreenErrors
import com.ravager.launcher.ui.menu.MenuPage
import com.ravager.launcher.ui.menu.MenuRow
import com.ravager.launcher.ui.menu.SideMenuActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Sleep, restart and turn off. Android only allows them through Shizuku or ADB. */
class PowerMenuActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val handler = Handler(Looper.getMainLooper())
    private val gateway by lazy { ShizukuGateway(this) }
    private var shownState: ShizukuState? = null

    override fun rootPage() = MenuPage(getString(R.string.power_title)) {
        val state = gateway.state()
        shownState = state
        if (state == ShizukuState.READY) readyRows() else alternativeRows(state)
    }

    /** The rows change completely with Shizuku, so the focus goes back to the first one. */
    override fun onResume() {
        val before = shownState
        super.onResume()
        if (before != null && before != shownState) replacePage(rootPage())
    }

    private fun readyRows(): List<MenuRow> = listOf(
        MenuRow.Action(getString(R.string.power_sleep), R.drawable.ic_moon) { perform(PowerAction.SLEEP) },
        MenuRow.Nav(getString(R.string.power_reboot), R.drawable.ic_restart) {
            open(confirmPage(R.string.power_reboot, R.string.power_reboot_note, PowerAction.REBOOT))
        },
        MenuRow.Nav(getString(R.string.power_shutdown), R.drawable.ic_power) {
            open(confirmPage(R.string.power_shutdown, R.string.power_shutdown_note, PowerAction.SHUTDOWN))
        },
    )

    private fun confirmPage(title: Int, note: Int, action: PowerAction) = MenuPage(getString(title)) {
        listOf(
            MenuRow.Note(getString(note)),
            MenuRow.Action(getString(title), R.drawable.ic_power, danger = true) { perform(action) },
            MenuRow.Action(getString(R.string.dialog_cancel), R.drawable.ic_close) { goBack() },
        )
    }

    private fun alternativeRows(state: ShizukuState): List<MenuRow> = buildList {
        add(MenuRow.Note(getString(R.string.power_needs_shizuku)))
        add(MenuRow.Section(getString(R.string.access_shizuku_title)))
        add(MenuRow.Note(getString(ShizukuStatus.textRes(state))))
        when (state) {
            ShizukuState.NOT_INSTALLED -> Unit
            ShizukuState.NEEDS_PERMISSION -> add(
                MenuRow.Action(getString(R.string.shizuku_authorize), R.drawable.ic_check) {
                    scope.launch {
                        gateway.requestPermission()
                        replacePage(rootPage())
                    }
                },
            )
            else -> add(MenuRow.Action(getString(R.string.shizuku_open), R.drawable.ic_open) { ShizukuStatus.openApp(this@PowerMenuActivity) })
        }
        add(MenuRow.Section(getString(R.string.access_adb_title)))
        PowerAction.entries.forEach { action ->
            add(MenuRow.Note(getString(labelOf(action))))
            add(MenuRow.Code(action.command.display))
        }
    }

    private fun labelOf(action: PowerAction) = when (action) {
        PowerAction.SLEEP -> R.string.power_sleep
        PowerAction.REBOOT -> R.string.power_reboot
        PowerAction.SHUTDOWN -> R.string.power_shutdown
    }

    private fun perform(action: PowerAction) {
        scope.launch {
            val done = PowerActions.perform(gateway, action)
            gateway.release()
            if (done) {
                closeMenu()
            } else {
                // Restarting also takes Shizuku down, which looks like a failure until the box goes dark.
                handler.postDelayed({ if (!isFinishing) showFailure() }, FAILURE_DELAY_MS)
            }
        }
    }

    private fun showFailure() = Toast.makeText(this, R.string.access_failed, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    private companion object {
        const val FAILURE_DELAY_MS = 4_000L
    }
}
