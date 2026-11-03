package com.ravager.launcher.apps

import com.ravager.launcher.trays.AppCategory

/** Popular TV apps, most useful first. Many apps declare no category, so this fills the gap. */
object KnownApps {
    val all: List<Pair<String, AppCategory>> = listOf(
        "com.google.android.youtube.tv" to AppCategory.MOVIES,
        "com.netflix.ninja" to AppCategory.MOVIES,
        "com.netflix.mediaclient" to AppCategory.MOVIES,
        "com.amazon.amazonvideo.livingroom" to AppCategory.MOVIES,
        "com.disney.disneyplus" to AppCategory.MOVIES,
        "com.wbd.stream" to AppCategory.MOVIES,
        "com.hbo.hbonow" to AppCategory.MOVIES,
        "com.globo.globotv" to AppCategory.MOVIES,
        "tv.pluto.android" to AppCategory.MOVIES,
        "com.plexapp.android" to AppCategory.MOVIES,
        "org.xbmc.kodi" to AppCategory.MOVIES,
        "org.videolan.vlc" to AppCategory.MOVIES,
        "com.stremio.one" to AppCategory.MOVIES,
        "org.jellyfin.androidtv" to AppCategory.MOVIES,
        "com.google.android.youtube.tvmusic" to AppCategory.MUSIC,
        "com.spotify.tv.android" to AppCategory.MUSIC,
        "com.deezer.android.tv" to AppCategory.MUSIC,
        "com.retroarch" to AppCategory.GAMES,
        "com.limelight" to AppCategory.GAMES,
        "com.valvesoftware.steamlink" to AppCategory.GAMES,
    )

    private val byPackage = all.toMap()

    fun category(pkg: String): AppCategory? = byPackage[pkg]
    fun rank(pkg: String): Int = all.indexOfFirst { it.first == pkg }.let { if (it < 0) Int.MAX_VALUE else it }
}
