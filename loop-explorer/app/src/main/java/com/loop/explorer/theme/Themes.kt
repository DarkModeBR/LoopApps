package com.loop.explorer.theme

import com.loop.explorer.R

sealed interface Background {
    /** Single color. The explorer only uses this one: a plain dark or light background. */
    data class Flat(val color: Int) : Background

    /** Kept so the side menus shared with Loop Launcher still compile; not used here. */
    data object AppGlow : Background
    data class Fixed(val color: Int) : Background
    data class Vertical(val top: Int, val bottom: Int) : Background
}

enum class TrayStyle { PANEL, NONE, WINDOW }

/** Everything that makes a theme look different. Sizes are percentages of the screen width. */
data class ThemeDefinition(
    val id: String,
    val nameRes: Int,
    val background: Background,
    val glowBase: Int = 0xFF000000.toInt(),
    val surface: Int,
    val raised: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val radius: Float,
    val cardRadius: Float,
    val trayStyle: TrayStyle = TrayStyle.NONE,
    val focusColor: Int = 0xFFFFFFFF.toInt(),
    val focusStroke: Float = 0.25f,
    val focusScale: Float = 1.04f,
    val mono: Boolean = false,
    val dark: Boolean = true,
    /** Top bar and side panel header. */
    val bar: Int = accent,
    val onBar: Int = onAccent,
    /** Circle behind folder icons. */
    val folder: Int = accent,
    /** Black and gray: every file icon uses the same gray, without a color per type. */
    val neutral: Boolean = false,
)

enum class ThemeMode(val nameRes: Int) {
    DARK(R.string.theme_dark),
    LIGHT(R.string.theme_light),
}

/**
 * The highlight color the user picks; it paints the top bar, the focus and the buttons.
 * [NEUTRAL] is the default: black and gray, with white (or black, in the light theme) for the focus.
 */
enum class Accent(val nameRes: Int, val color: Int) {
    NEUTRAL(R.string.accent_neutral, 0xFF9E9E9E.toInt()),
    BLUE(R.string.accent_blue, 0xFF1E88E5.toInt()),
    TEAL(R.string.accent_teal, 0xFF00897B.toInt()),
    GREEN(R.string.accent_green, 0xFF43A047.toInt()),
    AMBER(R.string.accent_amber, 0xFFFFB300.toInt()),
    ORANGE(R.string.accent_orange, 0xFFF4511E.toInt()),
    RED(R.string.accent_red, 0xFFE53935.toInt()),
    PINK(R.string.accent_pink, 0xFFD81B60.toInt()),
    PURPLE(R.string.accent_purple, 0xFF8E24AA.toInt()),
}

object Themes {
    /** The id joins the mode and the color, like "DARK:BLUE", so the shared menus can keep asking by id. */
    fun id(mode: ThemeMode, accent: Accent) = "${mode.name}:${accent.name}"

    val default: ThemeDefinition get() = build(ThemeMode.DARK, Accent.NEUTRAL)

    fun byId(id: String): ThemeDefinition {
        val mode = ThemeMode.entries.firstOrNull { it.name == id.substringBefore(':') } ?: ThemeMode.DARK
        val accent = Accent.entries.firstOrNull { it.name == id.substringAfter(':', "") } ?: Accent.NEUTRAL
        return build(mode, accent)
    }

    fun build(mode: ThemeMode, accent: Accent): ThemeDefinition {
        val dark = mode == ThemeMode.DARK
        val neutral = accent == Accent.NEUTRAL
        val color = when {
            !neutral -> accent.color
            dark -> 0xFFE0E0E0.toInt()
            else -> 0xFF212121.toInt()
        }
        val bar = when {
            !neutral -> color
            dark -> 0xFF262626.toInt()
            else -> 0xFFE0E0E0.toInt()
        }
        return ThemeDefinition(
            id = id(mode, accent),
            nameRes = mode.nameRes,
            background = Background.Flat(if (dark) 0xFF121212.toInt() else 0xFFF5F5F5.toInt()),
            surface = if (dark) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt(),
            raised = if (dark) 0xFF2C2C2C.toInt() else 0xFFE6E6E6.toInt(),
            text = if (dark) 0xFFEDEDED.toInt() else 0xFF212121.toInt(),
            muted = if (dark) 0xFFA3A3A3.toInt() else 0xFF6B6B6B.toInt(),
            accent = color,
            onAccent = onColor(color),
            radius = 0.5f,
            cardRadius = 0.6f,
            focusColor = color,
            dark = dark,
            bar = bar,
            onBar = onColor(bar),
            folder = if (neutral) 0xFF757575.toInt() else color,
            neutral = neutral,
        )
    }

    /** White on dark colors and black on light ones, so text on the color stays readable. */
    fun onColor(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val light = (0.299 * r + 0.587 * g + 0.114 * b) > 170
        return if (light) 0xFF1A1A1A.toInt() else 0xFFFFFFFF.toInt()
    }

    fun background(theme: ThemeDefinition): Int = (theme.background as? Background.Flat)?.color ?: theme.surface

    /** "#RRGGBB" for the web page, which follows the same colors. */
    fun hex(color: Int) = String.format("#%06X", color and 0xFFFFFF)
}
