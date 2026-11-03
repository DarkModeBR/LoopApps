package com.ravager.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.trays.AppCategory

class AppSource(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    /** Lists launchable apps; a leanback activity wins over a phone one in the same package. */
    fun loadApps(): List<AppEntry> {
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        (query(Intent.CATEGORY_LEANBACK_LAUNCHER) + query(Intent.CATEGORY_LAUNCHER)).forEach { info ->
            byPackage.getOrPut(info.activityInfo.packageName) { info }
        }
        byPackage.remove(context.packageName)
        val apps = byPackage.values.map { info ->
            val activity = info.activityInfo
            AppEntry(
                packageName = activity.packageName,
                activityName = activity.name,
                label = info.loadLabel(pm).toString(),
                hasBanner = activity.banner != 0 || activity.applicationInfo.banner != 0,
                declaredCategory = declaredCategory(activity.applicationInfo),
            )
        }
        return CustomNames.apply(apps, AppSettings(context).appNames)
    }

    fun banner(entry: AppEntry): Drawable? = runCatching {
        pm.getActivityBanner(component(entry)) ?: pm.getApplicationBanner(entry.packageName)
    }.getOrNull()

    fun icon(entry: AppEntry): Drawable = runCatching { pm.getActivityIcon(component(entry)) }
        .getOrElse { pm.defaultActivityIcon }

    fun version(entry: AppEntry): String? = runCatching {
        pm.getPackageInfo(entry.packageName, 0).versionName?.takeIf(String::isNotBlank)
    }.getOrNull()

    /** Returns false when the app could not be opened, for example if it was just uninstalled. */
    fun launch(entry: AppEntry): Boolean = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_MAIN)
                .setComponent(component(entry))
                .addCategory(launchCategory(entry))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }.isSuccess

    private fun launchCategory(entry: AppEntry): String =
        if (query(Intent.CATEGORY_LEANBACK_LAUNCHER).any { it.activityInfo.name == entry.activityName }) {
            Intent.CATEGORY_LEANBACK_LAUNCHER
        } else {
            Intent.CATEGORY_LAUNCHER
        }

    private fun component(entry: AppEntry) = ComponentName(entry.packageName, entry.activityName)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun declaredCategory(info: ApplicationInfo): AppCategory? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.MOVIES
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.MUSIC
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            else -> null
        }
    }
}
