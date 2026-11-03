package com.loop.launcher.weather

import com.loop.launcher.settings.TempUnit

data class Coordinates(val latitude: Double, val longitude: Double)

data class CurrentWeather(val temperature: Double, val code: Int, val fetchedAtMillis: Long)

/** [manual] is null when the location comes from the IP address. */
data class WeatherQuery(val manual: Coordinates?, val unit: TempUnit)

const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
