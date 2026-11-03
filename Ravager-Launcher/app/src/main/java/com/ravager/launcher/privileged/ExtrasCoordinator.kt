package com.ravager.launcher.privileged

/** Readings that a normal app cannot get but the ADB shell can. */
class ExtrasCoordinator(private val gateway: PrivilegedGateway) {
    suspend fun wifiName(): String? {
        if (gateway.state() != ShizukuState.READY) return null
        return successfulOutput(WIFI_INFO_LINE)?.let(WifiNameParser::parse)
            ?: successfulOutput(listOf("cmd", "wifi", "status"))?.let(WifiNameParser::parse)
    }

    suspend fun procStat(): String? {
        if (gateway.state() != ShizukuState.READY) return null
        return successfulOutput(listOf("cat", "/proc/stat"))
    }

    private companion object {
        /** The full dump is hundreds of kilobytes; only one line of it is needed. */
        val WIFI_INFO_LINE = listOf("sh", "-c", "dumpsys wifi | grep -m1 'mWifiInfo SSID'")
    }

    private suspend fun successfulOutput(argv: List<String>): String? =
        gateway.run(argv)?.takeIf { it.exitCode == 0 }?.output
}
