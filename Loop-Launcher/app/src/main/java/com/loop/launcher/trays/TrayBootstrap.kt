package com.loop.launcher.trays

import com.loop.launcher.apps.AppEntry
import com.loop.launcher.apps.CategoryResolver
import com.loop.launcher.apps.KnownApps

object TrayBootstrap {
    private const val MAX_FAVORITES = 6
    private val ORDER = listOf(
        AppCategory.FAVORITES, AppCategory.MOVIES, AppCategory.MUSIC, AppCategory.GAMES, AppCategory.OTHER,
    )

    fun build(apps: List<AppEntry>): TrayLayout {
        val favorites = apps
            .filter { it.hasBanner }
            .sortedBy { KnownApps.rank(it.packageName) }
            .take(MAX_FAVORITES)
            .map { it.packageName }
        val rest = apps.filter { it.packageName !in favorites }
        val trays = ORDER.map { category ->
            val packages = if (category == AppCategory.FAVORITES) {
                favorites
            } else {
                rest.filter { CategoryResolver.resolve(it.packageName, it.declaredCategory) == category }
                    .map { it.packageName }
            }
            Tray(category.name.lowercase(), category, null, packages)
        }
        return TrayLayout(trays, emptySet())
    }
}
