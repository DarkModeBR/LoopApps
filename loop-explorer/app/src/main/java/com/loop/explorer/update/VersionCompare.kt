package com.loop.explorer.update

object VersionCompare {
    /** A tag that is not a version number is never treated as newer. */
    fun isNewer(candidate: String, current: String): Boolean {
        val new = parts(candidate) ?: return false
        val old = parts(current) ?: return false
        for (index in 0 until maxOf(new.size, old.size)) {
            val a = new.getOrElse(index) { 0 }
            val b = old.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parts(version: String): List<Int>? {
        val values = version.trim().removePrefix("v").split('.').map { it.toIntOrNull() }
        return if (values.any { it == null }) null else values.filterNotNull()
    }
}
