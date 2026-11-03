package com.ravager.launcher.wallpaper

import com.ravager.launcher.R
import java.io.File
import java.io.IOException

sealed interface Wallpaper {
    /** The background of the theme. */
    data object None : Wallpaper

    /** An image that comes with the launcher. */
    data class Bundled(val asset: String) : Wallpaper

    /** A file the user copied into the launcher. */
    data class Imported(val name: String) : Wallpaper

    companion object {
        private const val BUNDLED = "bundled:"
        private const val IMPORTED = "imported:"

        fun encode(choice: Wallpaper): String = when (choice) {
            None -> ""
            is Bundled -> BUNDLED + choice.asset
            is Imported -> IMPORTED + choice.name
        }

        fun decode(text: String?): Wallpaper = when {
            text == null -> None
            // A wallpaper removed by an update goes back to the theme background.
            text.startsWith(BUNDLED) -> Bundled(text.removePrefix(BUNDLED)).takeIf(BundledWallpapers::has) ?: None
            text.startsWith(IMPORTED) -> Imported(text.removePrefix(IMPORTED))
            else -> None
        }
    }
}

enum class WallpaperKind {
    IMAGE, GIF, VIDEO;

    companion object {
        private val IMAGES = setOf("jpg", "jpeg", "png", "webp")
        private val VIDEOS = setOf("mp4", "webm", "mkv", "m4v")

        /** Null when the launcher cannot use this file as a wallpaper. */
        fun of(name: String): WallpaperKind? {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when {
                extension in IMAGES -> IMAGE
                extension == "gif" -> GIF
                extension in VIDEOS -> VIDEO
                else -> null
            }
        }
    }
}

/** How much the wallpaper is darkened so text stays readable. Alpha goes from 0 to 255. */
enum class DimLevel(val alpha: Int, val nameRes: Int) {
    NONE(0, R.string.dim_none),
    LIGHT(0x33, R.string.dim_light),
    MEDIUM(0x66, R.string.dim_medium),
    STRONG(0x99, R.string.dim_strong),
}

data class BundledWallpaper(val asset: String, val nameRes: Int)

object BundledWallpapers {
    const val FOLDER = "wallpapers"

    val all = listOf(
        BundledWallpaper("dunas_poente.jpg", R.string.wallpaper_dunes_sunset),
        BundledWallpaper("dunas_oceano.jpg", R.string.wallpaper_dunes_ocean),
        BundledWallpaper("dunas_violeta.jpg", R.string.wallpaper_dunes_violet),
        BundledWallpaper("dunas_esmeralda.jpg", R.string.wallpaper_dunes_emerald),
        BundledWallpaper("dunas_rubi.jpg", R.string.wallpaper_dunes_ruby),
        BundledWallpaper("dunas_ambar.jpg", R.string.wallpaper_dunes_amber),
        BundledWallpaper("dunas_rosa.jpg", R.string.wallpaper_dunes_rose),
        BundledWallpaper("dunas_artico.jpg", R.string.wallpaper_dunes_arctic),
    )

    fun has(choice: Wallpaper.Bundled) = all.any { it.asset == choice.asset }
}

/** Wallpapers copied into the launcher, so they keep working after a pen drive is removed. */
class WallpaperFiles(private val dir: File, private val maxBytes: Long = DEFAULT_MAX_BYTES) {
    /** Returns the saved name, or null when the file cannot be used or copied. */
    fun import(source: File): String? {
        if (WallpaperKind.of(source.name) == null || !source.isFile || source.length() > maxBytes) return null
        dir.mkdirs()
        val name = freeName(source.name)
        val temp = File(dir, "$name.tmp")
        return try {
            source.copyTo(temp, overwrite = true)
            if (temp.renameTo(File(dir, name))) name else null
        } catch (_: IOException) {
            null
        } finally {
            temp.delete()
        }
    }

    private fun freeName(wanted: String): String {
        if (!File(dir, wanted).exists()) return wanted
        val base = wanted.substringBeforeLast('.')
        val extension = wanted.substringAfterLast('.')
        return generateSequence(2) { it + 1 }.map { "$base ($it).$extension" }.first { !File(dir, it).exists() }
    }

    fun list(): List<String> = dir.list().orEmpty().filter { WallpaperKind.of(it) != null }.sorted()

    /** Null when the file is gone, or the name tries to leave this folder. */
    fun resolve(name: String): File? {
        if ('/' in name || '\\' in name) return null
        return File(dir, name).takeIf { it.isFile }
    }

    fun delete(name: String) {
        resolve(name)?.delete()
    }

    private companion object {
        const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024
    }
}

data class BrowseEntry(val file: File, val name: String, val isFolder: Boolean)

object FileBrowser {
    /** Folders first, then only the files that can become wallpapers. */
    fun entries(dir: File): List<BrowseEntry> {
        val all = dir.listFiles().orEmpty().filterNot { it.name.startsWith(".") }
        val folders = all.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = all.filter { it.isFile && WallpaperKind.of(it.name) != null }.sortedBy { it.name.lowercase() }
        return (folders + files).map { BrowseEntry(it, it.name, it.isDirectory) }
    }
}
