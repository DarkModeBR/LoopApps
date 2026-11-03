package com.ravager.launcher.weather

import android.content.Context
import com.ravager.launcher.R
import com.ravager.launcher.ui.BaseActivity
import java.util.Locale
import kotlin.math.roundToInt

enum class ConditionKey { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

object WeatherText {
    /** Maps a WMO weather code; an unknown code shows the temperature alone. */
    fun condition(wmoCode: Int): ConditionKey? = when (wmoCode) {
        0 -> ConditionKey.CLEAR
        1, 2 -> ConditionKey.PARTLY_CLOUDY
        3 -> ConditionKey.CLOUDY
        45, 48 -> ConditionKey.FOG
        in 51..57 -> ConditionKey.DRIZZLE
        in 61..67, in 80..82 -> ConditionKey.RAIN
        in 71..77, 85, 86 -> ConditionKey.SNOW
        in 95..99 -> ConditionKey.THUNDER
        else -> null
    }

    /** The line shown on screen, for example "24° · PARTLY CLOUDY". */
    fun display(context: Context, current: CurrentWeather): String {
        val condition = condition(current.code)?.let { context.getString(stringRes(it)) }
        return listOfNotNull(temperature(current.temperature), condition)
            .joinToString(" · ")
            .uppercase(Locale.forLanguageTag(BaseActivity.currentLanguage(context)))
    }

    fun temperature(value: Double): String = "${value.roundToInt()}°"

    fun stringRes(key: ConditionKey): Int = when (key) {
        ConditionKey.CLEAR -> R.string.weather_clear
        ConditionKey.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        ConditionKey.CLOUDY -> R.string.weather_cloudy
        ConditionKey.FOG -> R.string.weather_fog
        ConditionKey.DRIZZLE -> R.string.weather_drizzle
        ConditionKey.RAIN -> R.string.weather_rain
        ConditionKey.SNOW -> R.string.weather_snow
        ConditionKey.THUNDER -> R.string.weather_thunder
    }
}
