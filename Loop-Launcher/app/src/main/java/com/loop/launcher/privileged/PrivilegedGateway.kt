package com.loop.launcher.privileged

enum class ShizukuState { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, READY, UNSUPPORTED }

data class CommandResult(val exitCode: Int, val output: String)

interface PrivilegedGateway {
    fun state(): ShizukuState
    suspend fun requestPermission(): Boolean

    /** Returns null when Shizuku is not ready or stops in the middle of the call. */
    suspend fun run(argv: List<String>): CommandResult?

    /** Stops the helper process until the next command needs it. */
    fun release()
}
