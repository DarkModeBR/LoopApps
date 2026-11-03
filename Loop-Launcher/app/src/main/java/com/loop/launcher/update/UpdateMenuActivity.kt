package com.loop.launcher.update

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.loop.launcher.R
import com.loop.launcher.ui.ScreenErrors
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import com.loop.launcher.ui.menu.SideMenuActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Offers a new version, downloads it and hands it to the system installer. */
class UpdateMenuActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val installer by lazy { UpdateInstaller(this) }
    private val pending by lazy { PendingInstall(installer) }
    private var downloading = false

    override fun rootPage(): MenuPage {
        val version = intent.getStringExtra(EXTRA_VERSION).orEmpty()
        val notes = intent.getStringExtra(EXTRA_NOTES).orEmpty().take(MAX_NOTES)
        return MenuPage(getString(R.string.update_title, version)) {
            listOfNotNull(
                notes.takeIf { it.isNotBlank() }?.let(MenuRow::Note),
                MenuRow.Action(
                    getString(if (downloading) R.string.update_downloading else R.string.update_install),
                    R.drawable.ic_update,
                ) { install() },
                MenuRow.Action(getString(R.string.update_later), R.drawable.ic_close) { closeMenu() },
            )
        }
    }

    private fun install() {
        if (downloading) return
        downloading = true
        refresh()
        scope.launch {
            val apk = installer.download(intent.getStringExtra(EXTRA_URL).orEmpty())
            downloading = false
            if (apk == null || pending.start(apk) == InstallOutcome.FAILED) showFailed()
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pending.onResume() == InstallOutcome.FAILED) showFailed()
    }

    private fun showFailed() = Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_NOTES = "notes"
        private const val EXTRA_URL = "url"
        private const val MAX_NOTES = 600

        fun intent(context: Context, update: UpdateInfo): Intent =
            Intent(context, UpdateMenuActivity::class.java)
                .putExtra(EXTRA_VERSION, update.version)
                .putExtra(EXTRA_NOTES, update.notes)
                .putExtra(EXTRA_URL, update.apkUrl)
    }
}
