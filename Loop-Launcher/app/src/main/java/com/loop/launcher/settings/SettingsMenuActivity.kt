package com.loop.launcher.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.loop.launcher.BuildConfig
import com.loop.launcher.R
import com.loop.launcher.apps.AppMenus
import com.loop.launcher.home.CardSize
import com.loop.launcher.home.TopButton
import com.loop.launcher.net.UrlConnectionHttpGet
import com.loop.launcher.notifications.NotificationStatus
import com.loop.launcher.notifications.hasNotificationAccess
import com.loop.launcher.privileged.AccessHelpActivity
import com.loop.launcher.privileged.AccessKind
import com.loop.launcher.privileged.AndroidHomeSystem
import com.loop.launcher.privileged.HomeCaptureMenus
import com.loop.launcher.privileged.ShizukuGateway
import com.loop.launcher.privileged.ShizukuState
import com.loop.launcher.screensaver.SaverActivity
import com.loop.launcher.screensaver.SaverStyle
import com.loop.launcher.screensaver.SaverViews
import com.loop.launcher.theme.Themes
import com.loop.launcher.trays.TrayMenus
import com.loop.launcher.ui.FontChoice
import com.loop.launcher.ui.ScreenErrors
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import com.loop.launcher.ui.menu.SideMenuActivity
import com.loop.launcher.ui.opened
import com.loop.launcher.ui.openOrWarn
import com.loop.launcher.wallpaper.WallpaperMenus
import com.loop.launcher.weather.OpenMeteoClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.plus

/** Settings, grouped by subject. */
class SettingsMenuActivity : SideMenuActivity() {
    private val wallpapers by lazy { WallpaperMenus(this, settings) }
    private val scope = MainScope() + ScreenErrors.handler()
    private val homeCapture by lazy { HomeCaptureMenus(this, scope) }
    private var afterFileAccess: (() -> Unit)? = null

    private fun text(id: Int) = getString(id)

    override fun rootPage() = MenuPage(text(R.string.settings_title)) {
        listOf(
            MenuRow.Nav(
                text(R.string.category_appearance),
                R.drawable.ic_palette,
                detail = text(R.string.category_appearance_detail),
            ) { open(appearancePage()) },
            MenuRow.Nav(
                text(R.string.category_home),
                R.drawable.ic_grid,
                detail = text(R.string.category_home_detail),
            ) { open(homePage()) },
            MenuRow.Nav(
                text(R.string.category_top_bar),
                R.drawable.ic_status,
                detail = text(R.string.category_top_bar_detail),
            ) { open(topBarPage()) },
            MenuRow.Nav(
                text(R.string.category_weather),
                R.drawable.ic_cloud,
                detail = text(R.string.category_weather_detail),
            ) { open(weatherPage()) },
            MenuRow.Nav(
                text(R.string.category_saver),
                R.drawable.ic_moon,
                value = text(settings.saverStyle.nameRes),
            ) { open(saverPage(), settings.saverStyle.ordinal + 1) },
            MenuRow.Nav(
                text(R.string.category_system),
                R.drawable.ic_settings,
                detail = text(R.string.category_system_detail),
            ) { open(systemPage()) },
            MenuRow.Nav(text(R.string.settings_about), R.drawable.ic_info) { open(aboutPage()) },
        )
    }

    private fun appearancePage() = MenuPage(text(R.string.category_appearance)) {
        listOf(
            MenuRow.Nav(text(R.string.settings_theme), R.drawable.ic_palette, value = text(Themes.byId(settings.themeId).nameRes)) {
                open(themePage())
            },
            MenuRow.Nav(text(R.string.settings_wallpaper), R.drawable.ic_image, value = wallpapers.currentName()) {
                open(wallpapers.rootPage())
            },
            MenuRow.Nav(text(R.string.settings_font), R.drawable.ic_font, value = text(settings.font.nameRes)) {
                open(fontPage())
            },
            MenuRow.Nav(text(R.string.settings_layout), R.drawable.ic_grid, value = layoutLabel(settings.layoutMode)) {
                open(layoutPage())
            },
            MenuRow.Nav(text(R.string.settings_language), R.drawable.ic_globe, value = languageLabel(settings.language)) {
                open(languagePage())
            },
        )
    }

    private fun themePage() = MenuPage(text(R.string.settings_theme), { text(R.string.theme_hint) }) {
        Themes.all.map { theme ->
            MenuRow.Option(
                text(theme.nameRes),
                settings.themeId == theme.id,
                swatch = listOf(theme.surface, theme.accent, theme.text),
            ) {
                settings.themeId = theme.id
                reopenOn(REOPEN_THEMES)
            }
        }
    }

