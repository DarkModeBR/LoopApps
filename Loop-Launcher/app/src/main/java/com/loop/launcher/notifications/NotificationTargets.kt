package com.loop.launcher.notifications

import com.loop.launcher.privileged.PrivilegedGateway

/** What a notification opens when pressed, as far as Android tells the ADB shell. */
data class NotificationTarget(
    val action: String? = null,
    val categories: List<String> = emptyList(),
    val data: String? = null,
    val type: String? = null,
    val pkg: String? = null,
    val component: String? = null,
) {
    fun startCommand(): List<String> = buildList {
        add("am")
        add("start")
        action?.let { add("-a"); add(it) }
        categories.forEach { add("-c"); add(it) }
        data?.let { add("-d"); add(it) }
        type?.let { add("-t"); add(it) }
        component?.let { add("-n"); add(it) }
        // A package at the end of the command limits the search to that app.
        if (component == null) pkg?.let { add(it) }
    }
}

/**
 * Where Android refuses notification access, the launcher cannot press the notification itself.
 * The shell can still read where it leads and open that screen. Details the notification keeps
 * only in its extras are not shown by Android, so an app may open on its main screen.
 */
object NotificationTargets {
    private val RECORD_ID = Regex("^[0-9a-fA-F]+$")
    private val INTENT = Regex("""requestIntent=(.*)""")
    private const val CHOOSER = "android.intent.action.CHOOSER"

    /** Screens that some TV systems removed, and the nearest one they kept. */
    private val NEAREST_SCREEN = mapOf("android.settings.HARD_KEYBOARD_SETTINGS" to "android.settings.INPUT_METHOD_SETTINGS")

    fun readCommand(record: String): List<String>? {
        if (!RECORD_ID.matches(record)) return null
        return listOf("sh", "-c", "dumpsys activity intents | grep -A2 'PendingIntentRecord{$record '")
    }

    /** Null when the notification does not open a screen, or needs extras that cannot be read. */
    fun parse(output: String): NotificationTarget? {
        if ("type=startActivity" !in output) return null
        val text = INTENT.find(output)?.groupValues?.get(1) ?: return null
        val values = text.trim().split(' ').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        val target = NotificationTarget(
            action = values["act"],
            categories = values["cat"]?.removeSurrounding("[", "]")?.split(',').orEmpty(),
            data = values["dat"],
            type = values["typ"],
            pkg = values["pkg"],
            component = values["cmp"],
        )
        val usable = (target.action != null || target.component != null) && target.action != CHOOSER
        return target.takeIf { usable }
    }

    suspend fun open(gateway: PrivilegedGateway, record: String?): Boolean {
        val command = record?.let(::readCommand) ?: return false
        val answer = gateway.run(command)?.takeIf { it.exitCode == 0 } ?: return false
        val target = parse(answer.output) ?: return false
        if (start(gateway, target)) return true
        val nearest = NEAREST_SCREEN[target.action] ?: return false
        return start(gateway, target.copy(action = nearest))
    }

    /** The command ends well even when no screen opens, and says so in its text. */
    private suspend fun start(gateway: PrivilegedGateway, target: NotificationTarget): Boolean {
        val result = gateway.run(target.startCommand()) ?: return false
        return result.exitCode == 0 && "Error" !in result.output && "Exception" !in result.output
    }
}
