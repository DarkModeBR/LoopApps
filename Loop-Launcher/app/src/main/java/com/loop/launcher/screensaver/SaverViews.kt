package com.loop.launcher.screensaver

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import kotlin.random.Random

object SaverViews {
    /** The preview is this fraction of the screen wide, so the clock text scales with it. */
    const val PREVIEW_SCALE = 0.56f

    fun create(context: Context, style: SaverStyle, preview: Boolean = false): View = when (style) {
        SaverStyle.CLOCK -> SaverView(context, preview)
        SaverStyle.DVD -> DvdSaverView(context)
        SaverStyle.RAIN -> RainSaverView(context)
    }
}

/** The logo crosses the screen and changes color at each edge, like old DVD players. */
class DvdSaverView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var logo: Bitmap? = null
    private var bounce: Bounce? = null
    private var lastFrame = 0L
    private var colorIndex = Random.nextInt(COLORS.size)

    init {
        setBackgroundColor(Color.BLACK)
        paintColor()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        val logoWidth = (w * LOGO_WIDTH).toInt()
        val logoHeight = logoWidth * 11 / 20
        logo?.recycle()
        logo = DvdLogo.draw(logoWidth, logoHeight)
        val speed = w * SPEED
        bounce = Bounce(
            Random.nextInt((w - logoWidth).coerceAtLeast(1)).toFloat(),
            Random.nextInt((h - logoHeight).coerceAtLeast(1)).toFloat(),
            speed,
            speed * 0.72f,
        )
        lastFrame = 0L
    }

    override fun onDraw(canvas: Canvas) {
        val image = logo ?: return
        val moving = bounce ?: return
        val now = SystemClock.uptimeMillis()
        val seconds = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(MAX_STEP_SECONDS)
        lastFrame = now
        if (moving.step(seconds, (width - image.width).toFloat(), (height - image.height).toFloat())) nextColor()
        canvas.drawBitmap(image, moving.x, moving.y, paint)
        // Stops by itself while hidden, because a hidden view is not drawn.
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        logo?.recycle()
        logo = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrame = 0L
        if (logo == null && width > 0) onSizeChanged(width, height, 0, 0)
    }

    private fun nextColor() {
        colorIndex = (colorIndex + 1 + Random.nextInt(COLORS.size - 1)) % COLORS.size
        paintColor()
    }

    private fun paintColor() {
        paint.colorFilter = PorterDuffColorFilter(COLORS[colorIndex], PorterDuff.Mode.SRC_IN)
    }

    private companion object {
        const val LOGO_WIDTH = 0.2f
        const val SPEED = 0.09f
        const val MAX_STEP_SECONDS = 0.1f
        val COLORS = intArrayOf(
            0xFFFF4B4B.toInt(),
            0xFFFFA726.toInt(),
            0xFFFFE34D.toInt(),
            0xFF5CE65C.toInt(),
            0xFF35D6F5.toInt(),
            0xFF5C8DFF.toInt(),
            0xFFC77DFF.toInt(),
            0xFFFF7AC6.toInt(),
        )
    }
}

/** The logo in white, in its own file section so it is easy to swap before publishing the project. */
object DvdLogo {
    fun draw(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        }
        fit(paint, "DVD", width * 0.98f, height * 0.62f)
        val bounds = Rect()
        paint.getTextBounds("DVD", 0, 3, bounds)
        canvas.drawText("DVD", (width - paint.measureText("DVD")) / 2f, height * 0.62f - bounds.bottom, paint)

        val disc = RectF(width * 0.04f, height * 0.68f, width * 0.96f, height * 0.99f)
        canvas.drawOval(disc, paint)
        val cutout = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.22f
        }
        fit(cutout, "VIDEO", width * 0.46f, disc.height() * 0.6f)
        cutout.getTextBounds("VIDEO", 0, 5, bounds)
        canvas.drawText(
            "VIDEO",
            disc.centerX() - cutout.measureText("VIDEO") / 2f,
            disc.centerY() - bounds.exactCenterY(),
            cutout,
        )
        return bitmap
    }

    /** Stretches the text sideways to fill [width], which gives the wide look of the logo. */
    private fun fit(paint: Paint, text: String, width: Float, height: Float) {
        val bounds = Rect()
        paint.textSize = 100f
        paint.getTextBounds(text, 0, text.length, bounds)
        paint.textSize = 100f * height / bounds.height()
        paint.textScaleX = 1f
        paint.textScaleX = width / paint.measureText(text)
    }
}

/** Columns of green letters falling on black. Everything keeps moving, so nothing marks the TV. */
class RainSaverView(context: Context) : View(context) {
    private val random = Random.Default
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private var rain: RainColumns? = null
    private var heads = IntArray(0)
    private var tails = IntArray(0)
    private var letters = CharArray(0)
    private var rows = 0
    private var cell = 1f
    private var alphabet = CharArray(0)
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, STEP_MS)
        }
    }

    init {
        setBackgroundColor(Color.BLACK)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        cell = w / COLUMNS.toFloat()
        rows = (h / cell).toInt() + 1
        paint.textSize = cell * 0.95f
        paint.textAlign = Paint.Align.CENTER
        alphabet = alphabet().toCharArray()
        heads = IntArray(COLUMNS)
        tails = IntArray(COLUMNS) { MIN_TAIL + random.nextInt(MAX_TAIL - MIN_TAIL) }
        letters = CharArray(COLUMNS * rows) { alphabet[random.nextInt(alphabet.size)] }
        rain = RainColumns(COLUMNS, rows, random, MAX_TAIL)
        repeat(rows) { advance() }
    }

    private fun alphabet(): String {
        val katakana = (0xFF66..0xFF9D).map { it.toChar() }.filter { paint.hasGlyph(it.toString()) }
        return katakana.joinToString("") + "0123456789"
    }

    private fun advance() {
        val columns = rain ?: return
        for (column in 0 until columns.columns) heads[column] = columns.advance(column)
        repeat(LETTER_CHANGES_PER_STEP) {
            letters[random.nextInt(letters.size)] = alphabet[random.nextInt(alphabet.size)]
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (rain == null) return
        for (column in heads.indices) {
            val head = heads[column]
            val tail = tails[column]
            val x = (column + 0.5f) * cell
            for (back in 0 until tail) {
                val row = head - back
                if (row !in 0 until rows) continue
                paint.color = if (back == 0) HEAD_COLOR else {
                    val fade = 1f - back.toFloat() / tail
                    ((255 * fade * fade).toInt() shl 24) or TRAIL_RGB
                }
                canvas.drawText(letters, column * rows + row, 1, x, (row + 1) * cell, paint)
            }
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        running = visibility == VISIBLE
        removeCallbacks(tick)
        if (running) post(tick)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        removeCallbacks(tick)
    }

    private companion object {
        const val COLUMNS = 52
        const val STEP_MS = 75L
        const val MIN_TAIL = 8
        const val MAX_TAIL = 22
        const val LETTER_CHANGES_PER_STEP = 24
        const val HEAD_COLOR = 0xFFD9FFE3.toInt()
        const val TRAIL_RGB = 0x00E676
    }
}
