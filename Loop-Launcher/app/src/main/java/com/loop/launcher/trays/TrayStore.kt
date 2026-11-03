package com.loop.launcher.trays

import java.io.File
import java.io.IOException

class TrayStore(private val file: File) {
    fun load(): TrayLayout? = try {
        if (file.exists()) TrayCodec.decode(file.readText()) else null
    } catch (_: IOException) {
        null
    }

    /** Writes to a temporary file and renames it, so a power cut cannot leave a half-written layout. */
    fun save(layout: TrayLayout) = synchronized(WRITING) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            temp.writeText(TrayCodec.encode(layout))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: IOException) {
            temp.delete()
        }
    }

    /** The menus and the home screen each have a store, and they write to the same temporary file. */
    private companion object {
        val WRITING = Any()
    }
}
