package com.loop.launcher.trays

enum class AppCategory { FAVORITES, MOVIES, MUSIC, GAMES, OTHER }

/**
 * [category] links a tray to the apps it automatically receives, and survives a rename.
 * [customName] overrides the localized name of the category; a user-created tray has no category.
 */
data class Tray(
    val id: String,
    val category: AppCategory?,
    val customName: String?,
    val packages: List<String>,
)

/** Each package lives in exactly one tray or in [hidden]. */
data class TrayLayout(val trays: List<Tray>, val hidden: Set<String>)
