package com.ravager.launcher.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.lang.ref.WeakReference

class RavagerNotificationListener : NotificationListenerService(), SystemNotifications {
    override fun onListenerConnected() {
        active = WeakReference(this)
        val current = runCatching { activeNotifications }.getOrNull().orEmpty().map(::toItem)
        NotificationStore.replaceAll(current)
    }

    override fun onListenerDisconnected() {
        active = null
        NotificationStore.replaceAll(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = NotificationStore.post(toItem(sbn))

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotificationStore.remove(sbn.key)

    private fun toItem(sbn: StatusBarNotification): NotificationItem {
        val extras = sbn.notification.extras
        val text = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TITLE),
            extras.getCharSequence(Notification.EXTRA_TEXT),
        ).joinToString(" · ")
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        return NotificationItem(sbn.key, app, text, sbn.postTime, !sbn.isClearable, sbn.packageName)
    }

    private fun find(key: String): StatusBarNotification? =
        runCatching { activeNotifications }.getOrNull()?.firstOrNull { it.key == key }

    /** Actions that need typed text cannot be used with a remote, so they are left out. */
    private fun usableActions(key: String): List<Notification.Action> =
        find(key)?.notification?.actions.orEmpty().filter { it.remoteInputs.isNullOrEmpty() }

    override fun open(key: String): Boolean {
        val sbn = find(key) ?: return false
        val intent = sbn.notification.contentIntent ?: return false
        return runCatching {
            intent.send()
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) cancelNotification(key)
        }.isSuccess
    }

    override fun cancel(key: String): Boolean = runCatching { cancelNotification(key) }.isSuccess

    override fun actions(key: String): List<String> = usableActions(key).map { it.title.toString() }

    override fun runAction(key: String, index: Int): Boolean {
        val action = usableActions(key).getOrNull(index) ?: return false
        return runCatching { action.actionIntent.send() }.isSuccess
    }

    companion object {
        private var active: WeakReference<RavagerNotificationListener>? = null

        /** Null where Android has not granted notification access. */
        fun current(): SystemNotifications? = active?.get()
    }
}
