package com.loop.launcher.home

import com.loop.launcher.R

/** Buttons of the top bar the user can show, hide and reorder. */
enum class TopButton(val nameRes: Int, val iconRes: Int) {
    NOTIFICATIONS(R.string.topbar_notifications, R.drawable.ic_bell),
    USB(R.string.topbar_usb, R.drawable.ic_usb),
    NETWORK(R.string.topbar_network, R.drawable.ic_wifi),
    STATUS(R.string.chip_status, R.drawable.ic_status),
    POWER(R.string.chip_power, R.drawable.ic_power),
    SETTINGS(R.string.chip_settings, R.drawable.ic_settings),
}

data class TopBarConfig(val order: List<TopButton>, val hidden: Set<TopButton>) {
    fun visible(): List<TopButton> = order.filterNot { it in hidden }

    fun withVisible(button: TopButton, visible: Boolean) =
        copy(hidden = if (visible) hidden - button else hidden + button)

    fun moved(button: TopButton, delta: Int): TopBarConfig {
        val from = order.indexOf(button)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        val next = order.toMutableList()
        next.add(to, next.removeAt(from))
        return copy(order = next)
    }

    companion object {
        val DEFAULT = TopBarConfig(TopButton.entries, emptySet())
        private const val HIDDEN_MARK = "-"

        fun encode(config: TopBarConfig): String =
            config.order.joinToString(",") { (if (it in config.hidden) HIDDEN_MARK else "") + it.name }

        /** Buttons added by a newer version appear at the end, visible. */
        fun decode(text: String?): TopBarConfig {
            val saved = text.orEmpty().split(',').mapNotNull { item ->
                val button = TopButton.entries.firstOrNull { it.name == item.removePrefix(HIDDEN_MARK) } ?: return@mapNotNull null
                button to item.startsWith(HIDDEN_MARK)
            }
            val order = saved.map { it.first }.distinct()
            return TopBarConfig(order + (TopButton.entries - order.toSet()), saved.filter { it.second }.map { it.first }.toSet())
        }
    }
}
