package com.loop.launcher.status

import java.util.Locale

data class CpuTimes(val idle: Long, val total: Long)

object StatusParsers {
    private const val MIN_CELSIUS = 1
    private const val MAX_CELSIUS = 120
    private const val GIB = 1024.0 * 1024 * 1024

    /** Reads the "cpu" total line of /proc/stat; the fourth value is the idle time. */
    fun procStat(text: String): CpuTimes? {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val values = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (values.size < 4) return null
        return CpuTimes(idle = values[3], total = values.sum())
    }

    fun cpuPercent(a: CpuTimes, b: CpuTimes): Int? {
        val total = b.total - a.total
        if (total <= 0) return null
        return (100 * (total - (b.idle - a.idle)) / total).toInt().coerceIn(0, 100)
    }

    /** Sensors report thousandths of a degree or whole degrees, and some report nonsense. */
    fun thermalCelsius(values: List<String>): Int? = values
        .mapNotNull { it.trim().toIntOrNull() }
        .map { if (it >= 1000) it / 1000 else it }
        .filter { it in MIN_CELSIUS..MAX_CELSIUS }
        .maxOrNull()

    fun uptimeText(millis: Long, @Suppress("UNUSED_PARAMETER") language: String): String {
        val minutes = millis / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return if (days > 0) "$days d ${hours % 24} h" else "$hours h ${minutes % 60} min"
    }

    fun sizeText(used: Long, total: Long, language: String): String {
        val of = if (language == "pt") "de" else "of"
        return "${decimal(used / GIB, language)} $of ${decimal(total / GIB, language)} GB"
    }

    fun decimal(value: Double, language: String): String {
        val text = String.format(Locale.US, "%.1f", value)
        return if (language == "pt") text.replace('.', ',') else text
    }
}
