package com.loop.launcher.privileged

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.loop.launcher.R
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import com.loop.launcher.ui.menu.SideMenuActivity
import com.loop.launcher.ui.openOrWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Turns the Home capture on and off: through Shizuku, the system screen or an ADB command. */
class HomeCaptureMenus(private val menu: SideMenuActivity, private val scope: CoroutineScope) {
    private val gateway by lazy { ShizukuGateway(menu) }

    private fun text(id: Int) = menu.getString(id)

    private fun enabled(): String? =
        Settings.Secure.getString(menu.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    fun value(): String = text(if (HomeCapture.isOn(enabled())) R.string.value_on else R.string.value_off)

    fun page() = MenuPage(text(R.string.capture_title), { text(R.string.capture_hint) }) {
        val current = enabled()
        val on = HomeCapture.isOn(current)
        val commands = if (on) listOf(HomeCapture.disableCommand(current)) else HomeCapture.enableCommands(current)
        val state = gateway.state()
        buildList {
            add(MenuRow.Note(text(if (on) R.string.capture_is_on else R.string.capture_is_off)))
            if (state == ShizukuState.READY) {
                add(
                    MenuRow.Action(text(if (on) R.string.capture_turn_off else R.string.capture_turn_on), R.drawable.ic_home) {
                        run(commands, wanted = !on)
                    },
                )
            } else {
                add(MenuRow.Note(text(ShizukuStatus.textRes(state))))
            }
            add(
                MenuRow.Action(text(R.string.override_open_settings), R.drawable.ic_settings) {
                    menu.openOrWarn(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
            add(MenuRow.Section(text(R.string.access_adb_title)))
            add(MenuRow.Note(text(R.string.access_adb_run)))
            commands.forEach { add(MenuRow.Code(it.display)) }
            add(MenuRow.Note(text(R.string.capture_warning)))
        }
    }

    private fun run(commands: List<ShellCommand>, wanted: Boolean) {
        scope.launch {
            commands.forEach { gateway.run(it.argv) }
            gateway.release()
            if (HomeCapture.isOn(enabled()) != wanted) {
                Toast.makeText(menu, R.string.access_failed, Toast.LENGTH_LONG).show()
            }
            menu.refresh()
        }
    }
}
