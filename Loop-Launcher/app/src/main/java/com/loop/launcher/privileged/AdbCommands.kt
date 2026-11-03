package com.loop.launcher.privileged

/** A command shown to the user with its "adb shell" prefix and run without it through Shizuku. */
data class ShellCommand(val argv: List<String>) {
    val display: String get() = "adb shell " + argv.joinToString(" ")
}

object AdbCommands {
    private const val HOME_COMPONENT = "com.loop.launcher/.home.HomeActivity"
    private const val GOOGLE_TV_LAUNCHER = "com.google.android.apps.tv.launcherx"
    private const val ACCESSIBILITY_SERVICES = "enabled_accessibility_services"
    private const val LISTENER_COMPONENT =
        "com.loop.launcher/com.loop.launcher.notifications.LoopNotificationListener"

    val setHome = setHomeTo(HOME_COMPONENT)
    val disableGoogleTvLauncher = ShellCommand(listOf("pm", "disable-user", "--user", "0", GOOGLE_TV_LAUNCHER))
    val enableGoogleTvLauncher = ShellCommand(listOf("pm", "enable", GOOGLE_TV_LAUNCHER))
    val allowNotifications = ShellCommand(listOf("cmd", "notification", "allow_listener", LISTENER_COMPONENT))
    val disallowNotifications = ShellCommand(listOf("cmd", "notification", "disallow_listener", LISTENER_COMPONENT))

    /** Keeps [remaining] accessibility services on; an empty list clears the setting. */
    fun setAccessibilityServices(remaining: String) = ShellCommand(
        if (remaining.isEmpty()) {
            listOf("settings", "delete", "secure", ACCESSIBILITY_SERVICES)
        } else {
            listOf("settings", "put", "secure", ACCESSIBILITY_SERVICES, remaining)
        },
    )

    fun setHomeTo(component: String) = ShellCommand(listOf("cmd", "package", "set-home-activity", component))
}
