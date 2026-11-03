package com.loop.launcher.apps

import com.loop.launcher.trays.AppCategory

object CategoryResolver {
    fun resolve(pkg: String, declared: AppCategory?): AppCategory =
        KnownApps.category(pkg) ?: declared ?: AppCategory.OTHER
}
