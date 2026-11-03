package com.loop.explorer.access

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.loop.explorer.R
import com.loop.explorer.privileged.ShizukuGateway
import com.loop.explorer.privileged.ShizukuState
import com.loop.explorer.privileged.ShizukuStatus
import com.loop.explorer.ui.ScreenErrors
import com.loop.explorer.ui.menu.MenuPage
import com.loop.explorer.ui.menu.MenuRow
import com.loop.explorer.ui.menu.SideMenuActivity
import com.loop.explorer.ui.opened
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * From Android 11 on, a file manager needs access to all files, given on a system screen that many
 * TV systems do not have. The same paths as Loop Launcher: system screen, Shizuku or an ADB command.
 */
class AccessActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val gateway by lazy { ShizukuGateway(this) }

    override val sheetWidth = 46f

    private val command get() = listOf("appops", "set", packageName, "MANAGE_EXTERNAL_STORAGE", "allow")

    private fun granted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    override fun rootPage() = MenuPage(getString(R.string.access_title), { getString(R.string.access_intro) }) {
        val state = gateway.state()
        buildList {
            add(MenuRow.Action(getString(R.string.access_open_system), R.drawable.ic_open) { openSystemScreen() })
            add(MenuRow.Section(getString(R.string.access_shizuku_title)))
            add(MenuRow.Note(getString(ShizukuStatus.textRes(state))))
            when (state) {
                ShizukuState.READY -> add(MenuRow.Action(getString(R.string.access_apply), R.drawable.ic_check) { applyWithShizuku() })
                ShizukuState.NEEDS_PERMISSION -> add(
                    MenuRow.Action(getString(R.string.shizuku_authorize), R.drawable.ic_check) {
                        scope.launch {
                            gateway.requestPermission()
                            refresh()
                        }
                    },
                )
                ShizukuState.NOT_INSTALLED -> Unit
                else -> add(MenuRow.Action(getString(R.string.shizuku_open), R.drawable.ic_open) { ShizukuStatus.openApp(this@AccessActivity) })
            }
            add(MenuRow.Section(getString(R.string.access_adb_title)))
            add(MenuRow.Note(getString(R.string.access_adb_run)))
            add(MenuRow.Code("adb shell " + command.joinToString(" ")))
        }
    }

    private fun openSystemScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val own = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        val all = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        if (!opened { startActivity(own) } && !opened { startActivity(all) }) {
            Toast.makeText(this, R.string.access_no_screen, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyWithShizuku() {
        scope.launch {
            gateway.run(command)
            gateway.release()
            if (granted()) closeMenu() else Toast.makeText(this@AccessActivity, R.string.access_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (granted()) closeMenu()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
