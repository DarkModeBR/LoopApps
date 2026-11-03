package com.ravager.launcher.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.CopyOnWriteArrayList

data class NotificationItem(
    val key: String,
    val app: String,
    val text: String,
    val postedAt: Long,
    val ongoing: Boolean,
    val pkg: String = "",
    /** Android's id for what pressing it does, known when it was read through Shizuku. */
    val pressRecord: String? = null,
)

/** Current notifications, kept in memory; the system resends them when the service reconnects. */
object NotificationStore {
    private val items = LinkedHashMap<String, NotificationItem>()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Synchronized
    fun replaceAll(newItems: List<NotificationItem>) {
        items.clear()
        newItems.forEach { items[it.key] = it }
        notifyListeners()
    }

    @Synchronized
    fun post(item: NotificationItem) {
        items[item.key] = item
        notifyListeners()
    }

    @Synchronized
    fun remove(key: String) {
        if (items.remove(key) != null) notifyListeners()
    }

    @Synchronized
    fun visible(): List<NotificationItem> = items.values.filterNot { it.ongoing }.sortedByDescending { it.postedAt }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Tells the screens that what they show changed. */
    fun notifyListeners() = listeners.forEach { it() }
}

fun hasNotificationAccess(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
