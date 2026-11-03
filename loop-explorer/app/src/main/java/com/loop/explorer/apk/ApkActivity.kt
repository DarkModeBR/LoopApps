package com.loop.explorer.apk

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import com.loop.explorer.R
import com.loop.explorer.browse.Openers
import com.loop.explorer.files.Sizes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.menu.MenuPage
import com.loop.explorer.ui.menu.MenuRow
import com.loop.explorer.ui.menu.SideMenuActivity
import com.loop.explorer.update.InstallOutcome
import com.loop.explorer.update.PendingInstall
import com.loop.explorer.update.UpdateInstaller
import java.io.File

/** Shows what an APK is before installing it: name, icon, version and whether it is already installed. */
class ApkActivity : SideMenuActivity() {
    private val file by lazy { File(intent.getStringExtra(EXTRA_FILE).orEmpty()) }
    private val installer by lazy { UpdateInstaller(this) }
    private val pending by lazy { PendingInstall(installer) }

    private data class Apk(val label: String, val icon: Drawable?, val pkg: String, val version: String, val code: Long)

    private fun read(): Apk? {
        val manager = packageManager
        val info: PackageInfo = manager.getPackageArchiveInfo(file.path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        // The archive is not installed, so its name and icon are read from the file itself.
        app.sourceDir = file.path
        app.publicSourceDir = file.path
        return Apk(app.loadLabel(manager).toString(), app.loadIcon(manager), info.packageName, info.versionName.orEmpty(), versionCode(info))
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun installed(pkg: String): PackageInfo? = runCatching { packageManager.getPackageInfo(pkg, 0) }.getOrNull()

    override fun rootPage(): MenuPage {
        val apk = runCatching { read() }.getOrNull()
        val language = BaseActivity.currentLanguage(this)
        if (apk == null) {
            return MenuPage(file.name) {
                listOf(
                    MenuRow.Note(getString(R.string.apk_invalid)),
                    MenuRow.Action(getString(R.string.menu_open_with), R.drawable.ic_apps) {
                        Openers.openWith(this, file, chooser = true)
                        closeMenu()
                    },
                )
            }
        }
        val subtitle = listOf(getString(R.string.apk_version, apk.version), Sizes.text(file.length(), language)).joinToString(" · ")
        return MenuPage(apk.label, { subtitle }, icon = apk.icon) {
            val current = installed(apk.pkg)
            val status = when {
                current == null -> getString(R.string.apk_not_installed)
                versionCode(current) < apk.code -> getString(R.string.apk_older_installed, current.versionName.orEmpty())
                versionCode(current) == apk.code -> getString(R.string.apk_same_installed)
                else -> getString(R.string.apk_newer_installed, current.versionName.orEmpty())
            }
            listOf(
                MenuRow.Note(status),
                MenuRow.Action(
                    getString(if (current == null) R.string.apk_install else R.string.apk_update),
                    R.drawable.ic_package,
                ) { install() },
                MenuRow.Note(apk.pkg),
            )
        }
    }

    private fun install() {
        when (pending.start(file)) {
            InstallOutcome.INSTALLER_OPENED -> closeMenu()
            InstallOutcome.WAITING_FOR_PERMISSION -> Toast.makeText(this, R.string.apk_allow_hint, Toast.LENGTH_LONG).show()
            InstallOutcome.FAILED -> Toast.makeText(this, R.string.apk_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        when (pending.onResume()) {
            InstallOutcome.INSTALLER_OPENED -> closeMenu()
            InstallOutcome.FAILED -> Toast.makeText(this, R.string.apk_failed, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    companion object {
        private const val EXTRA_FILE = "file"

        fun intent(context: Context, file: File): Intent = Intent(context, ApkActivity::class.java).putExtra(EXTRA_FILE, file.path)
    }
}
