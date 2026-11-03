package com.ravager.launcher.privileged

/** Edits Android's colon-separated list of enabled accessibility services for the Home capture. */
object HomeCapture {
    const val SERVICE = "com.ravager.launcher/com.ravager.launcher.privileged.HomeCaptureService"
    private const val SHORT_SERVICE = "com.ravager.launcher/.privileged.HomeCaptureService"

    private fun services(enabled: String?) = enabled.orEmpty().split(':').filter { '/' in it }
    private fun isOurs(service: String) = service == SERVICE || service == SHORT_SERVICE

    fun isOn(enabled: String?): Boolean = services(enabled).any(::isOurs)

    fun turnedOn(enabled: String?): String =
        if (isOn(enabled)) enabled.orEmpty() else (services(enabled) + SERVICE).joinToString(":")

    fun turnedOff(enabled: String?): String = services(enabled).filterNot(::isOurs).joinToString(":")

    fun enableCommands(enabled: String?): List<ShellCommand> = listOf(
        AdbCommands.setAccessibilityServices(turnedOn(enabled)),
        ShellCommand(listOf("settings", "put", "secure", "accessibility_enabled", "1")),
    )

    fun disableCommand(enabled: String?): ShellCommand = AdbCommands.setAccessibilityServices(turnedOff(enabled))
}
