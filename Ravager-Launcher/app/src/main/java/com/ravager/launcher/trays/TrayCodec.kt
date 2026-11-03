package com.ravager.launcher.trays

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object TrayCodec {
    fun encode(layout: TrayLayout): String = JSONObject()
        .put(
            "trays",
            JSONArray(
                layout.trays.map { tray ->
                    JSONObject()
                        .put("id", tray.id)
                        .put("category", tray.category?.name ?: JSONObject.NULL)
                        .put("name", tray.customName ?: JSONObject.NULL)
                        .put("packages", JSONArray(tray.packages))
                },
            ),
        )
        .put("hidden", JSONArray(layout.hidden.toList()))
        .toString()

    /** Returns null for anything unreadable; the caller rebuilds the layout from the installed apps. */
    fun decode(text: String): TrayLayout? {
        if (text.isBlank()) return null
        return try {
            val root = JSONObject(text)
            TrayLayout(
                trays = root.getJSONArray("trays").objects().map { tray ->
                    Tray(
                        id = tray.getString("id"),
                        category = tray.optString("category").let { name ->
                            AppCategory.entries.firstOrNull { it.name == name }
                        },
                        customName = if (tray.isNull("name")) null else tray.getString("name"),
                        packages = tray.getJSONArray("packages").strings(),
                    )
                },
                hidden = root.getJSONArray("hidden").strings().toSet(),
            )
        } catch (_: JSONException) {
            null
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
