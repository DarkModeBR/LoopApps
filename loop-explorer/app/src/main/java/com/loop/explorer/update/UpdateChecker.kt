package com.loop.explorer.update

import com.loop.explorer.net.HttpGet
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

interface UpdatePrefs {
    var lastUpdateCheck: Long
}

/**
 * Asks GitHub for the releases at most once a day. Any failure just means no update is offered.
 * Only an answer counts as the check of the day: a box often starts before its network is up, and
 * an update on offer has to be found again after a restart.
 *
 * The repository holds the releases of several apps, so only tags that start with [tagPrefix] belong
 * to this one, like `loop-launcher-v0.1.1`. An empty prefix accepts every tag.
 */
class UpdateChecker(
    private val http: HttpGet,
    private val currentVersion: String,
    private val repo: String,
    private val tagPrefix: String,
    private val prefs: UpdatePrefs,
    private val clock: () -> Long,
) {
    suspend fun checkIfDue(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (repo.isBlank() || clock() - prefs.lastUpdateCheck < DAY_MS) return@withContext null
        try {
            val response = http.get("https://api.github.com/repos/$repo/releases?per_page=$PAGE_SIZE")
            val update = if (response.code in 200..299) newest(response.body) else null
            if (update == null) prefs.lastUpdateCheck = clock()
            update
        } catch (_: IOException) {
            null
        }
    }

    /** The highest version of this app that is newer than the one running, if any. */
    fun newest(text: String): UpdateInfo? = try {
        val releases = JSONArray(text)
        (0 until releases.length())
            .map { releases.getJSONObject(it) }
            .filterNot { it.optBoolean("draft") || it.optBoolean("prerelease") }
            .mapNotNull(::ownRelease)
            .filter { VersionCompare.isNewer(it.version, currentVersion) }
            .reduceOrNull { best, next -> if (VersionCompare.isNewer(next.version, best.version)) next else best }
    } catch (_: JSONException) {
        null
    }

    private fun ownRelease(release: JSONObject): UpdateInfo? {
        val tag = release.optString("tag_name")
        if (!tag.startsWith(tagPrefix, ignoreCase = true)) return null
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it).optString("browser_download_url") }
            .firstOrNull { it.endsWith(".apk", ignoreCase = true) }
            ?: return null
        return UpdateInfo(tag.substring(tagPrefix.length).removePrefix("v").removePrefix("V"), apk, release.optString("body"))
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val PAGE_SIZE = 50
    }
}
