package com.loop.launcher.system

enum class NetworkKind { WIFI, ETHERNET, OTHER, NONE }

data class ConnectionState(val kind: NetworkKind, val ssid: String? = null)

data class ConnectionLabels(val wifi: String, val cable: String, val other: String, val none: String)

object ConnectionLabel {
    fun text(state: ConnectionState, labels: ConnectionLabels): String = when (state.kind) {
        NetworkKind.WIFI -> state.ssid?.takeUnless { it.isBlank() || it == "<unknown ssid>" } ?: labels.wifi
        NetworkKind.ETHERNET -> labels.cable
        NetworkKind.OTHER -> labels.other
        NetworkKind.NONE -> labels.none
    }
}
