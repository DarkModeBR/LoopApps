package com.loop.explorer.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.loop.explorer.R
import com.loop.explorer.theme.Accent
import com.loop.explorer.theme.ThemeMode
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.FontChoice
import com.loop.explorer.update.UpdatePrefs

enum class SortOrder(val nameRes: Int) {
    NAME(R.string.sort_name),
    DATE(R.string.sort_date),
    SIZE(R.string.sort_size),
    TYPE(R.string.sort_type),
}

/** The list comes first: it is the default, and cards are the alternative. */
enum class ViewMode(val nameRes: Int, val iconRes: Int) {
    LIST(R.string.view_list, R.drawable.ic_list),
    GRID(R.string.view_grid, R.drawable.ic_grid),
}

class AppSettings(private val prefs: SharedPreferences) : UpdatePrefs {
    constructor(context: Context) : this(context.getSharedPreferences("settings", Context.MODE_PRIVATE))

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    var themeMode: ThemeMode
        get() = enumOf(prefs.getString(KEY_THEME, null), ThemeMode.DARK)
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var accent: Accent
        get() = enumOf(prefs.getString(KEY_ACCENT, null), Accent.NEUTRAL)
        set(value) = prefs.edit { putString(KEY_ACCENT, value.name) }

    /** Read by the side menus shared with Loop Launcher. */
    val themeId: String get() = Themes.id(themeMode, accent)

    var font: FontChoice
        get() = FontChoice.fromSaved(prefs.getString(KEY_FONT, null))
        set(value) = prefs.edit { putString(KEY_FONT, value.name) }

    var language: LanguageChoice
        get() = enumOf(prefs.getString(KEY_LANGUAGE, null), LanguageChoice.SYSTEM)
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value.name) }

    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)
        set(value) = prefs.edit { putBoolean(KEY_HIDDEN, value) }

    var sortOrder: SortOrder
        get() = enumOf(prefs.getString(KEY_SORT, null), SortOrder.NAME)
        set(value) = prefs.edit { putString(KEY_SORT, value.name) }

    var viewMode: ViewMode
        get() = enumOf(prefs.getString(KEY_VIEW, null), ViewMode.LIST)
        set(value) = prefs.edit { putString(KEY_VIEW, value.name) }

    var updateCheckEnabled: Boolean
        get() = prefs.getBoolean("update_check", true)
        set(value) = prefs.edit { putBoolean("update_check", value) }

    override var lastUpdateCheck: Long
        get() = prefs.getLong("last_update_check", 0L)
        set(value) = prefs.edit { putLong("last_update_check", value) }

    companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_FONT = "font"
        const val KEY_LANGUAGE = "language"
        const val KEY_HIDDEN = "show_hidden"
        const val KEY_SORT = "sort"
        const val KEY_VIEW = "view"

        /** Changes that the main screen shows right away, behind the open settings menu. */
        val LIVE = setOf(KEY_THEME, KEY_ACCENT, KEY_FONT, KEY_HIDDEN, KEY_SORT, KEY_VIEW)
    }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
