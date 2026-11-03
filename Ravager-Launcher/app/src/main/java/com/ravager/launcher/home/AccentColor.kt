package com.ravager.launcher.home

object AccentColor {
    const val NEUTRAL = 0xFF2B3140.toInt()

    /** Average of the most common colorful shade; black, white and gray pixels do not count. */
    fun dominant(pixels: IntArray): Int {
        val buckets = HashMap<Int, IntArray>()
        for (pixel in pixels) {
            if (pixel ushr 24 < 128) continue
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            if (max < 40 || max == 0 || (max - min) * 100 / max < 25) continue
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            val bucket = buckets.getOrPut(key) { IntArray(4) }
            bucket[0]++
            bucket[1] += r
            bucket[2] += g
            bucket[3] += b
        }
        val best = buckets.values.maxByOrNull { it[0] } ?: return NEUTRAL
        val n = best[0]
        return (0xFF shl 24) or ((best[1] / n) shl 16) or ((best[2] / n) shl 8) or (best[3] / n)
    }
}
