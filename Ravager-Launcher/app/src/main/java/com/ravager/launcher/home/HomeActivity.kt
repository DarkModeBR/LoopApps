package com.ravager.launcher.home

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.SharedPreferences
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ravager.launcher.BuildConfig
import com.ravager.launcher.R
import com.ravager.launcher.apps.AppEntry
import com.ravager.launcher.apps.AppMenuActivity
import com.ravager.launcher.apps.AppSource
import com.ravager.launcher.notifications.DumpsysNotifications
import com.ravager.launcher.notifications.NotificationCenter
import com.ravager.launcher.notifications.NotificationStore
import com.ravager.launcher.notifications.NotificationsMenuActivity
import com.ravager.launcher.notifications.hasNotificationAccess
import com.ravager.launcher.privileged.AccessHelpActivity
import com.ravager.launcher.privileged.AccessKind
import com.ravager.launcher.privileged.AndroidHomeSystem
import com.ravager.launcher.privileged.DefaultLauncherFlow
import com.ravager.launcher.privileged.ExtrasCoordinator
import com.ravager.launcher.privileged.LossNotice
import com.ravager.launcher.privileged.PowerMenuActivity
import com.ravager.launcher.privileged.ShizukuGateway
import com.ravager.launcher.privileged.ShizukuState
import com.ravager.launcher.screensaver.IdleTimer
import com.ravager.launcher.screensaver.SaverActivity
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.system.ConnectionLabel
import com.ravager.launcher.system.ConnectionLabels
import com.ravager.launcher.system.ConnectionState
import com.ravager.launcher.system.NetworkKind
import com.ravager.launcher.system.NetworkMonitor
import com.ravager.launcher.system.UsbMonitor
import com.ravager.launcher.settings.LayoutMode
import com.ravager.launcher.settings.SettingsMenuActivity
import com.ravager.launcher.status.StatusMenuActivity
import com.ravager.launcher.update.UpdateMenuActivity
import com.ravager.launcher.wallpaper.Wallpaper
import com.ravager.launcher.wallpaper.WallpaperFiles
import com.ravager.launcher.wallpaper.WallpaperLayer
import com.ravager.launcher.theme.Background
import com.ravager.launcher.theme.Themes
import com.ravager.launcher.trays.FocusResolver
import com.ravager.launcher.trays.MoveStep
import com.ravager.launcher.trays.TrayLayout
import com.ravager.launcher.trays.TrayLayouts
import com.ravager.launcher.trays.TrayNames
import com.ravager.launcher.trays.TrayLoader
import com.ravager.launcher.trays.TrayStore
import com.ravager.launcher.net.UrlConnectionHttpGet
import com.ravager.launcher.ui.BaseActivity
import com.ravager.launcher.ui.AppFonts
import com.ravager.launcher.ui.FontWeight
import com.ravager.launcher.ui.ScreenErrors
import com.ravager.launcher.ui.face
import com.ravager.launcher.ui.openOrWarn
import com.ravager.launcher.update.UpdateChecker
import com.ravager.launcher.update.UpdateInfo
import com.ravager.launcher.weather.Coordinates
import com.ravager.launcher.weather.CurrentWeather
import com.ravager.launcher.weather.WeatherQuery
import com.ravager.launcher.weather.WeatherRepository
import com.ravager.launcher.weather.WeatherText
import com.ravager.launcher.ui.BaseActivity.Companion.currentLanguage
import java.io.File
import rikka.shizuku.Shizuku
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

