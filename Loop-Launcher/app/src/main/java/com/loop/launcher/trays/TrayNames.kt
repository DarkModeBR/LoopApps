package com.loop.launcher.trays

import android.content.Context
import com.loop.launcher.R

object TrayNames {
    fun label(context: Context, tray: Tray): String = tray.customName ?: context.getString(
        when (tray.category) {
            AppCategory.FAVORITES -> R.string.tray_favorites
            AppCategory.MOVIES -> R.string.tray_movies
            AppCategory.MUSIC -> R.string.tray_music
            AppCategory.GAMES -> R.string.tray_games
            AppCategory.OTHER, null -> R.string.tray_other
        },
    )
}
