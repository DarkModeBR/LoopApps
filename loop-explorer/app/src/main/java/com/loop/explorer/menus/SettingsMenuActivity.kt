package com.loop.explorer.menus

import android.content.Intent
import android.os.Build
import com.loop.explorer.BuildConfig
import com.loop.explorer.R
import com.loop.explorer.access.AccessActivity
import com.loop.explorer.settings.LanguageChoice
import com.loop.explorer.settings.SortOrder
import com.loop.explorer.settings.ViewMode
import com.loop.explorer.theme.Accent
import com.loop.explorer.theme.ThemeMode
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.FontChoice
import com.loop.explorer.ui.menu.MenuPage
import com.loop.explorer.ui.menu.MenuRow
import com.loop.explorer.ui.menu.SideMenuActivity
import com.loop.explorer.settings.AppSettings as Prefs

/** Settings, grouped by subject, in the same style as Loop Launcher. */
class SettingsMenuActivity : SideMenuActivity() {
    private val prefs by lazy { Prefs(this) }

    private fun text(id: Int) = getString(id)

    override fun rootPage() = MenuPage(text(R.string.settings_title)) {
        listOfNotNull(
            MenuRow.Nav(text(R.string.category_appearance), R.drawable.ic_palette, detail = text(R.string.category_appearance_detail)) {
                open(appearancePage())
            },
            MenuRow.Nav(text(R.string.settings_view), prefs.viewMode.iconRes, value = text(prefs.viewMode.nameRes)) {
                open(
                    MenuPage(text(R.string.settings_view)) {
                        ViewMode.entries.map { mode ->
                            MenuRow.Option(text(mode.nameRes), prefs.viewMode == mode) {
                                prefs.viewMode = mode
                                refresh()
                            }
                        }
                    },
                    prefs.viewMode.ordinal,
                )
            },
            MenuRow.Nav(text(R.string.category_files), R.drawable.ic_folder, detail = text(R.string.category_files_detail)) {
                open(filesPage())
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                MenuRow.Action(text(R.string.settings_access), R.drawable.ic_check) {
                    startActivity(Intent(this, AccessActivity::class.java))
                }
            } else {
                null
            },
            MenuRow.Toggle(text(R.string.settings_updates), R.drawable.ic_update, prefs.updateCheckEnabled) {
                prefs.updateCheckEnabled = it
                refresh()
            },
            MenuRow.Nav(text(R.string.settings_about), R.drawable.ic_info) { open(aboutPage()) },
        )
    }

    private fun appearancePage() = MenuPage(text(R.string.category_appearance)) {
        listOf(
            MenuRow.Nav(text(R.string.settings_theme), R.drawable.ic_palette, value = text(prefs.themeMode.nameRes)) { open(themePage()) },
            MenuRow.Nav(text(R.string.settings_accent), R.drawable.ic_palette, value = text(prefs.accent.nameRes)) { open(accentPage()) },
            MenuRow.Nav(text(R.string.settings_font), R.drawable.ic_font, value = text(prefs.font.nameRes)) { open(fontPage()) },
            MenuRow.Nav(text(R.string.settings_language), R.drawable.ic_globe, value = languageLabel(prefs.language)) { open(languagePage()) },
        )
    }

    /** Only dark and light, as in AnExplorer; the background stays plain. */
    private fun themePage() = MenuPage(text(R.string.settings_theme)) {
        ThemeMode.entries.map { mode ->
            val look = Themes.build(mode, prefs.accent)
            MenuRow.Option(text(mode.nameRes), prefs.themeMode == mode, swatch = listOf(Themes.background(look), look.surface, look.text)) {
                prefs.themeMode = mode
                reopenOn(REOPEN_THEMES)
            }
        }
    }

    private fun accentPage() = MenuPage(text(R.string.settings_accent), { text(R.string.accent_hint) }) {
        Accent.entries.map { accent ->
            val look = Themes.build(prefs.themeMode, accent)
            val swatch = if (accent == Accent.NEUTRAL) listOf(look.bar, look.accent) else listOf(accent.color)
            MenuRow.Option(text(accent.nameRes), prefs.accent == accent, swatch = swatch) {
                prefs.accent = accent
                reopenOn(REOPEN_ACCENTS)
            }
        }
    }

    private fun fontPage() = MenuPage(text(R.string.settings_font)) {
        FontChoice.entries.map { font ->
            MenuRow.Option(text(font.nameRes), prefs.font == font) {
                prefs.font = font
                reopenOn(REOPEN_FONTS)
            }
        }
    }

    /** The menu itself changes look with the theme and the font, so it opens again on the same list. */
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
        when (reopen) {
            REOPEN_THEMES -> open(themePage(), prefs.themeMode.ordinal, animate = false)
            REOPEN_ACCENTS -> open(accentPage(), prefs.accent.ordinal, animate = false)
            else -> open(fontPage(), prefs.font.ordinal, animate = false)
        }
    }

    private fun languagePage() = MenuPage(text(R.string.settings_language)) {
        LanguageChoice.entries.map { choice ->
            MenuRow.Option(languageLabel(choice), prefs.language == choice) {
                prefs.language = choice
                recreate()
            }
        }
    }

    private fun filesPage() = MenuPage(text(R.string.category_files)) {
        listOf(
            MenuRow.Nav(text(R.string.settings_sort), R.drawable.ic_sort, value = text(prefs.sortOrder.nameRes)) {
                open(
                    MenuPage(text(R.string.settings_sort)) {
                        SortOrder.entries.map { order ->
                            MenuRow.Option(text(order.nameRes), prefs.sortOrder == order) {
                                prefs.sortOrder = order
                                refresh()
                            }
                        }
                    },
                )
            },
            MenuRow.Toggle(text(R.string.settings_hidden), R.drawable.ic_eye, prefs.showHidden) {
                prefs.showHidden = it
                refresh()
            },
        )
    }

    private fun aboutPage() = MenuPage(getString(R.string.about_title), { getString(R.string.about_version, BuildConfig.VERSION_NAME) }) {
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

    private fun languageLabel(choice: LanguageChoice) = text(
        when (choice) {
            LanguageChoice.SYSTEM -> R.string.language_system
            LanguageChoice.PT -> R.string.language_pt
            LanguageChoice.EN -> R.string.language_en
        },
    )

    private companion object {
        const val EXTRA_REOPEN = "reopen"
        const val REOPEN_THEMES = "themes"
        const val REOPEN_FONTS = "fonts"
        const val REOPEN_ACCENTS = "accents"
    }
}
