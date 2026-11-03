package com.ravager.launcher.weather

import com.ravager.launcher.net.HttpGet
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

class WeatherRepository(
    private val http: HttpGet,
    private val cacheFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var memory: Saved? = null

    private data class Saved(val weather: CurrentWeather, val queryKey: String)

    /** Returns fresh data when due, and the last saved value when anything goes wrong. */
    suspend fun refreshIfDue(query: WeatherQuery): CurrentWeather? = withContext(Dispatchers.IO) {
        val saved = saved()
        val key = query.toString()
        val age = saved?.let { clock() - it.weather.fetchedAtMillis }
        if (saved != null && saved.queryKey == key && age != null && age in 0 until REFRESH_INTERVAL_MS) {
            return@withContext saved.weather
        }
        fetch(query)?.also { store(Saved(it, key)) } ?: saved?.weather
    }

    fun cached(): CurrentWeather? = saved()?.weather

    private fun fetch(query: WeatherQuery): CurrentWeather? = try {
        val at = query.manual ?: locate()
        at?.let { coordinates ->
            val response = http.get(OpenMeteoClient.forecastUrl(coordinates, query.unit))
            if (response.code in 200..299) OpenMeteoClient.parseCurrent(response.body, clock()) else null
        }
    } catch (_: IOException) {
        null
    }

    private fun locate(): Coordinates? {
        val response = http.get(IpLocator.URL)
        return if (response.code in 200..299) IpLocator.parse(response.body) else null
    }

    private fun saved(): Saved? {
        memory?.let { return it }
        return try {
            val root = JSONObject(cacheFile.readText())
            Saved(
                CurrentWeather(root.getDouble("temperature"), root.getInt("code"), root.getLong("at")),
                root.getString("query"),
            ).also { memory = it }
        } catch (_: IOException) {
            null
        } catch (_: JSONException) {
            null
        }
    }

    private fun store(saved: Saved) {
        memory = saved
        try {
            cacheFile.writeText(
                JSONObject()
                    .put("temperature", saved.weather.temperature)
                    .put("code", saved.weather.code)
                    .put("at", saved.weather.fetchedAtMillis)
                    .put("query", saved.queryKey)
                    .toString(),
            )
        } catch (_: IOException) {
            // The value stays in memory until the launcher restarts.
        }
    }
}
