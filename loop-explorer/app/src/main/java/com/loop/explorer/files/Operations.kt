package com.loop.explorer.files

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/** Raised for problems the user can understand; [reason] says which. */
class OperationError(val reason: Reason) : IOException(reason.name) {
    enum class Reason { NO_ACCESS, NO_SPACE, INTO_ITSELF, FAILED, NOT_ZIP }
}

/** Receives how much was done, to show a progress bar. */
fun interface Progress {
    fun update(done: Long, total: Long, current: String)
}

/**
 * Copy, move, delete, compress and extract. Everything runs off the main thread and stops when the
 * coroutine is cancelled, removing the file that was half written.
 */
class Operations(private val context: Context, private val progress: Progress) {
    private var done = 0L
    private var total = 0L

    private fun writer(dir: File): Writer = Writers.forDir(context, dir) ?: throw OperationError(OperationError.Reason.NO_ACCESS)

    private fun checkSpace(dest: File, needed: Long) {
        val free = runCatching { StatFs(dest.path).availableBytes }.getOrNull() ?: return
        if (needed > free) throw OperationError(OperationError.Reason.NO_SPACE)
    }

    suspend fun copy(sources: List<File>, dest: File, deleteAfter: Boolean) {
        sources.forEach { source ->
            if (source.isDirectory && Places.isInside(dest, source)) throw OperationError(OperationError.Reason.INTO_ITSELF)
        }
        val target = writer(dest)
        val pending = mutableListOf<File>()
        // Moving inside the same place only changes the folder, so no bytes need to be copied.
        for (source in sources) {
            if (deleteAfter && source.parentFile != dest && target === writer(source.parentFile ?: dest)) {
                if (target.move(source, dest) != null) continue
            }
            if (deleteAfter && source.parentFile == dest) continue
            pending += source
        }
        total = pending.sumOf { Sizes.of(it) }
        done = 0
        checkSpace(dest, total)
        for (source in pending) {
            copyTree(source, dest, target)
            if (deleteAfter) writer(source.parentFile ?: dest).delete(source)
        }
    }

    private suspend fun copyTree(source: File, destDir: File, target: Writer) {
        coroutineContext.ensureActive()
        val name = Writers.freeName(destDir, source.name)
        if (source.isDirectory) {
            val made = target.makeDir(destDir, name) ?: throw OperationError(OperationError.Reason.FAILED)
            source.listFiles().orEmpty().forEach { copyTree(it, made, target) }
            return
        }
        val (file, output) = target.create(destDir, name) ?: throw OperationError(OperationError.Reason.FAILED)
        try {
            output.use { out -> source.inputStream().use { input -> pump(input, out::write, source.name) } }
            if (target === LocalWriter) file.setLastModified(source.lastModified())
        } catch (error: Throwable) {
            target.delete(file)
            throw error
        }
    }

    private suspend fun pump(input: InputStream, write: (ByteArray, Int, Int) -> Unit, name: String) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            write(buffer, 0, read)
            done += read
            progress.update(done, total, name)
        }
    }

    suspend fun delete(items: List<File>) {
        total = items.size.toLong()
        done = 0
        for (item in items) {
            coroutineContext.ensureActive()
            progress.update(done, total, item.name)
            if (!writer(item.parentFile ?: item).delete(item)) throw OperationError(OperationError.Reason.FAILED)
            done++
        }
    }

    /** Puts [sources] in one new ZIP file inside [dest]. */
    suspend fun zip(sources: List<File>, dest: File, wantedName: String) {
        val target = writer(dest)
        total = sources.sumOf { Sizes.of(it) }
        done = 0
        checkSpace(dest, total)
        val (file, output) = target.create(dest, Writers.freeName(dest, wantedName)) ?: throw OperationError(OperationError.Reason.FAILED)
        try {
            ZipOutputStream(output.buffered()).use { zip ->
                sources.forEach { addToZip(zip, it, it.name) }
            }
        } catch (error: Throwable) {
            target.delete(file)
            throw error
        }
    }

    private suspend fun addToZip(zip: ZipOutputStream, file: File, path: String) {
        coroutineContext.ensureActive()
        if (file.isDirectory) {
            zip.putNextEntry(ZipEntry("$path/").apply { time = file.lastModified() })
            zip.closeEntry()
            file.listFiles().orEmpty().forEach { addToZip(zip, it, "$path/${it.name}") }
            return
        }
        zip.putNextEntry(ZipEntry(path).apply { time = file.lastModified() })
        file.inputStream().use { pump(it, zip::write, file.name) }
        zip.closeEntry()
    }

    /** Extracts into a new folder named after the ZIP, so nothing already there is replaced. */
    suspend fun unzip(zipFile: File, dest: File) {
        val target = writer(dest)
        total = zipFile.length()
        done = 0
        checkSpace(dest, total)
        val folderName = Writers.freeName(dest, zipFile.name.substringBeforeLast('.').ifBlank { zipFile.name })
        val root = target.makeDir(dest, folderName) ?: throw OperationError(OperationError.Reason.FAILED)
        var entries = 0
        val counted = object : FilterInputStream(zipFile.inputStream().buffered()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also {
                if (it > 0) {
                    done += it
                    progress.update(done, total, zipFile.name)
                }
            }
        }
        try {
            ZipInputStream(counted).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    entries++
                    val parts = safeParts(entry.name) ?: continue
                    if (entry.isDirectory) {
                        ensureDirs(root, parts, target)
                    } else {
                        val parent = ensureDirs(root, parts.dropLast(1), target)
                        val (_, out) = target.create(parent, Writers.freeName(parent, parts.last())) ?: throw OperationError(OperationError.Reason.FAILED)
                        out.use { o ->
                            val buffer = ByteArray(BUFFER)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = zip.read(buffer)
                                if (read < 0) break
                                o.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            target.delete(root)
            throw error
        }
        if (entries == 0) {
            target.delete(root)
            throw OperationError(OperationError.Reason.NOT_ZIP)
        }
    }

    /** Refuses names that would write outside the destination folder. */
    private fun safeParts(name: String): List<String>? {
        val parts = name.replace('\\', '/').split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." } || name.startsWith("/")) return null
        return parts
    }

    private fun ensureDirs(root: File, parts: List<String>, target: Writer): File {
        var dir = root
        for (part in parts) {
            val next = File(dir, part)
            dir = if (next.isDirectory) next else target.makeDir(dir, part) ?: throw OperationError(OperationError.Reason.FAILED)
        }
        return dir
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
