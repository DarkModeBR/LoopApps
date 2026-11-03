package com.loop.explorer.files

import android.webkit.MimeTypeMap
import com.loop.explorer.R
import com.loop.explorer.settings.SortOrder
import java.io.File
import java.util.Locale

/** What a file is, from its extension. [color] fills the circle behind its icon, like in AnExplorer. */
enum class FileKind(val iconRes: Int, val color: Int) {
    FOLDER(R.drawable.ic_folder, 0),
    IMAGE(R.drawable.ic_image, 0xFF43A047.toInt()),
    VIDEO(R.drawable.ic_video, 0xFFE53935.toInt()),
    AUDIO(R.drawable.ic_music, 0xFF8E24AA.toInt()),
    APK(R.drawable.ic_package, 0xFF7CB342.toInt()),
    ZIP(R.drawable.ic_zip, 0xFFFB8C00.toInt()),
    TEXT(R.drawable.ic_text, 0xFF1E88E5.toInt()),
    OTHER(R.drawable.ic_file, 0xFF757575.toInt()),
    ;

    companion object {
        private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        private val VIDEOS = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp", "flv", "wmv", "mpg", "mpeg")
        private val AUDIOS = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "mka")
        private val TEXTS = setOf("txt", "log", "md", "json", "xml", "srt", "csv", "ini", "conf", "cfg", "m3u", "m3u8")

        fun of(file: File): FileKind = if (file.isDirectory) FOLDER else of(file.name)

        fun of(name: String): FileKind = when (extension(name)) {
            in IMAGES -> IMAGE
            in VIDEOS -> VIDEO
            in AUDIOS -> AUDIO
            "apk" -> APK
            "zip" -> ZIP
            in TEXTS -> TEXT
            else -> OTHER
        }

        fun extension(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

        fun mime(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension(name)) ?: when (of(name)) {
                APK -> "application/vnd.android.package-archive"
                ZIP -> "application/zip"
                TEXT -> "text/plain"
                else -> "application/octet-stream"
            }
    }
}

data class FileItem(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val kind: FileKind,
) {
    val name: String get() = file.name

    companion object {
        fun of(file: File): FileItem {
            val dir = file.isDirectory
            return FileItem(file, dir, if (dir) 0L else file.length(), file.lastModified(), FileKind.of(file))
        }
    }
}

object Listing {
    /** Null when the folder cannot be read. Folders always come first. */
    fun list(dir: File, showHidden: Boolean, order: SortOrder): List<FileItem>? {
        val children = dir.listFiles() ?: return null
        val items = children.filter { showHidden || !it.name.startsWith(".") }.map(FileItem::of)
        val byName = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val inside = when (order) {
            SortOrder.NAME -> byName
            SortOrder.DATE -> compareByDescending<FileItem> { it.modified }.then(byName)
            SortOrder.SIZE -> compareByDescending<FileItem> { it.size }.then(byName)
            SortOrder.TYPE -> compareBy<FileItem> { FileKind.extension(it.name) }.then(byName)
        }
        return items.sortedWith(compareByDescending<FileItem> { it.isDir }.then(inside))
    }

    /** The files of the same kind in a folder, in screen order, for moving to the next photo or song. */
    fun siblings(file: File, showHidden: Boolean, order: SortOrder, kinds: Set<FileKind>): List<File> {
        val parent = file.parentFile ?: return listOf(file)
        return list(parent, showHidden, order).orEmpty()
            .filter { !it.isDir && it.kind in kinds }
            .map { it.file }
            .ifEmpty { listOf(file) }
    }
}

object Sizes {
    fun text(bytes: Long, language: String): String {
        val units = listOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        val number = if (unit == 0 || value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value)
        return (if (language == "pt") number.replace('.', ',') else number) + " " + units[unit]
    }

    /** Total size of a file or folder; folders are walked, which can take a while. */
    fun of(file: File): Long = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
}
