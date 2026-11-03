package com.loop.explorer.server

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * The devices using the web page. The page says hello every [HELLO_SECONDS] seconds, so a device
 * that stops calling for [WINDOW_MS] has closed the page or left the network.
 */
object Clients {
    class Client(val ip: String) {
        @Volatile var since = 0L
        @Volatile var lastSeen = 0L
        @Volatile var userAgent = ""

        /** What the page tells about the screen; a Mac with touch is an iPad asking for the desktop site. */
        @Volatile var touchPoints = -1
        @Volatile var screenWidth = 0
        @Volatile var screenHeight = 0
    }

    private val all = ConcurrentHashMap<String, Client>()

    /** Called for every request, so a device appears even before the page says hello. */
    fun seen(ip: String?, userAgent: String?) {
        if (ip.isNullOrEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val client = all.getOrPut(ip) { Client(ip) }
        synchronized(client) {
            if (client.lastSeen == 0L || now - client.lastSeen > WINDOW_MS) client.since = now
            client.lastSeen = now
            if (!userAgent.isNullOrBlank()) client.userAgent = userAgent.take(MAX_AGENT)
        }
    }

    fun hello(ip: String?, body: JSONObject) {
        val client = ip?.let { all[it] } ?: return
        client.touchPoints = body.optInt("touch", -1)
        client.screenWidth = body.optInt("w", 0)
        client.screenHeight = body.optInt("h", 0)
    }

    /** Devices that called recently, the oldest connection first. */
    fun active(): List<Client> {
        val now = SystemClock.elapsedRealtime()
        return all.values.filter { now - it.lastSeen <= WINDOW_MS }.sortedBy { it.since }
    }

    fun clear() = all.clear()

    const val HELLO_SECONDS = 15
    private const val WINDOW_MS = 50_000L
    private const val MAX_AGENT = 400
}
