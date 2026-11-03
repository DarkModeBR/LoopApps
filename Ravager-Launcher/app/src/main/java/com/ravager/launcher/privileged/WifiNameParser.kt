package com.ravager.launcher.privileged

object WifiNameParser {
    private val DUMPSYS = Regex("""mWifiInfo SSID: (.*?), BSSID""")
    private val CMD_WIFI = Regex("""Wifi is connected to "(.*)"""")

    /** Understands the output of both `dumpsys wifi` and `cmd wifi status`. */
    fun parse(output: String): String? {
        val raw = DUMPSYS.find(output)?.groupValues?.get(1)
            ?: CMD_WIFI.find(output)?.groupValues?.get(1)
            ?: return null
        val name = raw.trim().removeSurrounding("\"")
        return name.takeUnless { it.isBlank() || it == "<unknown ssid>" }
    }
}
