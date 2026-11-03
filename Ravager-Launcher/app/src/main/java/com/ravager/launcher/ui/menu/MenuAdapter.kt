package com.ravager.launcher.ui.menu

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ravager.launcher.R
import com.ravager.launcher.theme.ThemeDefinition
import com.ravager.launcher.ui.FontWeight
import com.ravager.launcher.ui.face

/** Draws menu rows with the look of the current theme. */
class MenuAdapter(
    private val style: ThemeDefinition,
    private val unit: Float,
    private val onRowFocused: (Int) -> Unit,
) : RecyclerView.Adapter<MenuAdapter.Holder>() {
    var rows: List<MenuRow> = emptyList()
        private set

    private val square = style.radius == 0f

    fun submit(newRows: List<MenuRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun isFocusableRow(position: Int): Boolean = when (rows.getOrNull(position)) {
        null, is MenuRow.Section, is MenuRow.Meter -> false
        else -> true
    }

    /** Where the focus starts on a page: the first thing that can be pressed, not text to read. */
    fun firstPressableRow(): Int = rows.indexOfFirst {
        it is MenuRow.Nav || it is MenuRow.Action || it is MenuRow.Toggle || it is MenuRow.Option || it is MenuRow.Input
    }.coerceAtLeast(0)

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is MenuRow.Section -> TYPE_SECTION
        is MenuRow.Meter -> TYPE_METER
        is MenuRow.Note, is MenuRow.Code -> TYPE_TEXT
        is MenuRow.Input -> TYPE_INPUT
        else -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = when (viewType) {
        TYPE_SECTION -> SectionHolder(text(parent, 1.05f, style.muted, FontWeight.MEDIUM).apply {
            setPadding(px(1.1f), px(1.1f), px(1f), px(0.45f))
            layoutParams = RecyclerView.LayoutParams(MATCH, WRAP)
        })
        TYPE_METER -> MeterHolder(parent)
        TYPE_TEXT -> TextHolder(parent)
        TYPE_INPUT -> InputHolder(parent)
        else -> RowHolder(parent)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

    private fun px(percent: Float) = (unit * percent).toInt()

    private fun text(parent: ViewGroup, size: Float, color: Int, weight: FontWeight = FontWeight.REGULAR) =
        TextView(parent.context).apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * size)
            typeface = style.face(weight)
        }

    abstract class Holder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(row: MenuRow)
    }

    private class SectionHolder(private val label: TextView) : Holder(label) {
        override fun bind(row: MenuRow) {
            label.text = (row as MenuRow.Section).title
        }
    }

    private inner class RowHolder(parent: ViewGroup) : Holder(LinearLayout(parent.context)) {
        private val root = itemView as LinearLayout
        private val shape = GradientDrawable().apply { cornerRadius = unit * style.radius }
        private val icon = ImageView(parent.context)
        private val radio = RadioView(parent.context, square)
        private val thumb = ImageView(parent.context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                cornerRadius = unit * style.radius
                setColor(style.raised)
            }
            clipToOutline = true
        }
        private val textColumn = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
        private val title = text(parent, 1.35f, style.text).apply { maxLines = 1 }
        private val detail = text(parent, 1.05f, style.muted).apply { maxLines = 2 }
        private val value = text(parent, 1.1f, style.muted).apply {
            maxLines = 1
            gravity = Gravity.END
        }
        private val swatch = SwatchView(parent.context, square)
        private val switch = SwitchView(parent.context, square).apply {
            trackOff = style.raised
            trackOn = style.accent
            thumb = style.onAccent
        }
        private val chevron = ImageView(parent.context).apply { setImageResource(R.drawable.ic_chevron_right) }
        private var row: MenuRow? = null
        private var focused = false

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isFocusable = true
            root.isClickable = true
            root.background = shape
            root.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.25f) }
            root.setPadding(px(1.1f), px(0.8f), px(1f), px(0.8f))
            val iconSize = px(1.7f)
            root.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginEnd = px(1f) })
            root.addView(radio, LinearLayout.LayoutParams(px(1.4f), px(1.4f)).apply { marginEnd = px(1f) })
            root.addView(thumb, LinearLayout.LayoutParams(px(6.4f), px(3.6f)).apply { marginEnd = px(1f) })
            textColumn.addView(title)
            textColumn.addView(detail)
            root.addView(textColumn, LinearLayout.LayoutParams(0, WRAP, 1f))
            root.addView(value, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = px(0.8f) })
            root.addView(swatch, LinearLayout.LayoutParams(px(4.2f), px(1.5f)).apply { marginStart = px(0.8f) })
            root.addView(switch, LinearLayout.LayoutParams(px(3f), px(1.6f)).apply { marginStart = px(0.8f) })
            root.addView(chevron, LinearLayout.LayoutParams(px(1.2f), px(1.2f)).apply { marginStart = px(0.5f) })
            root.setOnFocusChangeListener { _, hasFocus ->
                focused = hasFocus
                paint()
                if (hasFocus && bindingAdapterPosition != RecyclerView.NO_POSITION) onRowFocused(bindingAdapterPosition)
            }
            root.setOnClickListener { click() }
            root.setOnLongClickListener { runMore() }
            root.setOnKeyListener { _, keyCode, event ->
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN && runMore()
            }
        }

        private fun runMore(): Boolean {
            val more = (row as? MenuRow.Toggle)?.more ?: return false
            more()
            return true
        }

        private fun click() {
            when (val current = row) {
                is MenuRow.Nav -> current.open()
                is MenuRow.Action -> current.run()
                is MenuRow.Option -> current.pick()
                is MenuRow.Toggle -> {
                    switch.setOn(!current.isOn, animate = true)
                    current.set(!current.isOn)
                }
                else -> Unit
            }
        }

        override fun bind(row: MenuRow) {
            this.row = row
            val iconRes: Int?
            val valueText: String?
            val detailText: String?
            val titleText: String
            when (row) {
                is MenuRow.Nav -> {
                    titleText = row.title; iconRes = row.icon; valueText = row.value; detailText = row.detail
                }
                is MenuRow.Action -> {
                    titleText = row.title; iconRes = row.icon; valueText = row.value; detailText = null
                }
                is MenuRow.Toggle -> {
                    titleText = row.title; iconRes = row.icon; valueText = null; detailText = row.detail
                }
                is MenuRow.Option -> {
                    titleText = row.title; iconRes = null; valueText = null; detailText = null
                }
                else -> return
            }
            title.text = titleText
            icon.visibility = if (iconRes == null) View.GONE else View.VISIBLE
            iconRes?.let(icon::setImageResource)
            value.text = valueText
            value.visibility = if (valueText == null) View.GONE else View.VISIBLE
            detail.text = detailText
            detail.visibility = if (detailText == null) View.GONE else View.VISIBLE
            chevron.visibility = if (row is MenuRow.Nav || (row as? MenuRow.Toggle)?.more != null) View.VISIBLE else View.GONE
            switch.visibility = if (row is MenuRow.Toggle) View.VISIBLE else View.GONE
            if (row is MenuRow.Toggle) switch.setOn(row.isOn, animate = false)
            radio.visibility = if (row is MenuRow.Option) View.VISIBLE else View.GONE
            swatch.visibility = if (row is MenuRow.Option && row.swatch.isNotEmpty()) View.VISIBLE else View.GONE
            if (row is MenuRow.Option) {
                radio.selected2 = row.selected
                swatch.colors = row.swatch
            }
            showThumb(row)
            paint()
        }

        private fun showThumb(row: MenuRow) {
            val source = (row as? MenuRow.Nav)?.thumb ?: (row as? MenuRow.Option)?.thumb
            thumb.visibility = if (source == null) View.GONE else View.VISIBLE
            thumb.setImageBitmap(null)
            source?.load { bitmap -> if (this.row === row) thumb.setImageBitmap(bitmap) }
        }

        private fun paint() {
            val danger = (row as? MenuRow.Action)?.danger == true
            shape.setColor(if (focused) style.accent else Color.TRANSPARENT)
            val content = when {
                focused -> style.onAccent
                danger -> DANGER
                else -> style.text
            }
            title.setTextColor(content)
            title.typeface = style.face(if (focused) FontWeight.MEDIUM else FontWeight.REGULAR)
            val soft = if (focused) style.onAccent else style.muted
            value.setTextColor(soft)
            detail.setTextColor(soft)
            icon.setColorFilter(content)
            chevron.setColorFilter(soft)
            // A switch in the accent color would vanish on a focused row of that same color.
            switch.trackOn = if (focused) style.onAccent else style.accent
            switch.thumb = if (focused) style.accent else style.onAccent
            switch.invalidate()
            radio.color = content
            radio.invalidate()
            swatch.invalidate()
        }
    }

    private inner class TextHolder(parent: ViewGroup) : Holder(text(parent, 1.2f, style.text)) {
        private val label = itemView as TextView
        private val shape = GradientDrawable().apply { cornerRadius = unit * style.radius }
        private var isCode = false

        init {
            label.isFocusable = true
            label.background = shape
            label.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.3f) }
            label.setPadding(px(1.1f), px(0.6f), px(1f), px(0.6f))
            label.setOnFocusChangeListener { _, focused ->
                paint(focused)
                if (focused && bindingAdapterPosition != RecyclerView.NO_POSITION) onRowFocused(bindingAdapterPosition)
            }
        }

        override fun bind(row: MenuRow) {
            isCode = row is MenuRow.Code
            label.text = if (row is MenuRow.Code) row.text else (row as MenuRow.Note).text
            label.typeface = if (isCode) Typeface.MONOSPACE else style.face()
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * if (isCode) 1.05f else 1.2f)
            paint(label.isFocused)
        }

        private fun paint(focused: Boolean) {
            shape.setColor(
                when {
                    focused -> style.raised
                    isCode -> 0xFF000000.toInt()
                    else -> Color.TRANSPARENT
                },
            )
            label.setTextColor(if (isCode || focused) style.text else style.muted)
        }
    }

    private inner class InputHolder(parent: ViewGroup) : Holder(EditText(parent.context)) {
        private val field = itemView as EditText
        private val shape = GradientDrawable().apply {
            cornerRadius = unit * style.radius
            setColor(style.raised)
        }
        private var row: MenuRow.Input? = null

        init {
            field.background = shape
            field.setSingleLine()
            field.imeOptions = EditorInfo.IME_ACTION_DONE
            field.setTextColor(style.text)
            field.setHintTextColor(style.muted)
            field.typeface = style.face()
            field.setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.35f)
            field.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.5f) }
            field.setPadding(px(1.1f), px(0.9f), px(1f), px(0.9f))
            field.setOnFocusChangeListener { _, focused ->
                shape.setStroke(px(0.15f).coerceAtLeast(2), if (focused) style.accent else Color.TRANSPARENT)
            }
            field.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) row?.submit?.invoke()
                action == EditorInfo.IME_ACTION_DONE
            }
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    row?.onChange?.invoke(s?.toString().orEmpty())
                }
            })
        }

        override fun bind(row: MenuRow) {
            val input = row as MenuRow.Input
            this.row = null
            field.hint = input.hint
            field.setText(input.initial)
            field.setSelection(field.text.length)
            this.row = input
        }
    }

    private inner class MeterHolder(parent: ViewGroup) : Holder(LinearLayout(parent.context)) {
        private val title = text(parent, 1.15f, style.muted).apply { maxLines = 1 }
        private val value = text(parent, 2.1f, style.text, FontWeight.LIGHT).apply { maxLines = 1 }
        private val fill = ClipDrawable(ColorDrawable(style.accent), Gravity.START, ClipDrawable.HORIZONTAL)
        private val track = FrameLayout(parent.context).apply {
            setBackgroundColor(style.raised)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            addView(View(context).apply { background = fill }, FrameLayout.LayoutParams(MATCH, MATCH))
        }

        init {
            val root = itemView as LinearLayout
            root.orientation = LinearLayout.VERTICAL
            root.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(1.7f) }
            root.setPadding(px(1.1f), 0, px(1f), 0)
            root.addView(title, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.1f) })
            root.addView(value, LinearLayout.LayoutParams(MATCH, WRAP))
            root.addView(track, LinearLayout.LayoutParams(MATCH, px(0.55f)).apply { topMargin = px(0.55f) })
        }

        override fun bind(row: MenuRow) {
            val meter = row as MenuRow.Meter
            title.text = meter.title
            value.text = meter.value
            track.visibility = if (meter.fraction == null) View.GONE else View.VISIBLE
            meter.fraction?.let { fill.level = (it.coerceIn(0.0, 1.0) * 10_000).toInt() }
        }
    }

    private companion object {
        const val TYPE_ROW = 0
        const val TYPE_SECTION = 1
        const val TYPE_TEXT = 2
        const val TYPE_INPUT = 3
        const val TYPE_METER = 4
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val DANGER = 0xFFFF8A80.toInt()
    }
}
