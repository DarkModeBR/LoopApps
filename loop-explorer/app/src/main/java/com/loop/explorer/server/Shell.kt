package com.loop.explorer.server

import com.loop.explorer.privileged.CommandResult
import com.loop.explorer.privileged.PrivilegedGateway
import com.loop.explorer.privileged.ShizukuState
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * Runs commands for the web terminal. With Shizuku they run like "adb shell"; without it, with the
 * permissions of this app only. Every command has a time limit and a size limit, so one that never
 * ends, like logcat, cannot hold the server.
 */
class Shell(private val gateway: PrivilegedGateway, private val appTemp: File) {
    enum class Mode { ADB, APP }

    data class Result(val exitCode: Int, val output: String, val mode: Mode)

    fun mode(): Mode = if (gateway.state() == ShizukuState.READY) Mode.ADB else Mode.APP

    /**
     * The command runs in its own process group with its output in a file. When time is up, the
     * whole group is killed, including what the command started, and only the start of the file
     * comes back. Waiting for the output to end does not work: a child left behind keeps it open.
     * The command is passed as $0, so it needs no quoting.
     */
    private fun script(temp: String, seconds: Int) = """
        f="$temp/loop-shell-$$.out"
        if command -v setsid >/dev/null 2>&1; then setsid sh -c "${'$'}0" > "${'$'}f" 2>&1 & else sh -c "${'$'}0" > "${'$'}f" 2>&1 & fi
        p=${'$'}!
        end=${'$'}(( ${'$'}(date +%s) + $seconds ))
        while kill -0 ${'$'}p 2>/dev/null && [ ${'$'}(date +%s) -lt ${'$'}end ]; do sleep 0.2; done
        if kill -0 ${'$'}p 2>/dev/null; then kill -9 -- -${'$'}p 2>/dev/null; kill -9 ${'$'}p 2>/dev/null; wait ${'$'}p 2>/dev/null; r=$TIMED_OUT; else wait ${'$'}p; r=${'$'}?; fi
        head -c $MAX_BYTES "${'$'}f"
        rm -f "${'$'}f"
        exit ${'$'}r
    """.trimIndent()

    fun run(command: String, seconds: Int = DEFAULT_SECONDS): Result {
        if (mode() == Mode.ADB) {
            val answer = adb(listOf("sh", "-c", script(ADB_TEMP, seconds), command))
            if (answer != null) return Result(answer.exitCode, answer.output, Mode.ADB)
        }
        return local(listOf("sh", "-c", script(appTemp.path, seconds), command), seconds)
    }

    /** One fixed command through Shizuku, as an argument list; null when Shizuku is not ready. */
    fun adb(argv: List<String>): CommandResult? {
        if (gateway.state() != ShizukuState.READY) return null
        return runBlocking { gateway.run(argv) }
    }

    private fun local(argv: List<String>, seconds: Int): Result {
        val process = runCatching { ProcessBuilder(argv).redirectErrorStream(true).start() }.getOrElse {
            return Result(-1, it.message.orEmpty(), Mode.APP)
        }
        val output = ByteArrayOutputStream()
        val reader = Thread {
            runCatching {
                process.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (output.size() < MAX_BYTES) output.write(buffer, 0, read)
                    }
                }
            }
        }
        reader.start()
        // The script stops itself; this is only a safety net in case the shell itself hangs.
        val deadline = System.currentTimeMillis() + (seconds + SAFETY_SECONDS) * 1000L
        var code: Int? = null
        while (code == null && System.currentTimeMillis() < deadline) {
            code = runCatching { process.exitValue() }.getOrNull()
            if (code == null) Thread.sleep(POLL_MS)
        }
        if (code == null) process.destroy()
        reader.join(READ_WAIT_MS)
        return Result(code ?: TIMED_OUT, output.toString("UTF-8"), Mode.APP)
    }

    companion object {
        const val DEFAULT_SECONDS = 30
        const val MAX_BYTES = 400_000
        const val TIMED_OUT = 137
        private const val ADB_TEMP = "/data/local/tmp"
        private const val SAFETY_SECONDS = 5
        private const val POLL_MS = 50L
        private const val READ_WAIT_MS = 2_000L
    }
}

/** Sleep, restart and turn off, with the same fallbacks as Loop Launcher. */
enum class PowerAction(val attempts: List<List<String>>) {
    SLEEP(listOf(listOf("input", "keyevent", "KEYCODE_SLEEP"))),
    REBOOT(listOf(listOf("svc", "power", "reboot"), listOf("reboot"))),
    SHUTDOWN(listOf(listOf("svc", "power", "shutdown"), listOf("reboot", "-p"))),
}
