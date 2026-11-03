package com.loop.launcher.privileged

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.loop.launcher.ui.opened

class AndroidHomeSystem(private val activity: Activity) : HomeSystem {
    private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun currentHome() =
        activity.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo

    override fun isDefaultHome(): Boolean = currentHome()?.packageName == activity.packageName

    /** The launcher chosen now, for the undo command; null when it is this one or none is chosen. */
    fun otherHomeComponent(): String? {
        val info = currentHome() ?: return null
        if (info.packageName == activity.packageName || info.name.endsWith("ResolverActivity")) return null
        return ComponentName(info.packageName, info.name).flattenToShortString()
    }

    /** Another launcher that is catching the Home button, if there is one. */
    fun homeOverride(): HomeOverride? {
        val enabled = Settings.Secure.getString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val homes = activity.packageManager.queryIntentActivities(homeIntent, 0).map { it.activityInfo.packageName }.toSet()
        return HomeOverride.find(enabled, activity.packageName, homes)
    }

    fun enabledAccessibilityServices(): String? =
        Settings.Secure.getString(activity.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    fun appLabel(pkg: String): String = runCatching {
        val manager = activity.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    override fun roleAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roles = activity.getSystemService(RoleManager::class.java)
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    override fun requestHomeRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val roles = activity.getSystemService(RoleManager::class.java)
            // The system screen needs to know who is asking, which only a request for a result tells it.
            activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
        }
    }

    override fun openHomeSettings() = open(Intent(Settings.ACTION_HOME_SETTINGS))
    override fun openDefaultAppsSettings() = open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    override fun openHomeChooser() = open(Intent.createChooser(homeIntent, null))

    override fun hasStockGoogleTvLauncher(): Boolean = runCatching {
        activity.packageManager.getPackageInfo("com.google.android.apps.tv.launcherx", 0)
    }.isSuccess

    private fun open(intent: Intent): Boolean = opened { activity.startActivity(intent) }

    private companion object {
        const val REQUEST_HOME_ROLE = 4022
    }
}
