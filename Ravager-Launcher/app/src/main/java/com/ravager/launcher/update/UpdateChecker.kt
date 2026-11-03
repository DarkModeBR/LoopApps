package com.ravager.launcher.update

import com.ravager.launcher.net.HttpGet
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

interface UpdatePrefs {
    var lastUpdateCheck: Long
}

/**
 * Asks GitHub for the latest release at most once a day. Any failure just means no update is offered.
 * Only an answer counts as the check of the day: a box often starts before its network is up, and
 * an update on offer has to be found again after a restart.
 */
class UpdateChecker(
    private val http: HttpGet,
    private val currentVersion: String,
    private val repo: String,
    private val prefs: UpdatePrefs,
    private val clock: () -> Long,
) {
    suspend fun checkIfDue(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank() || clock() - prefs.lastUpdateCheck < DAY_MS) return@withContext null
        try {
            val response = http.get("https://api.github.com/repos/$repo/releases/latest")
            val update = if (response.code in 200..299) parse(response.body) else null
            if (update == null) prefs.lastUpdateCheck = clock()
            update
        } catch (_: IOException) {
            null
        }
    }

    private fun parse(text: String): UpdateInfo? = try {
        val root = JSONObject(text)
        val version = root.getString("tag_name")
        val assets = root.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it).getString("browser_download_url") }
            .firstOrNull { it.endsWith(".apk") }
        if (apk != null && VersionCompare.isNewer(version, currentVersion)) {
            UpdateInfo(version, apk, root.optString("body"))
        } else {
            null
        }
    } catch (_: JSONException) {
        null
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
