package com.ravager.launcher.privileged

object LossNotice {
    fun shouldWarn(hadIt: Boolean, hasItNow: Boolean): Boolean = hadIt && !hasItNow
}