    /** The menu itself changes look with the theme, so it reopens on the theme list. */
    private fun reopenOn(page: String) {
        startActivity(
            Intent(this, SettingsMenuActivity::class.java)
                .putExtra(EXTRA_REOPEN, page)
                .putExtra(EXTRA_SKIP_ENTRANCE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onPostCreate(savedInstanceState: android.os.Bundle?) {
        super.onPostCreate(savedInstanceState)
        val reopen = intent.getStringExtra(EXTRA_REOPEN) ?: return
        intent.removeExtra(EXTRA_REOPEN)
        open(appearancePage(), animate = false)
        if (reopen == REOPEN_THEMES) {
            open(themePage(), Themes.all.indexOfFirst { it.id == settings.themeId }, animate = false)
        } else {
            open(fontPage(), settings.font.ordinal, animate = false)
        }
    }

    private fun fontPage() = MenuPage(text(R.string.settings_font), { text(R.string.font_hint) }) {
        FontChoice.entries.map { font ->
            MenuRow.Option(text(font.nameRes), settings.font == font) {
                settings.font = font
                reopenOn(REOPEN_FONTS)
            }
        }
    }

    /** Asks for permission to read files first, when the device needs it. */
    fun withFileAccess(action: () -> Unit) {
        val needed = filePermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) {
            action()
        } else {
            afterFileAccess = action
            requestPermissions(needed.toTypedArray(), REQUEST_FILES)
        }
    }

    private fun filePermissions(): List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_FILES) return
        val action = afterFileAccess
        afterFileAccess = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            action?.invoke()
        } else {
            Toast.makeText(this, R.string.files_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private fun layoutPage() = MenuPage(text(R.string.settings_layout)) {
        LayoutMode.entries.map { mode ->
            MenuRow.Option(layoutLabel(mode), settings.layoutMode == mode) {
                settings.layoutMode = mode
                refresh()
            }
        }
    }

    private fun languagePage() = MenuPage(text(R.string.settings_language)) {
        LanguageChoice.entries.map { choice ->
            MenuRow.Option(languageLabel(choice), settings.language == choice) {
                settings.language = choice
                recreate()
            }
        }
    }

    private fun homePage() = MenuPage(text(R.string.category_home)) {
        listOf(
            MenuRow.Section(text(R.string.home_section_cards)),
            MenuRow.Nav(text(R.string.settings_card_size), R.drawable.ic_grid, value = text(settings.cardSize.nameRes)) {
                open(
                    choicePage(text(R.string.settings_card_size), CardSize.entries, { settings.cardSize }, CardSize::nameRes) {
                        settings.cardSize = it
                    },
                )
            },
            MenuRow.Toggle(text(R.string.settings_app_name), R.drawable.ic_font, settings.showAppName) {
                settings.showAppName = it
                refresh()
            },
            MenuRow.Toggle(text(R.string.settings_tray_names), R.drawable.ic_edit, settings.showTrayNames) {
                settings.showTrayNames = it
                refresh()
            },
            MenuRow.Section(text(R.string.home_section_organize)),
            MenuRow.Nav(text(R.string.settings_organize_trays), R.drawable.ic_grid) { open(TrayMenus(this).rootPage()) },
            MenuRow.Nav(text(R.string.settings_all_apps), R.drawable.ic_apps) { open(AppMenus(this).allAppsPage()) },
        )
    }

    private fun <T> choicePage(title: String, choices: List<T>, current: () -> T, name: (T) -> Int, pick: (T) -> Unit) =
        MenuPage(title) {
            choices.map { choice ->
                MenuRow.Option(text(name(choice)), current() == choice) {
                    pick(choice)
                    refresh()
                }
            }
        }

    private fun topBarPage() = MenuPage(text(R.string.category_top_bar)) {
        val config = settings.topBar
        buildList {
            add(MenuRow.Section(text(R.string.top_bar_buttons)))
            config.order.forEach { button ->
                add(
                    MenuRow.Toggle(text(button.nameRes), button.iconRes, button !in config.hidden) { on ->
                        settings.topBar = settings.topBar.withVisible(button, on)
                        refresh()
                    },
                )
            }
            if (TopButton.SETTINGS in config.hidden) add(MenuRow.Note(text(R.string.top_bar_settings_hidden)))
            add(MenuRow.Nav(text(R.string.top_bar_order), R.drawable.ic_move) { open(topBarOrderPage()) })
            add(MenuRow.Section(text(R.string.top_bar_text)))
            add(
                MenuRow.Toggle(text(R.string.top_bar_labels), R.drawable.ic_font, settings.showButtonLabels) {
                    settings.showButtonLabels = it
                    refresh()
                },
            )
            add(
                MenuRow.Toggle(text(R.string.top_bar_network_name), R.drawable.ic_wifi, settings.showNetworkName) {
                    settings.showNetworkName = it
                    refresh()
                },
            )
            add(MenuRow.Section(text(R.string.top_bar_information)))
            add(
                MenuRow.Toggle(text(R.string.top_bar_clock), R.drawable.ic_moon, settings.showClock) {
                    settings.showClock = it
                    refresh()
                },
            )
            add(
                MenuRow.Toggle(text(R.string.top_bar_date), R.drawable.ic_grid, settings.showDate) {
                    settings.showDate = it
                    refresh()
                },
            )
            add(
                MenuRow.Toggle(text(R.string.settings_weather), R.drawable.ic_cloud, settings.weatherEnabled) {
                    settings.weatherEnabled = it
                    refresh()
                },
            )
        }
    }

    /** Listed in the order the buttons appear, from left to right. */
    private fun topBarOrderPage() = MenuPage(text(R.string.top_bar_order), { text(R.string.top_bar_order_hint) }) {
        settings.topBar.order.map { button ->
            MenuRow.Nav(text(button.nameRes), button.iconRes) { open(topBarMovePage(button)) }
        }
    }

    private fun topBarMovePage(button: TopButton) = MenuPage(text(button.nameRes)) {
        listOf(
            MenuRow.Action(text(R.string.top_bar_move_left), R.drawable.ic_arrow_up) {
                settings.topBar = settings.topBar.moved(button, -1)
                refresh()
            },
            MenuRow.Action(text(R.string.top_bar_move_right), R.drawable.ic_arrow_down) {
                settings.topBar = settings.topBar.moved(button, 1)
                refresh()
            },
        )
    }

    private fun weatherPage() = MenuPage(text(R.string.category_weather)) {
        listOf(
            MenuRow.Toggle(text(R.string.settings_weather), R.drawable.ic_cloud, settings.weatherEnabled) {
                settings.weatherEnabled = it
                refresh()
            },
            MenuRow.Nav(text(R.string.settings_temp_unit), R.drawable.ic_thermometer, value = unitLabel(settings.tempUnit)) {
                open(
                    MenuPage(text(R.string.settings_temp_unit)) {
                        TempUnit.entries.map { unit ->
                            MenuRow.Option(unitLabel(unit), settings.tempUnit == unit) {
                                settings.tempUnit = unit
                                refresh()
                            }
                        }
                    },
                )
            },
            MenuRow.Nav(
                text(R.string.settings_city),
                R.drawable.ic_pin,
                value = settings.manualCity?.name ?: text(R.string.city_auto),
            ) { open(cityPage()) },
        )
    }

    private fun cityPage(): MenuPage {
        var typed = settings.manualCity?.name.orEmpty()
        return MenuPage(text(R.string.settings_city), { text(R.string.city_hint) }) {
            listOf(
                MenuRow.Input(text(R.string.tray_name_city), typed, { typed = it }) { saveCity(typed.trim()) },
                MenuRow.Action(text(R.string.action_save), R.drawable.ic_check) { saveCity(typed.trim()) },
                MenuRow.Action(text(R.string.city_use_auto), R.drawable.ic_pin) { saveCity("") },
            )
        }
    }

    private fun saveCity(name: String) {
        if (name.isEmpty()) {
            settings.manualCity = null
            goBack()
            return
        }
        Thread {
            val city = runCatching {
                val response = UrlConnectionHttpGet().get(OpenMeteoClient.cityUrl(name))
                if (response.code in 200..299) OpenMeteoClient.parseCity(response.body) else null
            }.getOrNull()
            runOnUiThread {
                if (city == null) {
                    Toast.makeText(this, R.string.city_not_found, Toast.LENGTH_LONG).show()
                } else {
                    settings.manualCity = city
                    if (!isDestroyed) goBack()
                }
            }
        }.start()
    }

    private fun saverPage() = MenuPage(text(R.string.category_saver), { text(R.string.saver_hint) }, hasPreview = true) {
        val models = SaverStyle.entries.map { model ->
            MenuRow.Option(text(model.nameRes), settings.saverStyle == model, onFocus = { previewSaver(model) }) {
                settings.saverStyle = model
                refresh()
            }
        }
        val times = SAVER_MINUTES.map { minutes ->
            MenuRow.Option(saverLabel(minutes), settings.saverTimeoutMinutes == minutes, onFocus = { previewSaver(settings.saverStyle) }) {
                settings.saverTimeoutMinutes = minutes
                refresh()
            }
        }
        listOf(MenuRow.Section(text(R.string.saver_section_models))) + models + listOf(
            MenuRow.Action(text(R.string.saver_fullscreen), R.drawable.ic_open, onFocus = { previewSaver(settings.saverStyle) }) {
                startActivity(SaverActivity.preview(this, settings.saverStyle))
            },
            MenuRow.Section(text(R.string.settings_saver)),
        ) + times + listOf(
            MenuRow.Section(text(R.string.saver_system_section)),
            MenuRow.Note(text(R.string.saver_system_note)),
            MenuRow.Action(text(R.string.saver_system_open), R.drawable.ic_moon) {
                openSystemSaverSettings()
            },
        )
    }

    /** TV systems have this screen but do not answer to Android's name for it. */
    private fun openSystemSaverSettings() {
        val exact = listOf(
            Intent(Settings.ACTION_DREAM_SETTINGS),
            Intent().setClassName(TV_SETTINGS, "$TV_SETTINGS.device.display.daydream.DaydreamActivity"),
        )
        if (exact.any { opened { startActivity(it) } }) return
        Toast.makeText(this, R.string.saver_system_fallback, Toast.LENGTH_LONG).show()
        openOrWarn(Intent(Settings.ACTION_SETTINGS))
    }

    private fun previewSaver(model: SaverStyle) {
        showPreview(model) { SaverViews.create(this, model, preview = true) }
    }

    private fun systemPage() = MenuPage(text(R.string.category_system)) {
        listOf(
            MenuRow.Nav(
                text(R.string.settings_set_default),
                R.drawable.ic_home,
                value = defaultLauncherValue(),
            ) { startActivity(AccessHelpActivity.intent(this, AccessKind.DEFAULT_LAUNCHER)) },
            MenuRow.Nav(text(R.string.capture_title), R.drawable.ic_home, value = homeCapture.value()) {
                open(homeCapture.page())
            },
            MenuRow.Nav(
                text(R.string.settings_notifications),
                R.drawable.ic_bell,
                value = notificationValue(),
            ) { startActivity(AccessHelpActivity.intent(this, AccessKind.NOTIFICATIONS)) },
            MenuRow.Toggle(text(R.string.settings_updates), R.drawable.ic_update, settings.updateCheckEnabled) {
                settings.updateCheckEnabled = it
                refresh()
            },
            MenuRow.Action(text(R.string.settings_network), R.drawable.ic_wifi) {
                openOrWarn(Intent(Settings.ACTION_WIFI_SETTINGS))
            },
            MenuRow.Action(text(R.string.settings_system), R.drawable.ic_settings) {
                openOrWarn(Intent(Settings.ACTION_SETTINGS))
            },
        )
    }

    private fun defaultLauncherValue(): String {
        val system = AndroidHomeSystem(this)
        return text(
            when {
                !system.isDefaultHome() -> R.string.value_no
                system.homeOverride() != null -> R.string.home_captured
                else -> R.string.value_yes
            },
        )
    }

    private fun notificationValue(): String {
        val status = NotificationStatus.of(hasNotificationAccess(this), ShizukuGateway(this).state() == ShizukuState.READY)
        return text(
            when (status) {
                NotificationStatus.ON -> R.string.notifications_on
                NotificationStatus.THROUGH_SHIZUKU -> R.string.notifications_shizuku
                NotificationStatus.OFF -> R.string.notifications_off
            },
        )
    }

    private fun aboutPage() = MenuPage(text(R.string.about_title), { getString(R.string.about_version, BuildConfig.VERSION_NAME) }) {
        listOf(
            MenuRow.Section(text(R.string.about_creator_title)),
            MenuRow.Note(text(R.string.about_creator)),
            MenuRow.Section(text(R.string.about_license_title)),
            MenuRow.Note(text(R.string.about_license)),
            MenuRow.Section(text(R.string.about_privacy_title)),
            MenuRow.Note(text(R.string.about_privacy)),
            MenuRow.Section(text(R.string.about_credits_title)),
            MenuRow.Note(text(R.string.about_credits)),
        )
    }

    private fun layoutLabel(mode: LayoutMode) =
        text(if (mode == LayoutMode.HIGHLIGHT) R.string.layout_highlight else R.string.layout_compact)

    private fun languageLabel(choice: LanguageChoice) = text(
        when (choice) {
            LanguageChoice.SYSTEM -> R.string.language_system
            LanguageChoice.PT -> R.string.language_pt
            LanguageChoice.EN -> R.string.language_en
        },
    )

    private fun unitLabel(unit: TempUnit) = if (unit == TempUnit.C) "°C" else "°F"

    private fun saverLabel(minutes: Int) =
        if (minutes == 0) text(R.string.saver_off) else getString(R.string.saver_minutes, minutes)

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private companion object {
        const val EXTRA_REOPEN = "reopen"
        const val REOPEN_THEMES = "themes"
        const val REOPEN_FONTS = "fonts"
        const val REQUEST_FILES = 4031
        const val TV_SETTINGS = "com.android.tv.settings"
        val SAVER_MINUTES = listOf(0, 1, 5, 10, 30)
    }
}
