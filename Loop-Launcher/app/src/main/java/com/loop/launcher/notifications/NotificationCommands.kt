package com.loop.launcher.notifications

/** Control over real system notifications; only available where Android grants access. */
interface SystemNotifications {
    fun open(key: String): Boolean
    fun cancel(key: String): Boolean
    fun actions(key: String): List<String>
    fun runAction(key: String, index: Int): Boolean
}

/** Notifications the user cleared on devices where the launcher cannot remove them from the system. */
class DismissedNotifications(initial: Set<String>, private val save: (Set<String>) -> Unit) {
    private var signatures = initial

    fun dismiss(item: NotificationItem) {
        signatures = signatures + signature(item)
        save(signatures)
    }

    fun filter(items: List<NotificationItem>): List<NotificationItem> {
        val current = items.map(::signature).toSet()
        val kept = signatures.intersect(current)
        if (kept != signatures) {
            signatures = kept
            save(kept)
        }
        return items.filterNot { signature(it) in kept }
    }

    private fun signature(item: NotificationItem) = "${item.key}|${item.text}"
}

/**
 * What the buttons in the notification panel do. With normal access they act on the real
 * notification. Where Android blocks it, Open starts the app and Remove hides the entry here.
 */
class NotificationCommands(
    private val system: SystemNotifications?,
    private val dismissed: DismissedNotifications,
    private val launchApp: (String) -> Boolean,
    private val openThroughShell: suspend (NotificationItem) -> Boolean = { false },
) {
    suspend fun open(item: NotificationItem): Boolean =
        system?.open(item.key) == true || openThroughShell(item) || launchApp(item.pkg)

    fun remove(item: NotificationItem) {
        if (system?.cancel(item.key) != true) dismissed.dismiss(item)
    }

    fun clearAll(items: List<NotificationItem>) = items.filterNot { it.ongoing }.forEach(::remove)

    fun actions(item: NotificationItem): List<String> = system?.actions(item.key).orEmpty()

    fun runAction(item: NotificationItem, index: Int): Boolean = system?.runAction(item.key, index) == true
}
