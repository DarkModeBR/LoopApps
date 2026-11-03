package com.loop.launcher.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.loop.launcher.apps.CustomNames
import com.loop.launcher.home.CardSize
import com.loop.launcher.home.TopBarConfig
import com.loop.launcher.privileged.HomeFlowPrefs
import com.loop.launcher.screensaver.SaverStyle
import com.loop.launcher.ui.FontChoice
import com.loop.launcher.update.UpdatePrefs
import com.loop.launcher.wallpaper.DimLevel
import com.loop.launcher.wallpaper.Wallpaper

enum class LayoutMode { HIGHLIGHT, COMPACT }
enum class TempUnit { C, F }

data class ManualCity(val name: String, val latitude: Double, val longitude: Double)

class AppSettings(private val prefs: SharedPreferences) : HomeFlowPrefs, UpdatePrefs {
    constructor(context: Context) : this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    var layoutMode: LayoutMode
        get() = enumOf(prefs.getString(KEY_LAYOUT, null), LayoutMode.HIGHLIGHT)
        set(value) = prefs.edit { putString(KEY_LAYOUT, value.name) }

    var wallpaper: Wallpaper
        get() = Wallpaper.decode(prefs.getString(KEY_WALLPAPER, null))
        set(value) = prefs.edit { putString(KEY_WALLPAPER, Wallpaper.encode(value)) }

    var wallpaperDim: DimLevel
        get() = enumOf(prefs.getString(KEY_WALLPAPER_DIM, null), DimLevel.MEDIUM)
        set(value) = prefs.edit { putString(KEY_WALLPAPER_DIM, value.name) }

    var font: FontChoice
        get() = FontChoice.fromSaved(prefs.getString(KEY_FONT, null))
        set(value) = prefs.edit { putString(KEY_FONT, value.name) }

    var saverStyle: SaverStyle
        get() = SaverStyle.fromSaved(prefs.getString(KEY_SAVER_STYLE, null))
        set(value) = prefs.edit { putString(KEY_SAVER_STYLE, value.name) }

    var topBar: TopBarConfig
        get() = TopBarConfig.decode(prefs.getString(KEY_TOP_BAR, null))
        set(value) = prefs.edit { putString(KEY_TOP_BAR, TopBarConfig.encode(value)) }

    var showButtonLabels: Boolean
        get() = prefs.getBoolean(KEY_BUTTON_LABELS, true)
        set(value) = prefs.edit { putBoolean(KEY_BUTTON_LABELS, value) }

    var showNetworkName: Boolean
        get() = prefs.getBoolean(KEY_NETWORK_NAME, true)
        set(value) = prefs.edit { putBoolean(KEY_NETWORK_NAME, value) }

    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_CLOCK, value) }

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DATE, true)
        set(value) = prefs.edit { putBoolean(KEY_SHOW_DATE, value) }

    var cardSize: CardSize
        get() = enumOf(prefs.getString(KEY_CARD_SIZE, null), CardSize.MEDIUM)
        set(value) = prefs.edit { putString(KEY_CARD_SIZE, value.name) }

    var showAppName: Boolean
        get() = prefs.getBoolean(KEY_APP_NAME, true)
        set(value) = prefs.edit { putBoolean(KEY_APP_NAME, value) }

    var showTrayNames: Boolean
        get() = prefs.getBoolean(KEY_TRAY_NAMES, true)
        set(value) = prefs.edit { putBoolean(KEY_TRAY_NAMES, value) }

    var appNames: Map<String, String>
        get() = CustomNames.decode(prefs.getString("app_names", null))
        set(value) = prefs.edit { putString("app_names", CustomNames.encode(value)) }

    var themeId: String
        get() = prefs.getString(KEY_THEME, null) ?: "app_color"
        set(value) = prefs.edit { putString(KEY_THEME, value) }

    var language: LanguageChoice
        get() = enumOf(prefs.getString("language", null), LanguageChoice.SYSTEM)
        set(value) = prefs.edit { putString("language", value.name) }

    var weatherEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEATHER, true)
        set(value) = prefs.edit { putBoolean(KEY_WEATHER, value) }

    var tempUnit: TempUnit
        get() = enumOf(prefs.getString("temp_unit", null), TempUnit.C)
        set(value) = prefs.edit { putString("temp_unit", value.name) }

    var manualCity: ManualCity?
        get() {
            val name = prefs.getString("city_name", null) ?: return null
            return ManualCity(
                name,
                prefs.getFloat("city_lat", 0f).toDouble(),
                prefs.getFloat("city_lon", 0f).toDouble(),
            )
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove("city_name")
            } else {
                putString("city_name", value.name)
                putFloat("city_lat", value.latitude.toFloat())
                putFloat("city_lon", value.longitude.toFloat())
            }
        }

    /** 0 turns the screensaver off. */
    var saverTimeoutMinutes: Int
        get() = prefs.getInt("saver_minutes", 5)
        set(value) = prefs.edit { putInt("saver_minutes", value) }

    var updateCheckEnabled: Boolean
        get() = prefs.getBoolean("update_check", true)
        set(value) = prefs.edit { putBoolean("update_check", value) }

    override var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit { putLong("last_update_check", value) }

    override var autoHomeAttemptDone: Boolean
        get() = prefs.getBoolean("auto_home_done", false)
        set(value) = prefs.edit { putBoolean("auto_home_done", value) }

    /** The launcher that was the default before this one, for the undo command. */
    var previousHome: String?
        get() = prefs.getString("previous_home", null)
        set(value) = prefs.edit { putString("previous_home", value) }

    var wasDefaultHome: Boolean
        get() = prefs.getBoolean("was_default_home", false)
        set(value) = prefs.edit { putBoolean("was_default_home", value) }

    var hadNotificationAccess: Boolean
        get() = prefs.getBoolean("had_notification_access", false)
        set(value) = prefs.edit { putBoolean("had_notification_access", value) }

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_LAYOUT = "layout"
        const val KEY_WALLPAPER = "wallpaper"
        const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        const val KEY_FONT = "font"
        const val KEY_WEATHER = "weather_enabled"
        const val KEY_SAVER_STYLE = "saver_style"
        const val KEY_TOP_BAR = "top_bar"
        const val KEY_BUTTON_LABELS = "button_labels"
        const val KEY_NETWORK_NAME = "network_name"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_SHOW_DATE = "show_date"
        const val KEY_CARD_SIZE = "card_size"
        const val KEY_APP_NAME = "show_app_name"
        const val KEY_TRAY_NAMES = "show_tray_names"
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
