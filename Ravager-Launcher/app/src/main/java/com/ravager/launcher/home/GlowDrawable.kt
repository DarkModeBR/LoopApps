package com.ravager.launcher.home

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Full-screen background: a plain gradient, redrawn only when it changes. */
class GlowDrawable : Drawable() {
    private sealed interface Look {
        data class Glow(val color: Int, val base: Int) : Look
        data class Vertical(val top: Int, val bottom: Int) : Look
        data class Flat(val color: Int) : Look
    }

    private val paint = Paint()
    private var look: Look = Look.Flat(0xFF000000.toInt())

    fun showGlow(color: Int, base: Int) = show(Look.Glow(color, base))
    fun showVertical(top: Int, bottom: Int) = show(Look.Vertical(top, bottom))
    fun showFlat(color: Int) = show(Look.Flat(color))

    private fun show(next: Look) {
        if (next == look) return
        look = next
        rebuild()
    }

    override fun onBoundsChange(bounds: Rect) = rebuild()

    private fun rebuild() {
        val area = bounds
        paint.shader = when (val current = look) {
            is Look.Glow -> if (area.isEmpty) {
                null
            } else {
                RadialGradient(
                    area.width() * 0.38f,
                    area.height() * 0.42f,
                    area.width() * 0.62f,
                    intArrayOf(current.color, mix(current.color, current.base, 0.35f), current.base),
                    floatArrayOf(0f, 0.48f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            is Look.Vertical -> if (area.isEmpty) {
                null
            } else {
                LinearGradient(0f, 0f, 0f, area.height().toFloat(), current.top, current.bottom, Shader.TileMode.CLAMP)
            }
            is Look.Flat -> null
        }
        invalidateSelf()
    }

    private fun mix(a: Int, b: Int, amountOfA: Float): Int {
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca * amountOfA + cb * (1 - amountOfA)).toInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }

    override fun draw(canvas: Canvas) {
        val current = look
        if (current is Look.Flat) canvas.drawColor(current.color) else canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
