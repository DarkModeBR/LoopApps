package com.loop.explorer.server

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.loop.explorer.R
import com.loop.explorer.browse.focusTint
import com.loop.explorer.privileged.ShizukuGateway
import com.loop.explorer.privileged.ShizukuState
import com.loop.explorer.privileged.ShizukuStatus
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.theme.ThemeDefinition
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.Chip
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.ScreenErrors
import com.loop.explorer.ui.face
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * The HTTP server on a screen of its own: whether it is on, the address to type in a browser,
 * and a card for each device that has the page open.
 */
class ServerActivity : BaseActivity() {
    private val scope = MainScope() + ScreenErrors.handler()
    private val gateway by lazy { ShizukuGateway(this) }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var theme: ThemeDefinition
    private var unit = 0f

    private val stateShape = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private lateinit var stateIcon: ImageView
    private lateinit var stateText: TextView
    private lateinit var address: TextView
    private lateinit var hint: TextView
    private lateinit var toggle: Chip
    private lateinit var devicesTitle: TextView
    private lateinit var devicesList: RecyclerView
    private lateinit var empty: TextView
    private lateinit var shellNote: TextView
    private lateinit var shellChip: Chip
    private lateinit var devices: DeviceAdapter

    private val changed: () -> Unit = {
        showState()
        showDevices()
    }

