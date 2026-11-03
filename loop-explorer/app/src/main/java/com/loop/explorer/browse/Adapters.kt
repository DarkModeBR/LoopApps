package com.loop.explorer.browse

import android.graphics.Color
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.loop.explorer.R
import com.loop.explorer.files.FileItem
import com.loop.explorer.files.FileKind
import com.loop.explorer.files.Place
import com.loop.explorer.files.Sizes
import com.loop.explorer.files.Thumbs
import com.loop.explorer.theme.ThemeDefinition
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.face
import java.util.Date

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Color of the circle behind a file's icon; folders use the color the user picked. */
fun FileKind.circle(theme: ThemeDefinition): Int = if (this == FileKind.FOLDER || theme.neutral) theme.folder else color

/** A soft tint of the accent over the background, for the line that has the focus. */
fun focusTint(theme: ThemeDefinition, background: Int): Int = ColorUtils.blendARGB(background, theme.accent, if (theme.dark) 0.28f else 0.18f)

/** The storages in the side panel, with how full each one is. */
class PlacesAdapter(
    private val theme: ThemeDefinition,
    private val unit: Float,
    private val language: String,
    private val onOpen: (Place) -> Unit,
) : RecyclerView.Adapter<PlacesAdapter.Holder>() {
    var places: List<Place> = emptyList()
        private set
    var current: Place? = null
        private set

    fun submit(newPlaces: List<Place>, selected: Place?) {
        places = newPlaces
        current = selected
        notifyDataSetChanged()
    }

    fun select(place: Place?) {
        current = place
        notifyDataSetChanged()
    }

    override fun getItemCount() = places.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(parent)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(places[position])

    private fun px(percent: Float) = (unit * percent).toInt()

    inner class Holder(parent: ViewGroup) : RecyclerView.ViewHolder(LinearLayout(parent.context)) {
        private val root = itemView as LinearLayout
        private val shape = GradientDrawable().apply { cornerRadius = unit * theme.radius }
        private val icon = ImageView(parent.context)
        private val label = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.2f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val detail = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.92f)
            maxLines = 1
        }
        private val fillColor = ColorDrawable(theme.accent)
        private val trackColor = ColorDrawable(theme.raised)
        private val fill = ClipDrawable(fillColor, Gravity.START, ClipDrawable.HORIZONTAL)
        private val bar = View(parent.context).apply {
            background = LayerDrawable(arrayOf(trackColor, fill))
        }
        private var place: Place? = null
        private var focused = false

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.isFocusable = true
            root.isClickable = true
            root.background = shape
            root.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.3f) }
            root.setPadding(px(1.1f), px(0.8f), px(1.2f), px(0.8f))
            root.addView(icon, LinearLayout.LayoutParams(px(1.7f), px(1.7f)).apply { marginEnd = px(1.3f) })
            val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label)
            texts.addView(detail)
            texts.addView(bar, LinearLayout.LayoutParams(MATCH, px(0.3f)).apply { topMargin = px(0.4f) })
            root.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            root.setOnFocusChangeListener { _, hasFocus ->
                focused = hasFocus
                paint()
            }
            root.setOnClickListener { place?.let(onOpen) }
        }

        fun bind(item: Place) {
            place = item
            icon.setImageResource(item.iconRes)
            label.text = item.label
            detail.typeface = theme.face()
            val hasUsage = item.total > 0
            detail.visibility = if (hasUsage) View.VISIBLE else View.GONE
            bar.visibility = if (hasUsage) View.VISIBLE else View.GONE
            if (hasUsage) {
                detail.text = itemView.context.getString(R.string.place_free, Sizes.text(item.free, language), Sizes.text(item.total, language))
                fill.level = ((item.total - item.free) * 10_000 / item.total).toInt()
            }
            paint()
        }

        private fun paint() {
            val chosen = place?.dir == current?.dir
            // Like a navigation drawer: the open storage is tinted, the focused one is filled.
            shape.setColor(
                when {
                    focused -> theme.accent
                    chosen -> ColorUtils.setAlphaComponent(theme.accent, 0x33)
                    else -> Color.TRANSPARENT
                },
            )
            val content = when {
                focused -> theme.onAccent
                chosen -> if (theme.dark) theme.text else theme.accent
                else -> theme.text
            }
            label.setTextColor(content)
            label.typeface = theme.face(if (chosen || focused) FontWeight.MEDIUM else FontWeight.REGULAR)
            detail.setTextColor(if (focused) theme.onAccent else theme.muted)
            icon.setColorFilter(if (focused) theme.onAccent else if (chosen) theme.accent else theme.muted)
            // On the focused row the bar takes the row's text color, or it would vanish into the fill.
            fillColor.color = if (focused) theme.onAccent else theme.accent
            trackColor.color = if (focused) ColorUtils.setAlphaComponent(theme.onAccent, 0x40) else theme.raised
        }
    }
}

