package com.ravager.launcher.home

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ClockFormatter {
    fun time(hour: Int, minute: Int, is24h: Boolean): String {
        val minutes = minute.toString().padStart(2, '0')
        if (is24h) return "${hour.toString().padStart(2, '0')}:$minutes"
        val hour12 = if (hour % 12 == 0) 12 else hour % 12
        return "$hour12:$minutes ${if (hour < 12) "AM" else "PM"}"
    }

    fun date(dateMillis: Long, zone: TimeZone, language: String): String {
        val locale = Locale.forLanguageTag(language)
        fun field(pattern: String) = SimpleDateFormat(pattern, locale)
            .apply { timeZone = zone }
            .format(Date(dateMillis))
            .uppercase(locale)
        return if (language == "pt") {
            "${field("EEEE")} · ${field("d")} DE ${field("MMMM")}"
        } else {
            "${field("EEEE")} · ${field("MMMM")} ${field("d")}"
        }
    }
}
