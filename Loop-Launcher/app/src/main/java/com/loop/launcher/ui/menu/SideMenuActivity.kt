package com.loop.launcher.ui.menu

import android.annotation.SuppressLint
import android.graphics.Color
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
import com.loop.launcher.R
import com.loop.launcher.settings.AppSettings
import com.loop.launcher.theme.ThemeDefinition
import com.loop.launcher.theme.Themes
import com.loop.launcher.ui.BaseActivity
import com.loop.launcher.ui.FontWeight
import com.loop.launcher.ui.face

/**
 * Menu that slides in on the left with the home screen still visible behind it.
 * Each subclass only says which pages it has.
 */
abstract class SideMenuActivity : BaseActivity() {
    protected lateinit var settings: AppSettings
    protected var style: ThemeDefinition = Themes.all.first()
    protected var unit = 0f
    private lateinit var stack: MenuStack<MenuPage>
    private lateinit var sheet: LinearLayout
    private lateinit var scrim: View
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var headerIcon: ImageView
    private lateinit var list: RecyclerView
    private lateinit var adapter: MenuAdapter
    private lateinit var previewArea: View
    private lateinit var previewFrame: FrameLayout
    private var closing = false
    private var previewKey: Any? = null
    private var started = false

    /** Width of the menu, as a percentage of the screen. */
    protected open val sheetWidth = 36f

    abstract fun rootPage(): MenuPage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        style = Themes.byId(settings.themeId)
        unit = resources.displayMetrics.widthPixels / 100f
        buildSheet()
        stack = MenuStack(rootPage())
        showPage(animate = false)
        if (!intent.getBooleanExtra(EXTRA_SKIP_ENTRANCE, false)) slideIn()
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
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 2.3f)
            typeface = style.face(FontWeight.MEDIUM)
            maxLines = 2
        }
        subtitle = TextView(this).apply {
            setTextColor(style.muted)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.1f)
            typeface = style.face()
            maxLines = 2
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
            setPadding((unit * 0.7f).toInt(), (unit * 0.4f).toInt(), (unit * 1.3f).toInt(), (unit * 2f).toInt())
            addItemDecoration(ScrollPill(style.muted, unit * 0.22f, style.radius == 0f))
        }
        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.surface)
            elevation = unit
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
                setPadding((unit * 1.8f).toInt(), (unit * 2.6f).toInt(), (unit * 1.6f).toInt(), (unit * 1f).toInt())
                addView(headerIcon, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = unit.toInt() })
                addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(list, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        scrim = View(this).apply { setBackgroundColor(0x8C000000.toInt()) }
        val root = FrameLayout(this)
        root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(sheet, FrameLayout.LayoutParams((unit * sheetWidth).toInt(), MATCH))
        if (style.radius == 0f && style.mono) {
            // A tiling desktop window has a visible edge.
            root.addView(
                View(this).apply { setBackgroundColor(style.accent) },
                FrameLayout.LayoutParams(2, MATCH).apply { marginStart = (unit * sheetWidth).toInt() },
            )
        }
        root.addView(buildPreviewArea())
        setContentView(root)
    }

    /** Sits in the free space beside the menu, keeping the proportions of the TV. */
    private fun buildPreviewArea(): View {
        val free = 100f - sheetWidth
        val previewWidth = (unit * free * PREVIEW_FILL).toInt()
        previewFrame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = unit * style.radius
            }
            clipToOutline = true
        }
        val area = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(
                TextView(context).apply {
                    text = getString(R.string.saver_preview)
                    setTextColor(style.text)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.2f)
                    typeface = style.face(FontWeight.MEDIUM)
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { bottomMargin = (unit * 0.6f).toInt() },
            )
            addView(previewFrame, LinearLayout.LayoutParams(previewWidth, previewWidth * 9 / 16))
            layoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL).apply {
                marginStart = (unit * (sheetWidth + free * (1f - PREVIEW_FILL) / 2f)).toInt()
            }
        }
        previewArea = area
        return area
    }

    /** Replaces what runs in the preview area of a page made with [MenuPage.hasPreview]. */
    fun showPreview(key: Any, make: () -> View) {
        if (previewKey == key) return
        previewKey = key
        // The focus can arrive in the middle of a layout, when adding a view would be ignored.
        previewFrame.post {
            if (previewKey != key) return@post
            previewFrame.removeAllViews()
            previewFrame.addView(make(), FrameLayout.LayoutParams(MATCH, MATCH))
        }
    }

    private fun slideIn() {
        sheet.translationX = -unit * sheetWidth
        scrim.alpha = 0f
        sheet.animate().translationX(0f).setDuration(ANIMATION_MS).start()
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
        previewArea.visibility = if (page.hasPreview) View.VISIBLE else View.GONE
        if (!page.hasPreview) {
            previewKey = null
            previewFrame.removeAllViews()
        }
        title.text = page.title()
        headerIcon.setImageDrawable(page.icon)
        headerIcon.visibility = if (page.icon == null) View.GONE else View.VISIBLE
        val sub = page.subtitle()
        subtitle.text = sub
        subtitle.visibility = if (sub == null) View.GONE else View.VISIBLE
        adapter.submit(page.rows())
        if (animate) {
            list.alpha = 0f
            list.translationX = unit * 1.5f
            list.animate().alpha(1f).translationX(0f).setDuration(ANIMATION_MS).start()
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

    /** Back closes one page at a time, then the menu. */
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
        sheet.animate().translationX(-unit * sheetWidth).setDuration(ANIMATION_MS).start()
        previewArea.visibility = View.GONE
        scrim.animate().alpha(0f).setDuration(ANIMATION_MS).withEndAction {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }.start()
    }

    companion object {
        /** Reopens the menu without sliding in, when it restarts to change its own look. */
        const val EXTRA_SKIP_ENTRANCE = "skip_entrance"
        private const val ANIMATION_MS = 150L
        private const val HEADER_ICON = 4.4f

        /** How much of the free space beside the menu the preview takes. */
        private const val PREVIEW_FILL = 0.88f
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
