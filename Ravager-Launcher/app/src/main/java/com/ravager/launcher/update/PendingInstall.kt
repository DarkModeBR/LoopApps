package com.ravager.launcher.update

import java.io.File

enum class InstallOutcome { INSTALLER_OPENED, WAITING_FOR_PERMISSION, FAILED }

interface InstallTarget {
    fun canInstall(): Boolean
    fun openPermissionScreen(): Boolean
    fun launchInstaller(apk: File): Boolean
}

/** Remembers a downloaded update while the user grants permission, so it is not downloaded twice. */
class PendingInstall(private val target: InstallTarget) {
    private var waiting: File? = null

    fun start(apk: File): InstallOutcome {
        if (target.canInstall()) return launch(apk)
        if (!target.openPermissionScreen()) return InstallOutcome.FAILED
        waiting = apk
        return InstallOutcome.WAITING_FOR_PERMISSION
    }

    /** Null when no update was waiting. */
    fun onResume(): InstallOutcome? {
        val apk = waiting ?: return null
        waiting = null
        return if (target.canInstall()) launch(apk) else InstallOutcome.FAILED
    }

    private fun launch(apk: File) =
        if (target.launchInstaller(apk)) InstallOutcome.INSTALLER_OPENED else InstallOutcome.FAILED
}
