package com.ravager.launcher.apps

import org.json.JSONException
import org.json.JSONObject

/** Names the user gave to apps, by package. They only change inside the launcher. */
object CustomNames {
    fun apply(apps: List<AppEntry>, names: Map<String, String>): List<AppEntry> =
        apps.map { app -> names[app.packageName]?.let { app.copy(label = it) } ?: app }
            .sortedBy { it.label.lowercase() }

    /** A blank [name] goes back to the name of the app. */
    fun with(names: Map<String, String>, pkg: String, name: String): Map<String, String> {
        val clean = name.trim()
        return if (clean.isEmpty()) names - pkg else names + (pkg to clean)
    }

    fun encode(names: Map<String, String>): String = JSONObject(names as Map<*, *>).toString()

    fun decode(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            val json = JSONObject(text)
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (_: JSONException) {
            emptyMap()
        }
    }
}
