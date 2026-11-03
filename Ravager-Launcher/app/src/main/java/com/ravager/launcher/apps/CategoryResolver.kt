package com.ravager.launcher.apps

import com.ravager.launcher.trays.AppCategory

object CategoryResolver {
    fun resolve(pkg: String, declared: AppCategory?): AppCategory =
        KnownApps.category(pkg) ?: declared ?: AppCategory.OTHER
}
