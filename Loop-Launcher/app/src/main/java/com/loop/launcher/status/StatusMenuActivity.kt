package com.loop.launcher.status

import com.loop.launcher.R
import com.loop.launcher.privileged.ExtrasCoordinator
import com.loop.launcher.privileged.ShizukuGateway
import com.loop.launcher.ui.BaseActivity
import com.loop.launcher.ui.ScreenErrors
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import com.loop.launcher.ui.menu.SideMenuActivity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus

/** Live device readings: processor, memory, storage and time on. */
class StatusMenuActivity : SideMenuActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val gateway by lazy { ShizukuGateway(this) }
    private val sampler by lazy {
        StatusSampler(this, scope).apply { cpuPercentSource = ExtrasCoordinator(gateway)::procStat }
    }
    private var snapshot: StatusSnapshot? = null

    override fun rootPage() = MenuPage(getString(R.string.status_title)) { rows() }

    private fun rows(): List<MenuRow> {
        val now = snapshot ?: return emptyList()
        val language = BaseActivity.currentLanguage(this)
        val processor = StatusLines.processor(now.cpuPercent, now.tempC)?.let {
            MenuRow.Meter(getString(R.string.status_cpu), it, now.cpuPercent?.div(100.0))
        }
        return listOfNotNull(
            processor,
            MenuRow.Meter(
                getString(R.string.status_memory),
                StatusParsers.sizeText(now.ramUsed, now.ramTotal, language),
                now.ramUsed.toDouble() / now.ramTotal.coerceAtLeast(1),
            ),
            MenuRow.Meter(
                getString(R.string.status_storage),
                StatusParsers.sizeText(now.storageUsed, now.storageTotal, language),
                now.storageUsed.toDouble() / now.storageTotal.coerceAtLeast(1),
            ),
            MenuRow.Meter(getString(R.string.status_uptime), StatusParsers.uptimeText(now.uptimeMs, language)),
        )
    }

    override fun onResume() {
        super.onResume()
        sampler.start {
            snapshot = it
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        sampler.stop()
        gateway.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
