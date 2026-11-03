package com.loop.launcher.weather

import org.json.JSONException
import org.json.JSONObject

/** The only place that knows which IP location service is used, so it is easy to replace. */
object IpLocator {
    const val URL = "https://get.geojs.io/v1/ip/geo.json"

    fun parse(text: String): Coordinates? = try {
        val root = JSONObject(text)
        val latitude = root.optString("latitude").toDoubleOrNull()
        val longitude = root.optString("longitude").toDoubleOrNull()
        if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
    } catch (_: JSONException) {
        null
    }
}