interface FileListener {
    fun onFocused(position: Int, item: FileItem)
    fun onOpen(position: Int, item: FileItem)
    fun onMenu(position: Int, item: FileItem)
    fun isSelected(item: FileItem): Boolean
}

/** The files of the open folder: a list with round colored icons, or a grid of tiles. */
class FilesAdapter(
    private val theme: ThemeDefinition,
    private val unit: Float,
    private val language: String,
    private val thumbs: Thumbs,
    private val listener: FileListener,
) : RecyclerView.Adapter<FilesAdapter.Holder>() {
    var items: List<FileItem> = emptyList()
        private set
    var grid = false
    private val background = com.loop.explorer.theme.Themes.background(theme)

    init {
        setHasStableIds(true)
    }

    fun submit(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size
    override fun getItemId(position: Int) = items[position].file.path.hashCode().toLong()
    override fun getItemViewType(position: Int) = if (grid) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = if (viewType == 0) TileHolder(parent) else RowHolder(parent)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position], position)

    private fun px(percent: Float) = (unit * percent).toInt()

    abstract inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        protected var item: FileItem? = null
        protected var index = 0

        abstract fun bind(file: FileItem, position: Int)

        protected fun wire(root: View, onFocus: (Boolean) -> Unit) {
            root.isFocusable = true
            root.isClickable = true
            root.setOnFocusChangeListener { _, focused ->
                onFocus(focused)
                if (focused) item?.let { listener.onFocused(index, it) }
            }
            root.setOnClickListener { item?.let { listener.onOpen(index, it) } }
            root.setOnLongClickListener {
                item?.let { listener.onMenu(index, it) }
                item != null
            }
            root.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
                    item?.let { listener.onMenu(index, it) }
                    true
                } else {
                    false
                }
            }
        }

        protected fun date(file: FileItem): String =
            DateFormat.getDateFormat(itemView.context).format(Date(file.modified)) + " " +
                DateFormat.getTimeFormat(itemView.context).format(Date(file.modified))

        /** A round icon in the color of the type; photos, videos, songs and APKs show their picture instead. */
        protected fun showIcon(file: FileItem, circle: GradientDrawable, image: ImageView, icon: ImageView) {
            circle.setColor(file.kind.circle(theme))
            image.setImageDrawable(null)
            image.visibility = View.GONE
            icon.visibility = View.VISIBLE
            icon.setImageResource(file.kind.iconRes)
            icon.setColorFilter(Color.WHITE)
            thumbs.load(file) { bitmap ->
                if (item != file || bitmap == null) return@load
                image.setImageBitmap(bitmap)
                image.visibility = View.VISIBLE
                icon.visibility = View.GONE
            }
        }

        protected fun makeCheck(parent: ViewGroup) = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_check)
            setColorFilter(theme.onAccent)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(theme.accent)
                setStroke(px(0.15f).coerceAtLeast(2), this@FilesAdapter.background)
            }
            val pad = px(0.2f)
            setPadding(pad, pad, pad, pad)
        }
    }

    private inner class RowHolder(parent: ViewGroup) : Holder(LinearLayout(parent.context)) {
        private val root = itemView as LinearLayout
        private val shape = GradientDrawable().apply { cornerRadius = unit * theme.radius }
        private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        private val box = FrameLayout(parent.context)
        private val image = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        private val icon = ImageView(parent.context)
        private val check = makeCheck(parent)
        private val name = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1.2f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        private val detail = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.95f)
            maxLines = 1
        }
        private val size = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.95f)
            gravity = Gravity.END
            maxLines = 1
        }
        private var focused = false

        init {
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.background = shape
            root.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { bottomMargin = px(0.15f) }
            root.setPadding(px(1.2f), px(0.65f), px(1.6f), px(0.65f))
            box.background = circle
            box.clipToOutline = true
            box.outlineProvider = ViewOutlineProvider.BACKGROUND
            box.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            box.addView(icon, FrameLayout.LayoutParams(px(1.5f), px(1.5f), Gravity.CENTER))
            val boxWrap = FrameLayout(parent.context)
            boxWrap.addView(box, FrameLayout.LayoutParams(px(2.8f), px(2.8f)))
            boxWrap.addView(check, FrameLayout.LayoutParams(px(1.3f), px(1.3f), Gravity.BOTTOM or Gravity.END))
            root.addView(boxWrap, LinearLayout.LayoutParams(px(3f), px(3f)).apply { marginEnd = px(1.4f) })
            val texts = LinearLayout(parent.context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(name)
            texts.addView(detail)
            root.addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            root.addView(size, LinearLayout.LayoutParams(px(8f), WRAP).apply { marginStart = px(1f) })
            wire(root) {
                focused = it
                paint()
            }
        }

        override fun bind(file: FileItem, position: Int) {
            item = file
            index = position
            name.text = file.name
            name.typeface = theme.face()
            detail.typeface = theme.face()
            size.typeface = theme.face()
            detail.text = date(file)
            size.text = if (file.isDir) itemView.context.getString(R.string.kind_folder) else Sizes.text(file.size, language)
            check.visibility = if (listener.isSelected(file)) View.VISIBLE else View.GONE
            showIcon(file, circle, image, icon)
            paint()
        }

        private fun paint() {
            shape.setColor(if (focused) focusTint(theme, background) else Color.TRANSPARENT)
            shape.setStroke(px(0.15f).coerceAtLeast(2), if (focused) theme.accent else Color.TRANSPARENT)
            name.setTextColor(theme.text)
            name.typeface = theme.face(if (focused) FontWeight.MEDIUM else FontWeight.REGULAR)
            detail.setTextColor(theme.muted)
            size.setTextColor(theme.muted)
        }
    }

    private inner class TileHolder(parent: ViewGroup) : Holder(LinearLayout(parent.context)) {
        private val root = itemView as LinearLayout
        private val shape = GradientDrawable().apply { cornerRadius = unit * theme.cardRadius }
        private val picture = FrameLayout(parent.context)
        private val image = ImageView(parent.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        private val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        private val iconBox = FrameLayout(parent.context)
        private val icon = ImageView(parent.context)
        private val check = makeCheck(parent)
        private val name = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 1f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        private val detail = TextView(parent.context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * 0.85f)
            maxLines = 1
        }
        private var focused = false

        init {
            root.orientation = LinearLayout.VERTICAL
            root.background = shape
            root.layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply {
                bottomMargin = px(0.9f)
                marginStart = px(0.45f)
                marginEnd = px(0.45f)
            }
            root.clipToOutline = true
            picture.setBackgroundColor(theme.raised)
            picture.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
            iconBox.background = circle
            iconBox.addView(icon, FrameLayout.LayoutParams(px(1.7f), px(1.7f), Gravity.CENTER))
            picture.addView(iconBox, FrameLayout.LayoutParams(px(3.4f), px(3.4f), Gravity.CENTER))
            picture.addView(check, FrameLayout.LayoutParams(px(1.5f), px(1.5f), Gravity.TOP or Gravity.END).apply {
                topMargin = px(0.4f)
                marginEnd = px(0.4f)
            })
            root.addView(picture, LinearLayout.LayoutParams(MATCH, px(6.4f)))
            val texts = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(px(0.7f), px(0.5f), px(0.7f), px(0.6f))
                addView(name)
                addView(detail)
            }
            root.addView(texts, LinearLayout.LayoutParams(MATCH, WRAP))
            wire(root) { hasFocus ->
                focused = hasFocus
                val scale = if (hasFocus) theme.focusScale else 1f
                root.animate().scaleX(scale).scaleY(scale).setDuration(110).start()
                paint()
            }
        }

        override fun bind(file: FileItem, position: Int) {
            item = file
            index = position
            name.text = file.name
            name.typeface = theme.face()
            detail.typeface = theme.face()
            detail.text = if (file.isDir) itemView.context.getString(R.string.kind_folder) else Sizes.text(file.size, language)
            check.visibility = if (listener.isSelected(file)) View.VISIBLE else View.GONE
            val scale = if (root.isFocused) theme.focusScale else 1f
            root.scaleX = scale
            root.scaleY = scale
            image.setImageDrawable(null)
            image.visibility = View.GONE
            iconBox.visibility = View.VISIBLE
            circle.setColor(file.kind.circle(theme))
            icon.setImageResource(file.kind.iconRes)
            icon.setColorFilter(Color.WHITE)
            thumbs.load(file) { bitmap ->
                if (item != file || bitmap == null) return@load
                image.setImageBitmap(bitmap)
                image.visibility = View.VISIBLE
                iconBox.visibility = View.GONE
            }
            paint()
        }

        private fun paint() {
            shape.setColor(if (focused) focusTint(theme, background) else theme.surface)
            shape.setStroke(px(0.18f).coerceAtLeast(2), if (focused) theme.accent else Color.TRANSPARENT)
            name.setTextColor(theme.text)
            name.typeface = theme.face(if (focused) FontWeight.MEDIUM else FontWeight.REGULAR)
            detail.setTextColor(theme.muted)
        }
    }
}
