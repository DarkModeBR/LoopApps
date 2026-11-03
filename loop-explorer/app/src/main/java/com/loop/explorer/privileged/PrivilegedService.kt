package com.loop.explorer.privileged

import androidx.annotation.Keep
import kotlin.system.exitProcess

/** Runs in a separate process started by Shizuku, with the permissions of the ADB shell. */
class PrivilegedService @Keep constructor() : IPrivilegedService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(argv: Array<String>): String {
        val process = ProcessBuilder(*argv).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return "${process.waitFor()}\n$output"
    }
}
