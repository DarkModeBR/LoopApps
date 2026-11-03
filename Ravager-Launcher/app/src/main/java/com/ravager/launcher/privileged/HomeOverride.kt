package com.ravager.launcher.privileged

/**
 * Another launcher that keeps an accessibility service running can catch the Home button
 * and open itself, even when Android says this launcher is the default.
 */
data class HomeOverride(val pkg: String, val service: String) {
    companion object {
        /** [enabled] is Android's colon-separated list of enabled accessibility services. */
        fun find(enabled: String?, ownPackage: String, homePackages: Set<String>): HomeOverride? =
            services(enabled)
                .map { HomeOverride(it.substringBefore('/'), it) }
                .firstOrNull { it.pkg != ownPackage && it.pkg in homePackages }

        /** The same list without [service], for turning only that one off. */
        fun without(enabled: String?, service: String): String =
            services(enabled).filterNot { it == service }.joinToString(":")

        private fun services(enabled: String?): List<String> =
            enabled.orEmpty().split(':').filter { '/' in it }
    }
}
