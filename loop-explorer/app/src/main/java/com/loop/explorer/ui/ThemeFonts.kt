package com.loop.explorer.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import com.loop.explorer.R
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.theme.ThemeDefinition

enum class FontWeight { LIGHT, REGULAR, MEDIUM }

enum class FontChoice(val nameRes: Int) {
    /** Poppins, except in the terminal-style theme, which keeps its monospaced look. */
    AUTO(R.string.font_auto),
    POPPINS(R.string.font_poppins),
    BARLOW(R.string.font_barlow),
    PROMPT(R.string.font_prompt),
    OXYGEN(R.string.font_oxygen);

    /** Null means the theme's monospaced system font. */
    fun familyFor(themeIsMono: Boolean): FontChoice? = when {
        this != AUTO -> this
        themeIsMono -> null
        else -> POPPINS
    }

    fun assetFor(weight: FontWeight) = "fonts/${name.lowercase()}_${weight.name.lowercase()}.ttf"

    companion object {
        fun fromSaved(name: String?): FontChoice = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Keeps the bundled fonts loaded, because reading them from the app package is slow. */
object AppFonts {
    private var assets: AssetManager? = null
    private var choice = FontChoice.AUTO
    private val loaded = HashMap<String, Typeface>()

    /** Reads the font chosen in the settings. Called whenever a screen is created. */
    fun use(context: Context) {
        assets = context.applicationContext?.assets ?: context.assets
        choice = AppSettings(context).font
    }

    fun face(themeIsMono: Boolean, weight: FontWeight): Typeface {
        val family = choice.familyFor(themeIsMono) ?: return Typeface.MONOSPACE
        val asset = family.assetFor(weight)
        loaded[asset]?.let { return it }
        val manager = assets ?: return Typeface.DEFAULT
        val typeface = runCatching { Typeface.createFromAsset(manager, asset) }.getOrDefault(Typeface.DEFAULT)
        loaded[asset] = typeface
        return typeface
    }
}

fun ThemeDefinition.face(weight: FontWeight = FontWeight.REGULAR): Typeface = AppFonts.face(mono, weight)
