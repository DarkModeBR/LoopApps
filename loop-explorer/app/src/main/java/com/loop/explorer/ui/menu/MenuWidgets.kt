package com.loop.explorer.ui.menu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/** On/off switch drawn with the colors of the theme. */
class SwitchView(context: Context, private val square: Boolean) : View(context) {
    var trackOn = Color.WHITE
    var trackOff = Color.GRAY
    var thumb = Color.BLACK
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var position = 0f
    private var animator: ValueAnimator? = null
    private val shape = RectF()

    fun setOn(on: Boolean, animate: Boolean) {
        val target = if (on) 1f else 0f
        animator?.cancel()
        if (!animate) {
            position = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(position, target).apply {
            duration = 140
            addUpdateListener {
                position = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        val corner = if (square) 0f else h / 2
        paint.color = blend(trackOff, trackOn, position)
        shape.set(0f, 0f, w, h)
        canvas.drawRoundRect(shape, corner, corner, paint)
        val inset = h * 0.14f
        val size = h - inset * 2
        val x = inset + (w - inset * 2 - size) * position
        paint.color = thumb
        val thumbCorner = if (square) 0f else size / 2
        shape.set(x, inset, x + size, inset + size)
        canvas.drawRoundRect(shape, thumbCorner, thumbCorner, paint)
    }

    private fun blend(a: Int, b: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val ca = (a shr shift) and 0xFF
            val cb = (b shr shift) and 0xFF
            return (ca + (cb - ca) * amount).toInt() shl shift
        }
        return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
    }
}

/** Selection mark for choosing one option among several. */
class RadioView(context: Context, private val square: Boolean) : View(context) {
    var color = Color.WHITE
    var selected2 = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        val stroke = size * 0.11f
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        val half = size / 2
        if (square) {
            canvas.drawRect(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2, paint)
        } else {
            canvas.drawCircle(half, half, half - stroke / 2, paint)
        }
        if (!selected2) return
        paint.style = Paint.Style.FILL
        if (square) {
            val inset = size * 0.28f
            canvas.drawRect(inset, inset, size - inset, size - inset, paint)
        } else {
            canvas.drawCircle(half, half, size * 0.24f, paint)
        }
    }
}

/** Row of color dots that previews a theme. */
class SwatchView(context: Context, private val square: Boolean) : View(context) {
    var colors: List<Int> = emptyList()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val size = height.toFloat()
        colors.forEachIndexed { index, color ->
            paint.color = color
            val x = index * size * 0.7f
            if (square) {
                canvas.drawRect(x, 0f, x + size * 0.6f, size, paint)
            } else {
                canvas.drawCircle(x + size / 2, size / 2, size / 2, paint)
            }
        }
    }
}

/** Thin scroll position mark on the side of a list; hidden when everything fits. */
class ScrollPill(pillColor: Int, private val width: Float, private val square: Boolean) : RecyclerView.ItemDecoration() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillColor }

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val range = parent.computeVerticalScrollRange()
        val extent = parent.computeVerticalScrollExtent()
        if (range <= extent) return
        val height = parent.height.toFloat()
        val pill = (height * extent / range).coerceAtLeast(width * 6)
        val top = (height - pill) * parent.computeVerticalScrollOffset() / (range - extent)
        val right = parent.width.toFloat()
        val corner = if (square) 0f else width / 2
        canvas.drawRoundRect(RectF(right - width, top, right, top + pill), corner, corner, paint)
    }
}
