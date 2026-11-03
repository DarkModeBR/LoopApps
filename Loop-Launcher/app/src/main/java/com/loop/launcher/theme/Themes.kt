package com.loop.launcher.theme

import com.loop.launcher.R

sealed interface Background {
    /** Glow in the color of the focused app. */
    data object AppGlow : Background

    /** Glow in a fixed color. */
    data class Fixed(val color: Int) : Background

    /** Single color. */
    data class Flat(val color: Int) : Background

    /** Soft top-to-bottom fade. */
    data class Vertical(val top: Int, val bottom: Int) : Background
}

enum class TrayStyle {
    /** Soft translucent bar behind the cards. */
    PANEL,

    /** Cards directly on the background. */
    NONE,

    /** Bordered window with the tray name cut into its top edge, like a tiling desktop. */
    WINDOW,
}

/** Everything that makes a theme look different. Sizes are percentages of the screen width. */
data class ThemeDefinition(
    val id: String,
    val nameRes: Int,
    val background: Background,
    val glowBase: Int = GLOW_BASE,
    val surface: Int,
    val raised: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val radius: Float,
    val cardRadius: Float,
    val trayStyle: TrayStyle = TrayStyle.PANEL,
    val focusColor: Int = WHITE,
    val focusStroke: Float = 0.3f,
    val focusScale: Float = 1.07f,
    val mono: Boolean = false,
)

const val GLOW_BASE = 0xFF07080B.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

object Themes {
    /** To add a theme, add one entry here and its name to both strings.xml files. */
    val all: List<ThemeDefinition> = listOf(
        ThemeDefinition(
            id = "app_color",
            nameRes = R.string.theme_app_color,
            background = Background.AppGlow,
            surface = 0xFF14161B.toInt(),
            raised = 0xFF262932.toInt(),
            text = WHITE,
            muted = 0xFFA8ADB6.toInt(),
            accent = WHITE,
            onAccent = 0xFF101114.toInt(),
            radius = 0.8f,
            cardRadius = 1f,
        ),
        ThemeDefinition(
            id = "apple_tv",
            nameRes = R.string.theme_apple_tv,
            background = Background.Vertical(0xFF3B3E48.toInt(), 0xFF0E0F13.toInt()),
            surface = 0xFF26272D.toInt(),
            raised = 0xFF3F414A.toInt(),
            text = 0xFFF5F5F7.toInt(),
            muted = 0xFFB4B6BD.toInt(),
            accent = 0xFFF5F5F7.toInt(),
            onAccent = 0xFF1C1C1E.toInt(),
            radius = 1.4f,
            cardRadius = 1.6f,
            focusStroke = 0.12f,
            focusScale = 1.12f,
        ),
        ThemeDefinition(
            id = "atv_launcher",
            nameRes = R.string.theme_atv_launcher,
            background = Background.Vertical(0xFF103048.toInt(), 0xFF05121D.toInt()),
            surface = 0xFF0C2133.toInt(),
            raised = 0xFF17384F.toInt(),
            text = WHITE,
            muted = 0xFF9FB9CC.toInt(),
            accent = 0xFF29B6F6.toInt(),
            onAccent = 0xFF04202F.toInt(),
            radius = 0.4f,
            cardRadius = 0.5f,
            trayStyle = TrayStyle.NONE,
            focusColor = 0xFF29B6F6.toInt(),
            focusStroke = 0.35f,
            focusScale = 1.05f,
        ),
        ThemeDefinition(
            id = "mi_tv_box",
            nameRes = R.string.theme_mi_tv_box,
            background = Background.Fixed(0xFF5C2A08.toInt()),
            glowBase = 0xFF101010.toInt(),
            surface = 0xFF1A1A1A.toInt(),
            raised = 0xFF2E2E2E.toInt(),
            text = 0xFFF4F4F4.toInt(),
            muted = 0xFFA6A6A6.toInt(),
            accent = 0xFFFF6900.toInt(),
            onAccent = 0xFF1A0C00.toInt(),
            radius = 0.8f,
            cardRadius = 0.9f,
            focusColor = 0xFFFF6900.toInt(),
            focusStroke = 0.35f,
        ),
        ThemeDefinition(
            id = "android_google_tv",
            nameRes = R.string.theme_android_google_tv,
            background = Background.AppGlow,
            glowBase = 0xFF202124.toInt(),
            surface = 0xFF292A2D.toInt(),
            raised = 0xFF45484D.toInt(),
            text = 0xFFE8EAED.toInt(),
            muted = 0xFFA6ABB1.toInt(),
            accent = 0xFF8AB4F8.toInt(),
            onAccent = 0xFF202124.toInt(),
            radius = 1.2f,
            cardRadius = 1.2f,
            focusScale = 1.08f,
        ),
        ThemeDefinition(
            id = "omarchy_tv",
            nameRes = R.string.theme_omarchy_tv,
            background = Background.Flat(0xFF1A1B26.toInt()),
            surface = 0xFF1F2335.toInt(),
            raised = 0xFF3B4261.toInt(),
            text = 0xFFC0CAF5.toInt(),
            muted = 0xFF8F98C2.toInt(),
            accent = 0xFF7AA2F7.toInt(),
            onAccent = 0xFF1A1B26.toInt(),
            radius = 0f,
            cardRadius = 0f,
            trayStyle = TrayStyle.WINDOW,
            focusColor = 0xFF7AA2F7.toInt(),
            focusStroke = 0.3f,
            focusScale = 1f,
            mono = true,
        ),
    )

    fun byId(id: String): ThemeDefinition = all.firstOrNull { it.id == id } ?: all.first()
}