class HomeActivity : BaseActivity(), CardListener {
    private val scope = MainScope() + ScreenErrors.handler()
    private lateinit var settings: AppSettings
    private lateinit var source: AppSource
    private lateinit var store: TrayStore
    private lateinit var artLoader: CardArtLoader
    private lateinit var metrics: HomeMetrics
    private lateinit var header: HeaderView
    private lateinit var appName: TextView
    private lateinit var trayList: RecyclerView
    private lateinit var trayAdapter: TrayAdapter
    private lateinit var emptyView: View
    private lateinit var usbChip: ChipView
    private lateinit var networkChip: ChipView
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var usbMonitor: UsbMonitor
    private lateinit var weather: WeatherRepository
    private lateinit var gateway: ShizukuGateway
    private lateinit var extras: ExtrasCoordinator
    private lateinit var bellChip: ChipView
    private val shizukuConnected = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            refreshWifiName()
            readNotificationsWithShizuku()
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private var idleTimer = IdleTimer(0) { 0L }
    private val idleCheck = object : Runnable {
        override fun run() {
            if (idleTimer.shouldStart()) {
                startActivity(Intent(this@HomeActivity, SaverActivity::class.java))
            } else {
                handler.postDelayed(this, IDLE_CHECK_MS)
            }
        }
    }
    private val notificationsChanged = { runOnUiThread { showNotifications() } }
    private var connection = ConnectionState(NetworkKind.NONE)
    private var usbDevices: List<String> = emptyList()
    private var wifiName: String? = null
    private var wifiJob: Job? = null
    private var notificationJob: Job? = null
    private var availableUpdate: UpdateInfo? = null
    private var updateJob: Job? = null
    private lateinit var updateChip: ChipView
    private var clockTicks = 0
    private val glow = GlowDrawable()
    private var theme = Themes.all.first()

    private var layout: TrayLayout? = null
    private var appsByPackage: Map<String, AppEntry> = emptyMap()
    private var lastPackage: String? = null
    private var lastTrayIndex = 0
    private var lastColumn = 0
    private var appliedLayoutMode = LayoutMode.HIGHLIGHT
    private var appliedLanguage = ""
    private var resumedBefore = false
    private var started = false
    private var wallpaperLayer: WallpaperLayer? = null
    private var showButtonLabels = true
    private var showNetworkName = true
    private var warnedOverride: String? = null
    private var movingPackage: String? = null
    private var layoutBeforeMove: TrayLayout? = null

    /** A move asked for while this screen did not exist, which starts once the apps are known. */
    private var pendingMove: String? = null

