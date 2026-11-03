package com.ravager.launcher.status

object StatusLines {
    /** Percent and temperature share one line; null hides the line when neither can be read. */
    fun processor(percent: Int?, celsius: Int?): String? {
        val parts = listOfNotNull(percent?.let { "$it%" }, celsius?.let { "$it °C" })
        return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
    }
}
