package com.ravager.launcher.privileged

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.provider.Settings
import com.ravager.launcher.notifications.hasNotificationAccess

interface NotificationSystem {
    fun hasAccess(): Boolean

    /** Android refuses this access on low-memory devices, whatever is tried. */
    fun blockedByDevice(): Boolean
    fun openAccessSettings(): Boolean
}

class AndroidNotificationSystem(private val activity: Activity) : NotificationSystem {
    override fun hasAccess() = hasNotificationAccess(activity)

    override fun blockedByDevice() = activity.getSystemService(ActivityManager::class.java).isLowRamDevice

    override fun openAccessSettings(): Boolean =
        runCatching { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.isSuccess
}

class NotificationAccessFlow(
    private val system: NotificationSystem,
    private val gateway: PrivilegedGateway,
) : AccessFlow {
    override fun onButtonPressed(): ButtonResult = when {
        system.hasAccess() -> ButtonResult.ALREADY_DEFAULT
        system.blockedByDevice() -> ButtonResult.SHOW_ALTERNATIVES
        system.openAccessSettings() -> ButtonResult.SYSTEM_SCREEN_OPENED
        else -> ButtonResult.SHOW_ALTERNATIVES
    }

    override fun onNothingHappened(): ButtonResult = ButtonResult.SHOW_ALTERNATIVES

    override fun onReturnedFromSystem(): ReturnResult =
        if (system.hasAccess()) ReturnResult.NOW_DEFAULT else ReturnResult.SHOW_ALTERNATIVES

    /** On a blocked device there is nothing to change: Shizuku being ready is what makes the panel work. */
    override suspend fun applyWithShizuku(): Boolean {
        if (gateway.state() != ShizukuState.READY) return false
        if (system.blockedByDevice()) return true
        gateway.run(AdbCommands.allowNotifications.argv)
        return system.hasAccess()
    }
}