    /** Shows theme and layout changes right away, behind the open settings menu. */
    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in LIVE_SETTINGS) rebuildForSettings()
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateClock()
            refreshWeather()
            if (++clockTicks % NOTIFICATION_POLL_TICKS == 0) readNotificationsWithShizuku()
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { artLoader.forget(it) }
            reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        source = AppSource(this)
        store = TrayStore(File(filesDir, "trays.json"))
        appliedLanguage = currentLanguage(this)
        networkMonitor = NetworkMonitor(this)
        usbMonitor = UsbMonitor(this)
        gateway = ShizukuGateway(this)
        extras = ExtrasCoordinator(gateway)
        gateway.onConnected(shizukuConnected)
        weather = WeatherRepository(UrlConnectionHttpGet(), File(filesDir, "weather.json"))
        settings.register(settingsChanged)
        artLoader = CardArtLoader(source, scope)
        pendingMove = intent.getStringExtra(EXTRA_MOVE_PACKAGE)
        intent.removeExtra(EXTRA_MOVE_PACKAGE)
        buildScreen()
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        reloadApps()
        trayList.post { tryToBecomeDefault() }
        // A downloaded update is useless once the launcher starts again.
        scope.launch(Dispatchers.IO) { File(cacheDir, "updates").deleteRecursively() }
        // Newer Android delivers Back only here, and a launcher has nothing behind it to go to.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (movingPackage != null) cancelMove()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentLanguage(this) != appliedLanguage) {
            recreate()
            return
        }
        if (resumedBefore) {
            // Picks up changes made in the menus that do not repaint by themselves.
            reloadApps()
            refreshWeather()
            showNotifications()
            readNotificationsWithShizuku()
        }
        resumedBefore = true
        checkDefaultStatus()
        idleTimer = IdleTimer(settings.saverTimeoutMinutes * 60_000L, SystemClock::elapsedRealtime)
        handler.post(idleCheck)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(idleCheck)
    }

    private fun buildScreen() {
        wallpaperLayer?.release()
        AppFonts.use(this)
        appliedLayoutMode = settings.layoutMode
        theme = Themes.byId(settings.themeId)
        metrics = HomeMetrics(this, appliedLayoutMode, settings.cardSize, settings.showTrayNames)
        header = HeaderView(this, metrics, theme, settings.showClock, settings.showDate, settings.weatherEnabled)
        appName = TextView(this).apply {
            setTextColor(theme.text)
            setShadowLayer(metrics.cwf(0.4f), 0f, metrics.cwf(0.08f), 0x99000000.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(APP_NAME_SIZE))
            typeface = theme.face(FontWeight.REGULAR)
            maxLines = 1
            visibility = if (settings.showAppName) View.VISIBLE else View.INVISIBLE
        }
        trayAdapter = TrayAdapter(metrics, artLoader, this, theme, legendBackground())
        trayList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context).apply { isItemPrefetchEnabled = true }
            adapter = trayAdapter
            itemAnimator = null
            isFocusable = false
            overScrollMode = View.OVER_SCROLL_NEVER
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
        emptyView = buildEmptyView()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(metrics.sidePadding, metrics.topPadding, metrics.sidePadding, 0)
        }
        content.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))
        content.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))
        content.addView(appName, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = metrics.cw(0.7f) })
        content.addView(trayList, LinearLayout.LayoutParams(MATCH, metrics.rowHeight * metrics.visibleRows))

        val root = FrameLayout(this).apply { background = glow }
        val layer = WallpaperLayer(this, scope) {
            Toast.makeText(this, R.string.wallpaper_broken, Toast.LENGTH_LONG).show()
        }
        wallpaperLayer = layer
        root.addView(layer, FrameLayout.LayoutParams(MATCH, MATCH))
        layer.show(settings.wallpaper, WallpaperFiles(File(filesDir, "wallpapers")), settings.wallpaperDim)
        if (started) layer.resume()
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(emptyView, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)
        addChips()
        paintBackground(null)
    }

    private fun legendBackground(): Int = if (settings.wallpaper != Wallpaper.None) theme.surface else when (val background = theme.background) {
        is Background.Flat -> background.color
        is Background.Vertical -> background.bottom
        else -> theme.glowBase
    }

    private fun chip(icon: Int, label: String? = null, onClick: () -> Unit) = ChipView(this, metrics, theme).apply {
        setIcon(icon)
        setLabel(label)
        setOnClickListener { onClick() }
    }

    private fun addChips() {
        showButtonLabels = settings.showButtonLabels
        showNetworkName = settings.showNetworkName
        updateChip = chip(R.drawable.ic_update, buttonLabel(R.string.chip_update)) {
            availableUpdate?.let { startActivity(UpdateMenuActivity.intent(this, it)) }
        }
        updateChip.visibility = if (availableUpdate == null) View.GONE else View.VISIBLE
        header.addChip(updateChip)
        // These are updated from many places, so they exist even when they are not in the bar.
        bellChip = chip(R.drawable.ic_bell) { onBellClicked() }
        usbChip = chip(R.drawable.ic_usb) { openOrWarn(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
        usbChip.visibility = View.GONE
        networkChip = chip(R.drawable.ic_wifi) { openOrWarn(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        settings.topBar.visible().forEach { button ->
            header.addChip(
                when (button) {
                    TopButton.NOTIFICATIONS -> bellChip
                    TopButton.USB -> usbChip
                    TopButton.NETWORK -> networkChip
                    TopButton.STATUS -> chip(R.drawable.ic_status, buttonLabel(R.string.chip_status)) { openStatus() }
                    TopButton.POWER -> chip(R.drawable.ic_power) { openPower() }
                    TopButton.SETTINGS -> chip(R.drawable.ic_settings, buttonLabel(R.string.chip_settings)) { openSettings() }
                },
            )
        }
        showNotifications()
        showConnection(connection.takeIf { it.kind != NetworkKind.NONE } ?: networkMonitor.state)
        showUsb(usbMonitor.devices())
    }

    private fun buttonLabel(text: Int): String? = if (showButtonLabels) getString(text) else null

    /** Says so once if another launcher is catching the Home button, since nothing else shows it. */
    private fun warnIfHomeIsCaptured() {
        val system = AndroidHomeSystem(this)
        val override = system.homeOverride() ?: return
        if (warnedOverride == override.pkg) return
        warnedOverride = override.pkg
        Toast.makeText(this, getString(R.string.override_toast, system.appLabel(override.pkg)), Toast.LENGTH_LONG).show()
    }

    private fun openStatus() = startActivity(Intent(this, StatusMenuActivity::class.java))

    private fun openPower() = startActivity(Intent(this, PowerMenuActivity::class.java))

    private fun showConnection(state: ConnectionState) {
        connection = state
        networkChip.setIcon(
            when (state.kind) {
                NetworkKind.WIFI -> R.drawable.ic_wifi
                NetworkKind.ETHERNET -> R.drawable.ic_cable
                NetworkKind.OTHER -> R.drawable.ic_status
                NetworkKind.NONE -> R.drawable.ic_no_network
            },
        )
        val shown = if (showNetworkName) state else state.copy(ssid = null)
        networkChip.setLabel(if (showButtonLabels) ConnectionLabel.text(shown, connectionLabels()) else null)
    }

    /** Only runs when the network changes; without Shizuku the button just says Wi-Fi. */
    private fun refreshWifiName() {
        // Reading the name starts a helper process, which is wasted when it is not shown.
        if (!showNetworkName || TopButton.NETWORK !in settings.topBar.visible()) return
        if (connection.kind != NetworkKind.WIFI) {
            wifiName = null
            return
        }
        if (wifiJob?.isActive == true) return
        wifiJob = scope.launch {
            wifiName = extras.wifiName()
            if (connection.kind == NetworkKind.WIFI) showConnection(connection.copy(ssid = wifiName))
            gateway.release()
        }
    }

    private fun connectionLabels() = ConnectionLabels(
        getString(R.string.connection_wifi),
        getString(R.string.connection_cable),
        getString(R.string.connection_other),
        getString(R.string.connection_none),
    )

    private fun showUsb(devices: List<String>) {
        usbDevices = devices
        val focused = currentFocus === usbChip
        usbChip.setLabel(devices.firstOrNull()?.takeIf { showButtonLabels })
        usbChip.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        if (focused && devices.isEmpty()) {
            header.visibleChips().firstOrNull()?.requestFocus() ?: restoreFocus()
        }
    }

    private fun showNotifications() {
        bellChip.setBadge(NotificationCenter.visible(this).size)
    }

    /** Without normal access the panel can still work through Shizuku; otherwise the help screen explains. */
    private fun onBellClicked() {
        val canRead = hasNotificationAccess(this) || gateway.state() == ShizukuState.READY
        startActivity(
            if (canRead) {
                Intent(this, NotificationsMenuActivity::class.java)
            } else {
                AccessHelpActivity.intent(this, AccessKind.NOTIFICATIONS)
            },
        )
    }

    private fun readNotificationsWithShizuku() {
        if (TopButton.NOTIFICATIONS !in settings.topBar.visible()) return
        if (hasNotificationAccess(this) || notificationJob?.isActive == true) return
        // Stopping Shizuku also clears the list here, so no old count stays on the bell.
        notificationJob = scope.launch {
            DumpsysNotifications.read(gateway)?.let { items ->
                val named = items.map { it.copy(app = applicationLabel(it.pkg)) }
                NotificationStore.replaceAll(named)
            }
            gateway.release()
        }
    }

    private fun applicationLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Once per install, after the screen is up, so it never delays the first frame. */
    private fun tryToBecomeDefault() {
        val system = AndroidHomeSystem(this)
        system.otherHomeComponent()?.let { settings.previousHome = it }
        scope.launch {
            DefaultLauncherFlow(system, gateway, settings).onAppOpened()
            gateway.release()
        }
    }

    private fun checkDefaultStatus() {
        val isDefault = AndroidHomeSystem(this).isDefaultHome()
        if (LossNotice.shouldWarn(settings.wasDefaultHome, isDefault)) {
            Toast.makeText(this, R.string.home_lost_default, Toast.LENGTH_LONG).show()
        }
        settings.wasDefaultHome = isDefault
        warnIfHomeIsCaptured()
        val hasAccess = hasNotificationAccess(this)
        if (LossNotice.shouldWarn(settings.hadNotificationAccess, hasAccess)) {
            Toast.makeText(this, R.string.notifications_lost, Toast.LENGTH_LONG).show()
        }
        settings.hadNotificationAccess = hasAccess
    }

    /** Quiet by design: a button appears in the top bar and nothing takes the focus. */
    private fun checkForUpdate() {
        if (!settings.updateCheckEnabled) {
            availableUpdate = null
            updateChip.visibility = View.GONE
            return
        }
        if (availableUpdate != null || updateJob?.isActive == true) return
        val checker = UpdateChecker(
            UrlConnectionHttpGet(),
            BuildConfig.VERSION_NAME,
            BuildConfig.UPDATE_REPO,
            BuildConfig.UPDATE_TAG_PREFIX,
            settings,
            System::currentTimeMillis,
        )
        updateJob = scope.launch {
            availableUpdate = checker.checkIfDue() ?: availableUpdate
            updateChip.visibility = if (availableUpdate == null) View.GONE else View.VISIBLE
        }
    }

    private fun openSettings() = startActivity(Intent(this, SettingsMenuActivity::class.java))

    private fun buildEmptyView(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        visibility = View.GONE
        addView(
            TextView(context).apply {
                text = getString(R.string.home_no_apps)
                setTextColor(theme.text)
                typeface = theme.face()
                setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(1.8f))
            },
        )
        addView(
            chip(R.drawable.ic_settings, getString(R.string.home_open_settings)) { openSettings() },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = metrics.cw(1.5f) },
        )
    }

    private fun paintBackground(accent: Int?) {
        when (val background = theme.background) {
            Background.AppGlow -> glow.showGlow(accent ?: theme.glowBase, theme.glowBase)
            is Background.Fixed -> glow.showGlow(background.color, theme.glowBase)
            is Background.Flat -> glow.showFlat(background.color)
            is Background.Vertical -> glow.showVertical(background.top, background.bottom)
        }
    }

    private fun rebuildForSettings() {
        buildScreen()
        showLayout()
        updateClock()
        refreshWeather()
        if (wifiName == null) refreshWifiName()
        readNotificationsWithShizuku()
    }

    fun reloadApps() {
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                val apps = source.loadApps()
                apps to TrayLoader.load(store, apps)
            }
            appsByPackage = loaded.first.associateBy { it.packageName }
            // A card being moved by hand stays where the user put it until it is dropped.
            if (movingPackage == null) layout = loaded.second
            showLayout()
            pendingMove?.let {
                pendingMove = null
                startMoving(it)
            }
        }
    }

    private fun showLayout() {
        val current = layout ?: return
        val rows = TrayLayouts.visibleTrays(current).mapNotNull { tray ->
            val apps = tray.packages.mapNotNull { appsByPackage[it] }
            if (apps.isEmpty()) null else TrayRow(tray.id, TrayNames.label(this, tray), apps)
        }
        if (rows == trayAdapter.rows && rows.isNotEmpty()) {
            if (currentFocus == null) restoreFocus()
            return
        }
        trayAdapter.submit(rows)
        trayList.layoutParams = trayList.layoutParams.apply {
            height = metrics.rowHeight * rows.size.coerceIn(1, metrics.visibleRows)
        }
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        trayList.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
        if (rows.isEmpty()) {
            if (movingPackage == null) appName.text = null
            emptyView.requestFocus()
        } else {
            restoreFocus()
        }
    }

    private fun restoreFocus() {
        val current = layout ?: return
        val target = FocusResolver.next(current, lastPackage, lastTrayIndex, lastColumn) ?: return
        val index = trayAdapter.rows.indexOfFirst { it.trayId == target.trayId }
        if (index >= 0) trayList.post { focusCard(index, target.column) }
    }

    /** On a slow first start the list can take a moment to exist, so this keeps trying for a while. */
    private fun focusCard(row: Int, column: Int, attempts: Int = FOCUS_ATTEMPTS) {
        val holder = trayList.findViewHolderForAdapterPosition(row) as? TrayAdapter.RowHolder
        if (holder != null) {
            holder.focusColumn(column)
        } else if (attempts > 0) {
            trayList.scrollToPosition(row)
            trayList.postDelayed({ focusCard(row, column, attempts - 1) }, FOCUS_RETRY_MS)
        }
    }

    /** Older Android delivers Back as a key, and that is what most TV boxes run. */
    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        idleTimer.onActivity()
        if (movingPackage != null) {
            handleMoveKey(event)
            return true
        }
        // A launcher has nothing behind it, so Back stays here.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        if (event.action == KeyEvent.ACTION_DOWN && moveFocus(event.keyCode)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Arrows carry the card, OK drops it where it is and Back puts it back. */
    @SuppressLint("GestureBackNavigation")
    private fun handleMoveKey(event: KeyEvent) {
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> MoveStep.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> MoveStep.RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> MoveStep.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> MoveStep.DOWN
            else -> null
        }
        if (step != null) {
            if (event.action == KeyEvent.ACTION_DOWN) stepMovingCard(step)
            return
        }
        if (event.action != KeyEvent.ACTION_UP) return
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> dropMovingCard()
            KeyEvent.KEYCODE_BACK -> cancelMove()
        }
    }

    private fun startMoving(pkg: String) {
        val current = store.load() ?: return
        if (current.trays.none { pkg in it.packages }) return
        layout = current
        layoutBeforeMove = current
        movingPackage = pkg
        lastPackage = pkg
        appName.visibility = View.VISIBLE
        // The instructions are longer than a name, so they use a smaller size.
        appName.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(MOVE_HINT_SIZE))
        appName.text = getString(R.string.move_hint, appsByPackage[pkg]?.label.orEmpty())
        trayAdapter.submit(emptyList())
        showLayout()
    }

    private fun stepMovingCard(step: MoveStep) {
        val pkg = movingPackage ?: return
        val current = layout ?: return
        val next = TrayLayouts.moveStep(current, pkg, step)
        if (next == current) return
        layout = next
        showLayout()
    }

    private fun dropMovingCard() {
        layout?.let(store::save)
        endMove()
    }

    private fun cancelMove() {
        layoutBeforeMove?.let { layout = it }
        endMove()
        showLayout()
    }

    private fun endMove() {
        val pkg = movingPackage
        movingPackage = null
        layoutBeforeMove = null
        appName.visibility = if (settings.showAppName) View.VISIBLE else View.INVISIBLE
        appName.setTextSize(TypedValue.COMPLEX_UNIT_PX, metrics.cwf(APP_NAME_SIZE))
        appName.text = appsByPackage[pkg]?.label
        // The moved card goes back to its normal size.
        trayAdapter.submit(trayAdapter.rows)
        restoreFocus()
    }

    /** Moves between cards and buttons directly; nested lists make Android's focus search unreliable. */
    private fun moveFocus(keyCode: Int): Boolean {
        val rows = trayAdapter.rows
        val chips = header.visibleChips()
        val chipIndex = chips.indexOf(currentFocus)
        if (chipIndex >= 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> chips.getOrNull(chipIndex - 1)?.requestFocus()
                KeyEvent.KEYCODE_DPAD_RIGHT -> chips.getOrNull(chipIndex + 1)?.requestFocus()
                KeyEvent.KEYCODE_DPAD_DOWN -> if (rows.isNotEmpty()) focusCard(lastTrayIndex, lastColumn)
                KeyEvent.KEYCODE_DPAD_UP -> Unit
                else -> return false
            }
            return true
        }
        if (rows.isEmpty() || !isInsideTrays(currentFocus)) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (lastColumn > 0) focusCard(lastTrayIndex, lastColumn - 1)
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val count = rows.getOrNull(lastTrayIndex)?.apps?.size ?: 0
                if (lastColumn < count - 1) focusCard(lastTrayIndex, lastColumn + 1)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (lastTrayIndex > 0) {
                    val above = lastTrayIndex - 1
                    focusCard(above, lastColumn.coerceAtMost(rows[above].apps.lastIndex))
                } else {
                    chips.lastOrNull()?.requestFocus()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (lastTrayIndex < rows.lastIndex) {
                    val below = lastTrayIndex + 1
                    focusCard(below, lastColumn.coerceAtMost(rows[below].apps.lastIndex))
                }
            }
            else -> return false
        }
        return true
    }

    private fun isInsideTrays(view: View?): Boolean =
        generateSequence(view?.parent) { it.parent }.any { it === trayList }

    override fun onCardFocused(trayIndex: Int, column: Int, entry: AppEntry, accent: Int?) {
        lastTrayIndex = trayIndex
        lastColumn = column
        lastPackage = entry.packageName
        if (movingPackage == null) appName.text = entry.label
        if (theme.background == Background.AppGlow) paintBackground(accent)
    }

    override fun onCardClicked(entry: AppEntry) {
        if (!source.launch(entry)) {
            Toast.makeText(this, R.string.app_could_not_open, Toast.LENGTH_SHORT).show()
            reloadApps()
        }
    }

    override fun onCardMenu(entry: AppEntry) {
        startActivity(AppMenuActivity.intent(this, entry.packageName))
    }

    override fun isMoving(entry: AppEntry) = entry.packageName == movingPackage

    override fun onStart() {
        super.onStart()
        started = true
        wallpaperLayer?.resume()
        updateClock()
        refreshWeather()
        checkForUpdate()
        readNotificationsWithShizuku()
        NotificationStore.addListener(notificationsChanged)
        showNotifications()
        networkMonitor.start { state ->
            val kindChanged = state.kind != connection.kind
            if (kindChanged) wifiName = null
            showConnection(state.copy(ssid = wifiName.takeIf { state.kind == NetworkKind.WIFI }))
            if (kindChanged || wifiName == null) refreshWifiName()
            // The check when the screen opened fails if the box had no network yet.
            if (kindChanged && state.kind != NetworkKind.NONE) checkForUpdate()
        }
        usbMonitor.start(::showUsb)
        registerReceiver(
            clockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
        )
    }

    override fun onStop() {
        super.onStop()
        started = false
        if (movingPackage != null) cancelMove()
        wallpaperLayer?.pause()
        unregisterReceiver(clockReceiver)
        NotificationStore.removeListener(notificationsChanged)
        networkMonitor.stop()
        usbMonitor.stop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val toMove = intent.getStringExtra(EXTRA_MOVE_PACKAGE)
        if (toMove != null) {
            startMoving(toMove)
            return
        }
        if (movingPackage != null) cancelMove()
        val alreadyHome = hasWindowFocus() && intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT == 0
        if (alreadyHome && trayAdapter.rows.isNotEmpty()) {
            trayList.scrollToPosition(0)
            trayList.post { focusCard(0, 0) }
        }
    }

    /** Never blocks the screen: shows the saved value first, then updates when one is due. */
    private fun refreshWeather() {
        if (!settings.weatherEnabled) {
            header.setWeather(null)
            return
        }
        val query = WeatherQuery(
            settings.manualCity?.let { Coordinates(it.latitude, it.longitude) },
            settings.tempUnit,
        )
        scope.launch { showWeather(weather.refreshIfDue(query)) }
    }

    private fun showWeather(current: CurrentWeather?) {
        header.setWeather(
            current?.let { WeatherText.display(this, it) },
        )
    }

    private fun updateClock() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val language = currentLanguage(this)
        header.setClock(
            ClockFormatter.time(hour, now.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)),
            ClockFormatter.date(now.timeInMillis, TimeZone.getDefault(), language),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
        settings.unregister(settingsChanged)
        wallpaperLayer?.release()
        gateway.removeOnConnected(shizukuConnected)
        scope.cancel()
    }

    companion object {
        private const val EXTRA_MOVE_PACKAGE = "move_package"
        private const val FOCUS_ATTEMPTS = 20
        private const val FOCUS_RETRY_MS = 50L
        private const val APP_NAME_SIZE = 3f
        private const val MOVE_HINT_SIZE = 1.8f

        /** Comes back to the home screen with this app ready to be moved with the arrows. */
        fun moveIntent(context: Context, pkg: String): Intent =
            Intent(context, HomeActivity::class.java).putExtra(EXTRA_MOVE_PACKAGE, pkg)

        /** Reading through Shizuku starts a helper process, so it happens every few minutes only. */
        private const val NOTIFICATION_POLL_TICKS = 5
        private const val IDLE_CHECK_MS = 15_000L
        private val LIVE_SETTINGS = setOf(
            AppSettings.KEY_THEME,
            AppSettings.KEY_LAYOUT,
            AppSettings.KEY_FONT,
            AppSettings.KEY_WALLPAPER,
            AppSettings.KEY_WALLPAPER_DIM,
            AppSettings.KEY_WEATHER,
            AppSettings.KEY_TOP_BAR,
            AppSettings.KEY_BUTTON_LABELS,
            AppSettings.KEY_NETWORK_NAME,
            AppSettings.KEY_SHOW_CLOCK,
            AppSettings.KEY_SHOW_DATE,
            AppSettings.KEY_CARD_SIZE,
            AppSettings.KEY_APP_NAME,
            AppSettings.KEY_TRAY_NAMES,
        )
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
