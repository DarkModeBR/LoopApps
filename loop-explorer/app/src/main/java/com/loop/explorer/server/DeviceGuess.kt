package com.loop.explorer.server

import com.loop.explorer.R

enum class DeviceType(val nameRes: Int, val iconRes: Int) {
    PHONE(R.string.device_phone, R.drawable.ic_phone),
    TABLET(R.string.device_tablet, R.drawable.ic_tablet),
    COMPUTER(R.string.device_computer, R.drawable.ic_computer),
    TV(R.string.device_tv, R.drawable.ic_tv),
    OTHER(R.string.device_other, R.drawable.ic_globe),
}

/** What can be told from the browser: the kind of device, its system, the browser and sometimes the model. */
data class DeviceGuess(val type: DeviceType, val system: String?, val browser: String?, val model: String?)

/**
 * Reads the browser's User-Agent. Browsers hide part of it on purpose: Chrome on Android always says
 * "Android 10; K", and Windows 11 says "Windows NT 10.0", so those versions are left out instead
 * of being shown wrong.
 */
object DeviceGuesser {
    fun guess(userAgent: String, touchPoints: Int): DeviceGuess {
        val ua = userAgent.lowercase()
        val browser = browser(ua)
        val tv = TV_HINTS.any { it in ua }
        return when {
            ua.isBlank() -> DeviceGuess(DeviceType.OTHER, null, null, null)
            "xbox" in ua -> DeviceGuess(DeviceType.OTHER, "Xbox", browser, null)
            "playstation" in ua -> DeviceGuess(DeviceType.OTHER, "PlayStation", browser, null)
            "iphone" in ua || "ipod" in ua -> DeviceGuess(DeviceType.PHONE, apple("iOS", ua), browser, "iPhone")
            "ipad" in ua -> DeviceGuess(DeviceType.TABLET, apple("iPadOS", ua), browser, "iPad")
            "macintosh" in ua && touchPoints > 1 -> DeviceGuess(DeviceType.TABLET, "iPadOS", browser, "iPad")
            "macintosh" in ua -> DeviceGuess(DeviceType.COMPUTER, "macOS", browser, "Mac")
            "android" in ua -> android(userAgent, ua, browser, tv)
            "windows" in ua -> DeviceGuess(DeviceType.COMPUTER, "Windows", browser, null)
            "cros" in ua -> DeviceGuess(DeviceType.COMPUTER, "ChromeOS", browser, null)
            "tizen" in ua -> DeviceGuess(if (tv) DeviceType.TV else DeviceType.OTHER, "Tizen", browser, null)
            "web0s" in ua || "webos" in ua -> DeviceGuess(DeviceType.TV, "webOS", browser, null)
            "linux" in ua -> DeviceGuess(if (tv) DeviceType.TV else DeviceType.COMPUTER, "Linux", browser, null)
            else -> DeviceGuess(if (tv) DeviceType.TV else DeviceType.OTHER, null, browser, null)
        }
    }

    private fun android(original: String, ua: String, browser: String?, tv: Boolean): DeviceGuess {
        val match = Regex("""Android\s+([\d.]+)(?:;\s*([^;)]+))?""", RegexOption.IGNORE_CASE).find(original)
        val version = match?.groupValues?.getOrNull(1).orEmpty()
        val model = match?.groupValues?.getOrNull(2)?.substringBefore(" Build")?.trim()
            ?.takeIf { it.isNotEmpty() && it != "K" && it.lowercase() != "wv" && !it.startsWith("Mobile") }
        // "Android 10; K" is Chrome's frozen text, not the real version.
        val system = if (version.isEmpty() || model == null && version == "10") "Android" else "Android $version"
        val type = when {
            tv -> DeviceType.TV
            "mobile" in ua -> DeviceType.PHONE
            else -> DeviceType.TABLET
        }
        return DeviceGuess(type, system, browser, model)
    }

    private fun apple(name: String, ua: String): String {
        val version = Regex("""os (\d+)[_.](\d+)""").find(ua)?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }
        return if (version == null) name else "$name $version"
    }

    private fun browser(ua: String): String? = when {
        "edg/" in ua || "edga/" in ua || "edgios/" in ua -> "Edge"
        "opr/" in ua || "opera" in ua -> "Opera"
        "samsungbrowser" in ua -> "Samsung Internet"
        "firefox" in ua || "fxios" in ua -> "Firefox"
        "crios" in ua || "chrome" in ua || "chromium" in ua -> "Chrome"
        "safari" in ua -> "Safari"
        ua.startsWith("curl") -> "curl"
        ua.startsWith("wget") -> "Wget"
        "python" in ua -> "Python"
        else -> null
    }

    private val TV_HINTS = listOf(
        "smart-tv", "smarttv", "googletv", "google tv", "android tv", "androidtv", "bravia", "hbbtv",
        "web0s", "netcast", "crkey", "aftb", "aftt", "aftm", "afts", "aftka", "aftmm", "aftss", "tv bro", "; tv",
    )
}
