package com.ravager.launcher.weather

import com.ravager.launcher.settings.ManualCity
import com.ravager.launcher.settings.TempUnit
import java.net.URLEncoder
import org.json.JSONException
import org.json.JSONObject

object OpenMeteoClient {
    fun forecastUrl(at: Coordinates, unit: TempUnit): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${at.latitude}&longitude=${at.longitude}" +
            "&current=temperature_2m,weather_code&temperature_unit=" +
            if (unit == TempUnit.F) "fahrenheit" else "celsius"

    fun cityUrl(name: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name, "UTF-8")

    fun parseCurrent(text: String, fetchedAtMillis: Long): CurrentWeather? = try {
        val current = JSONObject(text).getJSONObject("current")
        CurrentWeather(current.getDouble("temperature_2m"), current.getInt("weather_code"), fetchedAtMillis)
    } catch (_: JSONException) {
        null
    }

    fun parseCity(text: String): ManualCity? = try {
        val first = JSONObject(text).optJSONArray("results")?.optJSONObject(0)
        first?.let { ManualCity(it.getString("name"), it.getDouble("latitude"), it.getDouble("longitude")) }
    } catch (_: JSONException) {
        null
    }
}
