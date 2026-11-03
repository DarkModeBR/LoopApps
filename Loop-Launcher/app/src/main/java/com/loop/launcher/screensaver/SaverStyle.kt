package com.loop.launcher.screensaver

import com.loop.launcher.R
import kotlin.math.abs
import kotlin.random.Random

/** The screensaver models. To add one, add it here and in [SaverViews]. */
enum class SaverStyle(val nameRes: Int) {
    CLOCK(R.string.saver_style_clock),
    DVD(R.string.saver_style_dvd),
    RAIN(R.string.saver_style_rain);

    companion object {
        fun fromSaved(name: String?): SaverStyle = entries.firstOrNull { it.name == name } ?: CLOCK
    }
}

/** Something that moves in a straight line and turns back at the edges. */
class Bounce(var x: Float, var y: Float, var vx: Float, var vy: Float) {
    /** Moves by [seconds] inside 0..[maxX] and 0..[maxY]. True when it touched an edge. */
    fun step(seconds: Float, maxX: Float, maxY: Float): Boolean {
        x += vx * seconds
        y += vy * seconds
        var hit = false
        if (x < 0f) {
            x = -x
            vx = abs(vx)
            hit = true
        } else if (x > maxX) {
            x = 2 * maxX - x
            vx = -abs(vx)
            hit = true
        }
        if (y < 0f) {
            y = -y
            vy = abs(vy)
            hit = true
        } else if (y > maxY) {
            y = 2 * maxY - y
            vy = -abs(vy)
            hit = true
        }
        // After a long pause one reflection is not enough to get back on screen.
        x = x.coerceIn(0f, maxX.coerceAtLeast(0f))
        y = y.coerceIn(0f, maxY.coerceAtLeast(0f))
        return hit
    }
}

/** Where the next letter of each falling column goes. */
class RainColumns(count: Int, private val rows: Int, private val random: Random, private val tail: Int = 0) {
    private val heads = IntArray(count) { -random.nextInt(rows.coerceAtLeast(1)) }

    val columns get() = heads.size

    /** The row of the first letter of [column] after falling once; it can be off the screen. */
    fun advance(column: Int): Int {
        var head = heads[column] + 1
        // A column waits a random time after its whole tail leaves the screen.
        if (head >= rows + tail && random.nextFloat() < RESTART_CHANCE) head = -random.nextInt((rows / 2).coerceAtLeast(1))
        heads[column] = head
        return head
    }

    private companion object {
        const val RESTART_CHANCE = 0.06f
    }
}
