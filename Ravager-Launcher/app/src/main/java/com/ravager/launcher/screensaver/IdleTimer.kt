package com.ravager.launcher.screensaver

/** A timeout of 0 turns the screensaver off. */
class IdleTimer(private val timeoutMs: Long, private val clock: () -> Long) {
    private var lastActivity = clock()

    fun onActivity() {
        lastActivity = clock()
    }

    fun shouldStart(): Boolean = timeoutMs > 0 && clock() - lastActivity >= timeoutMs
}
