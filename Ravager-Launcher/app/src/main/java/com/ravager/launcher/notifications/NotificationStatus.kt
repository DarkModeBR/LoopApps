package com.ravager.launcher.notifications

/** What the notification button can do on this device right now. */
enum class NotificationStatus {
    ON, THROUGH_SHIZUKU, OFF;

    companion object {
        fun of(hasAccess: Boolean, shizukuReady: Boolean): NotificationStatus = when {
            hasAccess -> ON
            shizukuReady -> THROUGH_SHIZUKU
            else -> OFF
        }
    }
}