    /** Once a second, so the connection times count up and closed pages disappear. */
    private val tick = object : Runnable {
        override fun run() {
            showDevices()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme = Themes.byId(AppSettings(this).themeId)
        unit = resources.displayMetrics.widthPixels / 100f
        buildScreen()
        ServerService.addListener(changed)
        showState()
        showDevices()
        toggle.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        showShell()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        ServerService.removeListener(changed)
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
    }

    private fun px(percent: Float) = (unit * percent).toInt()

    private fun text(size: Float, color: Int, weight: FontWeight = FontWeight.REGULAR) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * size)
        setTextColor(color)
        typeface = theme.face(weight)
    }

    private fun card() = GradientDrawable().apply {
        setColor(theme.surface)
        cornerRadius = unit * theme.cardRadius
    }

    private fun buildScreen() {
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.bar)
            elevation = unit * 0.3f
            setPadding(px(2.4f), 0, px(2.4f), 0)
            addView(text(1.7f, theme.onBar, FontWeight.MEDIUM).apply { text = getString(R.string.server_title) })
            addView(text(0.95f, ColorUtils.setAlphaComponent(theme.onBar, 0xCC)).apply {
                text = getString(R.string.server_intro)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }

        stateIcon = ImageView(this).apply { setImageResource(R.drawable.ic_server) }
        val stateBadge = FrameLayout(this).apply {
            background = stateShape
            addView(stateIcon, FrameLayout.LayoutParams(px(2.2f), px(2.2f), Gravity.CENTER))
        }
        stateText = text(1.05f, theme.muted, FontWeight.MEDIUM)
        address = text(2.3f, theme.text, FontWeight.MEDIUM).apply { maxLines = 3 }
        hint = text(1f, theme.muted).apply { maxLines = 2 }
        val stateTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(stateText)
            addView(address, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(0.2f) })
            addView(hint, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(0.3f) })
        }
        toggle = Chip(this, theme, unit).apply { setOnClickListener { flip() } }
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = card()
            setPadding(px(1.8f), px(1.6f), px(1.8f), px(1.6f))
            addView(stateBadge, LinearLayout.LayoutParams(px(4.6f), px(4.6f)))
            addView(stateTexts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = px(1.6f) })
            addView(toggle, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = px(1.6f) })
        }

        devicesTitle = text(1.15f, theme.text, FontWeight.MEDIUM).apply { setPadding(px(0.2f), px(2.2f), 0, px(1f)) }
        devices = DeviceAdapter()
        devicesList = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(context, COLUMNS)
            adapter = devices
            itemAnimator = null
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, px(1f))
        }
        empty = text(1.05f, theme.muted).apply {
            background = card()
            gravity = Gravity.CENTER
            setPadding(px(2f), px(2.4f), px(2f), px(2.4f))
        }
        val devicesBox = FrameLayout(this).apply {
            // The cards have a small margin for the focus border; this keeps their edges in line with the box above.
            addView(devicesList, FrameLayout.LayoutParams(MATCH, MATCH).apply { setMargins(-px(0.5f), -px(0.5f), -px(0.5f), 0) })
            addView(empty, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        }

        shellNote = text(0.95f, theme.muted).apply { maxLines = 2 }
        shellChip = Chip(this, theme, unit)
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(1f), 0, 0)
            addView(shellNote, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(shellChip, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = px(1.2f) })
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(2.4f), px(2f), px(2.4f), px(1.6f))
            addView(status, LinearLayout.LayoutParams(MATCH, WRAP))
            addView(devicesTitle)
            addView(devicesBox, LinearLayout.LayoutParams(MATCH, 0, 1f))
            addView(footer, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Themes.background(theme))
            addView(top, LinearLayout.LayoutParams(MATCH, px(TOOLBAR)))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        setContentView(root)
    }

    private fun flip() {
        if (ServerService.running) {
            ServerService.stop(this)
            return
        }
        // Without this permission the notification is hidden, but the server still works.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
        ServerService.start(this)
        // When every port is taken the server stops at once; the user should know why nothing changed.
        handler.postDelayed({
            if (!ServerService.running && !isFinishing) android.widget.Toast.makeText(this, R.string.server_failed, android.widget.Toast.LENGTH_LONG).show()
        }, START_WAIT_MS)
    }

    private fun showState() {
        val on = ServerService.running
        stateShape.setColor(if (on) ON_COLOR else theme.raised)
        stateIcon.setColorFilter(if (on) Color.WHITE else theme.muted)
        stateText.text = getString(if (on) R.string.server_state_on else R.string.server_state_off)
        stateText.setTextColor(if (on) onText() else theme.muted)
        address.text = ServerService.urls.joinToString("\n")
        address.visibility = if (on) View.VISIBLE else View.GONE
        hint.text = getString(if (on) R.string.server_open_hint else R.string.server_off_hint)
        toggle.set(if (on) R.drawable.ic_close else R.drawable.ic_server, getString(if (on) R.string.server_stop else R.string.server_turn_on))
    }

    private fun onText() = if (theme.dark) 0xFF69F0AE.toInt() else 0xFF2E7D32.toInt()

    private fun showDevices() {
        val list = if (ServerService.running) Clients.active() else emptyList()
        if (list.isNotEmpty()) own = ServerService.addresses().toSet()
        val focusLost = devicesList.hasFocus() && list.size < devices.itemCount
        devices.submit(list)
        devicesTitle.text = if (list.isEmpty()) getString(R.string.server_devices) else getString(R.string.server_devices_count, list.size)
        empty.text = getString(if (ServerService.running) R.string.server_devices_none else R.string.server_devices_off)
        empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (focusLost) devicesList.post { if (!devicesList.hasFocus() && currentFocus == null) toggle.requestFocus() }
    }

    private fun showShell() {
        val state = gateway.state()
        shellNote.text = getString(if (state == ShizukuState.READY) R.string.server_shell_adb else R.string.server_shell_app)
        when (state) {
            ShizukuState.NEEDS_PERMISSION -> {
                shellChip.set(R.drawable.ic_check, getString(R.string.shizuku_authorize))
                shellChip.setOnClickListener {
                    scope.launch {
                        gateway.requestPermission()
                        showShell()
                    }
                }
                shellChip.visibility = View.VISIBLE
            }
            ShizukuState.NOT_RUNNING, ShizukuState.UNSUPPORTED -> {
                shellChip.set(R.drawable.ic_open, getString(R.string.shizuku_open))
                shellChip.setOnClickListener { ShizukuStatus.openApp(this) }
                shellChip.visibility = View.VISIBLE
            }
            else -> shellChip.visibility = View.GONE
        }
    }

    private var own: Set<String> = emptySet()

    private fun ownAddress(ip: String) = ip == "127.0.0.1" || ip == "::1" || ip in own

    private fun shownIp(ip: String) = ip.substringBefore('%').removePrefix("::ffff:")

    private fun elapsed(since: Long): String {
        val seconds = ((SystemClock.elapsedRealtime() - since) / 1000).coerceAtLeast(0)
        return when {
            seconds < 60 -> getString(R.string.time_seconds, seconds.toInt())
            seconds < 3600 -> getString(R.string.time_minutes, (seconds / 60).toInt())
            else -> getString(R.string.time_hours, (seconds / 3600).toInt(), ((seconds % 3600) / 60).toInt())
        }
    }

    /** One card per device. Ids stay the same while a device is connected, so the focus stays on its card. */
    private inner class DeviceAdapter : RecyclerView.Adapter<DeviceAdapter.Holder>() {
        private var items: List<Clients.Client> = emptyList()

        init {
            setHasStableIds(true)
        }

        @SuppressLint("NotifyDataSetChanged")
        fun submit(list: List<Clients.Client>) {
            val same = list.map { it.ip } == items.map { it.ip }
            items = list
            if (same) notifyItemRangeChanged(0, list.size) else notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun getItemId(position: Int) = items[position].ip.hashCode().toLong()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder()

        override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

        inner class Holder : RecyclerView.ViewHolder(DeviceCard()) {
            fun bind(client: Clients.Client) = (itemView as DeviceCard).bind(client)
        }
    }

    @SuppressLint("ViewConstructor")
    private inner class DeviceCard : LinearLayout(this@ServerActivity) {
        private val shape = card()
        private val badgeShape = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        private val icon = ImageView(context)
        private val title = text(1.3f, theme.text, FontWeight.MEDIUM).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val subtitle = text(0.95f, theme.muted).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val system = Line(R.string.device_system)
        private val browser = Line(R.string.device_browser)
        private val ip = Line(R.string.device_ip)
        private val time = Line(R.string.device_time)
        private var focused = false

        init {
            orientation = VERTICAL
            isFocusable = true
            background = shape
            layoutParams = RecyclerView.LayoutParams(MATCH, WRAP).apply { setMargins(px(0.5f), px(0.5f), px(0.5f), px(0.5f)) }
            setPadding(px(1.4f), px(1.3f), px(1.4f), px(1.3f))
            val badge = FrameLayout(context).apply {
                background = badgeShape
                addView(icon, FrameLayout.LayoutParams(px(1.8f), px(1.8f), Gravity.CENTER))
            }
            val names = LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title)
                addView(subtitle)
            }
            val head = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(badge, LayoutParams(px(3.6f), px(3.6f)))
                addView(names, LayoutParams(0, WRAP, 1f).apply { marginStart = px(1.1f) })
            }
            addView(head)
            val divider = View(context).apply { setBackgroundColor(theme.raised) }
            addView(divider, LayoutParams(MATCH, px(0.12f).coerceAtLeast(1)).apply { setMargins(0, px(1.1f), 0, px(0.8f)) })
            listOf(system, browser, ip, time).forEach { addView(it.row) }
            paint()
        }

        fun bind(client: Clients.Client) {
            val guess = DeviceGuesser.guess(client.userAgent, client.touchPoints)
            val self = ownAddress(client.ip)
            val type = if (self) DeviceType.TV else guess.type
            icon.setImageResource(type.iconRes)
            val badgeColor = if (theme.neutral) theme.folder else theme.accent
            badgeShape.setColor(badgeColor)
            icon.setColorFilter(Themes.onColor(badgeColor))
            title.text = getString(type.nameRes)
            // The model when the browser tells it; the system is already in the lines below.
            subtitle.text = if (self) getString(R.string.device_this) else guess.model
            subtitle.visibility = if (subtitle.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            val unknown = getString(R.string.device_unknown)
            system.set(guess.system ?: if (self) "Android" else unknown)
            browser.set(guess.browser ?: unknown)
            ip.set(shownIp(client.ip))
            time.set(elapsed(client.since))
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            focused = gainFocus
            paint()
        }

        private fun paint() {
            val background = Themes.background(theme)
            shape.setColor(if (focused) focusTint(theme, background) else theme.surface)
            shape.setStroke(px(0.18f).coerceAtLeast(2), if (focused) theme.accent else Color.TRANSPARENT)
        }
    }

    /** A label on the left and its value on the right, inside a device card. */
    private inner class Line(label: Int) {
        private val value = text(1f, theme.text).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.END
        }
        val row = LinearLayout(this@ServerActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, px(0.25f), 0, px(0.25f))
            addView(text(1f, theme.muted).apply { text = getString(label) }, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(value, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = px(1f) })
        }

        fun set(text: String) {
            value.text = text
        }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 5201
        const val TICK_MS = 1000L
        const val START_WAIT_MS = 3000L
        const val COLUMNS = 3
        const val TOOLBAR = 6.4f
        const val ON_COLOR = 0xFF2E7D32.toInt()
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
