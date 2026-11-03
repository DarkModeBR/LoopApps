package com.loop.launcher.privileged

private fun commandOf(vararg words: String) = ShellCommand(words.toList())

/**
 * [attempts] go from the cleanest way to the one that works on more devices: some systems
 * refuse the Android command to the shell, yet accept the plain one.
 */
enum class PowerAction(private val attempts: List<ShellCommand>) {
    SLEEP(listOf(commandOf("input", "keyevent", "KEYCODE_SLEEP"))),
    REBOOT(listOf(commandOf("svc", "power", "reboot"), commandOf("reboot"))),
    SHUTDOWN(listOf(commandOf("svc", "power", "shutdown"), commandOf("reboot", "-p")));

    /** The command to show the user. */
    val command: ShellCommand get() = attempts.last()

    val commands: List<ShellCommand> get() = attempts
}

object PowerActions {
    /** Android only lets privileged processes do these, so they all go through Shizuku. */
    suspend fun perform(gateway: PrivilegedGateway, action: PowerAction): Boolean {
        for (command in action.commands) {
            // No answer means Shizuku is gone, which also happens when the box is already going down.
            val result = gateway.run(command.argv) ?: return false
            if (result.exitCode == 0) return true
        }
        return false
    }
}
