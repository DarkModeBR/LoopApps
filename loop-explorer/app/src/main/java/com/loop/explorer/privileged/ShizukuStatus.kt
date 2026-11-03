package com.loop.explorer.privileged

import android.app.Activity
import com.loop.explorer.R

object ShizukuStatus {
    private const val PACKAGE = "moe.shizuku.privileged.api"

    fun textRes(state: ShizukuState): Int = when (state) {
        ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
        ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
        ShizukuState.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
        ShizukuState.READY -> R.string.shizuku_ready
        ShizukuState.UNSUPPORTED -> R.string.shizuku_unsupported
    }

    fun openApp(activity: Activity) {
        val manager = activity.packageManager
        (manager.getLeanbackLaunchIntentForPackage(PACKAGE) ?: manager.getLaunchIntentForPackage(PACKAGE))
            ?.let(activity::startActivity)
    }
}
