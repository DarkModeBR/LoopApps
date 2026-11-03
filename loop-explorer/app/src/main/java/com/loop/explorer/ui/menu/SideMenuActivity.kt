package com.loop.explorer.ui.menu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.theme.ThemeDefinition
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.face

/**
 * Menu shown as a window in the middle of the screen, over the files. When a page has more than
 * fits, the window takes the whole screen. The name is kept from Loop Launcher, where it slides in
 * from the side; each subclass only says which pages it has.
 */
abstract class SideMenuActivity : BaseActivity() {
    protected lateinit var settings: AppSettings
    protected var style: ThemeDefinition = Themes.default
    protected var unit = 0f
    private lateinit var stack: MenuStack<MenuPage>
    private lateinit var sheet: LinearLayout
    private lateinit var sheetShape: GradientDrawable
    private lateinit var scrim: View
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var headerIcon: ImageView
    private lateinit var list: RecyclerView
    private lateinit var adapter: MenuAdapter
    private var closing = false
    private var started = false

    /** Width of the window, as a percentage of the screen. */
    protected open val sheetWidth = 50f

    /** Pages that are a screen of their own, like the HTTP server, always take the whole screen. */
    protected open val fullScreen = false

    abstract fun rootPage(): MenuPage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        style = Themes.byId(settings.themeId)
        unit = resources.displayMetrics.widthPixels / 100f
        buildSheet()
        stack = MenuStack(rootPage())
        showPage(animate = false)
        if (!intent.getBooleanExtra(EXTRA_SKIP_ENTRANCE, false)) appear()
        started = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) refresh()
    }

    private fun buildSheet() {
        title = TextView(this).apply {
            setTextColor(style.text)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 2f)
            typeface = style.face(FontWeight.MEDIUM)
            maxLines = 2
        }
        subtitle = TextView(this).apply {
            setTextColor(style.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.1f)
            typeface = style.face()
            maxLines = 3
        }
        adapter = MenuAdapter(style, unit) { row ->
            stack.rememberRow(row)
            when (val focused = adapter.rows.getOrNull(row)) {
                is MenuRow.Option -> focused.onFocus?.invoke()
                is MenuRow.Action -> focused.onFocus?.invoke()
                else -> Unit
            }
        }
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SideMenuActivity.adapter
            itemAnimator = null
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding((unit * 0.9f).toInt(), (unit * 0.3f).toInt(), (unit * 1.3f).toInt(), (unit * 1.4f).toInt())
            addItemDecoration(ScrollPill(style.muted, unit * 0.22f, false))
        }
        sheetShape = GradientDrawable().apply {
            setColor(style.surface)
            cornerRadius = unit * CORNER
        }
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = sheetShape
            elevation = unit
            clipToOutline = true
            val iconSize = (unit * HEADER_ICON).toInt()
            headerIcon = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                visibility = View.GONE
            }
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(subtitle)
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((unit * 2f).toInt(), (unit * 1.8f).toInt(), (unit * 1.8f).toInt(), (unit * 0.9f).toInt())
                addView(headerIcon, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = unit.toInt() })
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(list, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        scrim = View(this).apply { setBackgroundColor(0xB3000000.toInt()) }
        val root = ModalLayout(this, sheet, (unit * sheetWidth).toInt(), { fullScreen }) { full ->
            sheetShape.cornerRadius = if (full) 0f else unit * CORNER
        }
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(sheet, FrameLayout.LayoutParams(WRAP, WRAP))
        setContentView(root)
    }

    private fun appear() {
        sheet.alpha = 0f
        sheet.scaleX = APPEAR_SCALE
        sheet.scaleY = APPEAR_SCALE
        scrim.alpha = 0f
        sheet.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(ANIMATION_MS).start()
        scrim.animate().alpha(1f).setDuration(ANIMATION_MS).start()
    }

    /** Goes one page deeper. */
    fun open(page: MenuPage, row: Int = 0, animate: Boolean = true) {
        stack.open(page)
        stack.rememberRow(row)
        showPage(animate)
    }

    /** Swaps the current page, keeping what Back returns to. */
    fun replacePage(page: MenuPage) {
        stack.replace(page)
        showPage(animate = false)
    }

    /** Redraws the current page with fresh values, keeping the focus where it is. */
    fun refresh() = showPage(animate = false)

    private fun showPage(animate: Boolean) {
        val page = stack.current
        title.text = page.title()
        headerIcon.setImageDrawable(page.icon)
        headerIcon.visibility = if (page.icon == null) View.GONE else View.VISIBLE
        val sub = page.subtitle()
        subtitle.text = sub
        subtitle.visibility = if (sub == null) View.GONE else View.VISIBLE
        adapter.submit(page.rows())
        if (animate) {
            list.alpha = 0f
            list.animate().alpha(1f).setDuration(ANIMATION_MS).start()
        }
        focusRow(if (stack.currentRow == 0) adapter.firstPressableRow() else stack.currentRow)
    }

    private fun focusRow(wanted: Int, attempts: Int = 3) {
        val count = adapter.itemCount
        if (count == 0) return
        val preferred = wanted.coerceIn(0, count - 1)
        val target = (preferred until count).firstOrNull(adapter::isFocusableRow)
            ?: (preferred downTo 0).firstOrNull(adapter::isFocusableRow)
            ?: return
        list.post {
            val row = list.findViewHolderForAdapterPosition(target)?.itemView
            if (row != null) {
                row.requestFocus()
            } else if (attempts > 0) {
                list.scrollToPosition(target)
                focusRow(target, attempts - 1)
            }
        }
    }

    /** Older Android delivers Back as a key, and that is what most TV boxes run. */
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) goBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back closes one page at a time, then the window. */
    fun goBack() {
        hideKeyboard()
        if (stack.back()) showPage(animate = true) else closeMenu()
    }

    /** Otherwise the keyboard stays over the next page and keeps taking the arrow keys. */
    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    fun closeMenu() {
        if (closing) return
        hideKeyboard()
        closing = true
        sheet.animate().alpha(0f).scaleX(APPEAR_SCALE).scaleY(APPEAR_SCALE).setDuration(ANIMATION_MS).start()
        scrim.animate().alpha(0f).setDuration(ANIMATION_MS).withEndAction {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.start()
    }

    /**
     * Centers the window and sizes it to its content. When the content does not fit in
     * [LIMIT] of the screen height, or the page asks for it, the window takes the whole screen.
     */
    @SuppressLint("ViewConstructor")
    private class ModalLayout(
        context: Context,
        private val sheet: View,
        private val windowWidth: Int,
        private val alwaysFull: () -> Boolean,
        private val onMode: (Boolean) -> Unit,
    ) : FrameLayout(context) {
        private var full: Boolean? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val limit = (height * LIMIT).toInt()
            var takeAll = alwaysFull()
            if (!takeAll) {
                sheet.measure(
                    MeasureSpec.makeMeasureSpec(minOf(windowWidth, width), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST),
                )
                takeAll = sheet.measuredHeight >= limit
            }
            if (takeAll) {
                sheet.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
                )
            }
            if (full != takeAll) {
                full = takeAll
                onMode(takeAll)
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val x = (right - left - sheet.measuredWidth) / 2
            val y = (bottom - top - sheet.measuredHeight) / 2
            sheet.layout(x, y, x + sheet.measuredWidth, y + sheet.measuredHeight)
        }

        private companion object {
            const val LIMIT = 0.86f
        }
    }

    companion object {
        /** Reopens the window without the entrance animation, when it restarts to change its own look. */
        const val EXTRA_SKIP_ENTRANCE = "skip_entrance"
        private const val ANIMATION_MS = 150L
        private const val HEADER_ICON = 3.6f
        private const val CORNER = 1.2f
        private const val APPEAR_SCALE = 0.96f
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
