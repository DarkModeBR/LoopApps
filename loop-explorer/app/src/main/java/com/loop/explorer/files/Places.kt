package com.loop.explorer.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.loop.explorer.R
import java.io.File

enum class PlaceKind { INTERNAL, USB, FOLDER }

/** Somewhere to start browsing: the device memory, a pen drive, or a common folder. */
data class Place(val kind: PlaceKind, val label: String, val dir: File, val iconRes: Int, val total: Long = 0, val free: Long = 0)

object Places {
    fun internalRoot(): File = Environment.getExternalStorageDirectory()

    /** Only the storages: the device memory and what is plugged in. */
    fun all(context: Context): List<Place> = storages(context)

    fun storages(context: Context): List<Place> {
        val internal = internalRoot()
        val list = mutableListOf(usage(Place(PlaceKind.INTERNAL, context.getString(R.string.place_internal), internal, R.drawable.ic_storage)))
        removable(context).forEach { (volume, dir) ->
            val label = runCatching { volume.getDescription(context) }.getOrNull() ?: context.getString(R.string.place_usb)
            list += usage(Place(PlaceKind.USB, label, dir, R.drawable.ic_usb))
        }
        return list
    }

    private fun usage(place: Place): Place = runCatching {
        val stat = StatFs(place.dir.path)
        place.copy(total = stat.totalBytes, free = stat.availableBytes)
    }.getOrDefault(place)

    /** Pen drives and memory cards that are plugged in, with the folder where they show up. */
    fun removable(context: Context): List<Pair<StorageVolume, File>> {
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return runCatching {
            manager.storageVolumes
                .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                .mapNotNull { volume -> directory(volume)?.let { volume to it } }
        }.getOrDefault(emptyList())
    }

    /** Android only says where a volume is from version 11 on; before that the field exists but is hidden. */
    fun directory(volume: StorageVolume): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return volume.directory
        return runCatching { volume.javaClass.getMethod("getPathFile").invoke(volume) as? File }.getOrNull()
            ?: runCatching { (volume.javaClass.getMethod("getPath").invoke(volume) as? String)?.let(::File) }.getOrNull()
            ?: volume.uuid?.let { File("/storage/$it") }?.takeIf { it.isDirectory }
    }

    /** The plugged-in volume that holds [file], when it is not the device memory. */
    fun volumeOf(context: Context, file: File): Pair<StorageVolume, File>? =
        removable(context).firstOrNull { (_, root) -> isInside(file, root) }

    fun isInside(file: File, root: File): Boolean {
        val path = file.absolutePath
        val base = root.absolutePath
        return path == base || path.startsWith("$base/")
    }
}
