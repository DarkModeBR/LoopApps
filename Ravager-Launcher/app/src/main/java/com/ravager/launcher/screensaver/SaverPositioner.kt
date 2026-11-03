package com.ravager.launcher.screensaver

import kotlin.math.hypot
import kotlin.random.Random

object SaverPositioner {
    private const val ATTEMPTS = 24
    private const val MIN_MOVE = 0.2

    /**
     * A random top-left corner that keeps the whole view on screen, far from [previous] so
     * that no pixel stays lit in the same place for long. That is what prevents burn-in.
     */
    fun next(
        random: Random,
        screenW: Int,
        screenH: Int,
        viewW: Int,
        viewH: Int,
        previous: Pair<Int, Int>,
    ): Pair<Int, Int> {
        val maxX = (screenW - viewW).coerceAtLeast(0)
        val maxY = (screenH - viewH).coerceAtLeast(0)
        val wanted = screenW * MIN_MOVE
        var best = 0 to 0
        var bestDistance = -1.0
        repeat(ATTEMPTS) {
            val candidate = random.nextInt(maxX + 1) to random.nextInt(maxY + 1)
            val distance = hypot((candidate.first - previous.first).toDouble(), (candidate.second - previous.second).toDouble())
            if (distance >= wanted) return candidate
            if (distance > bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }
}
