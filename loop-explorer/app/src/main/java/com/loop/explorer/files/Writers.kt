package com.loop.explorer.files

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Changes files in one place. Reading always uses plain files; writing goes through this, because
 * Android only lets apps write to a pen drive through the permission the user gives in the system picker.
 */
interface Writer {
    fun makeDir(parent: File, name: String): File?

    /** A new file named [name], which must not exist yet. */
    fun create(parent: File, name: String): Pair<File, OutputStream>?

    fun delete(target: File): Boolean

    fun rename(target: File, newName: String): File?

    /** Moves inside the same place without copying; null when that is not possible. */
    fun move(target: File, toDir: File): File?
}

object LocalWriter : Writer {
    override fun makeDir(parent: File, name: String): File? = File(parent, name).takeIf { it.mkdir() }

    override fun create(parent: File, name: String): Pair<File, OutputStream>? = runCatching {
        val file = File(parent, name)
        file to (FileOutputStream(file) as OutputStream)
    }.getOrNull()

    override fun delete(target: File): Boolean = target.deleteRecursively()

    override fun rename(target: File, newName: String): File? {
        val result = File(target.parentFile, newName)
        // Storage ignores upper and lower case, so "foto" to "Foto" says yes and changes nothing;
        // going through another name first makes the new spelling stick.
        if (newName != target.name && newName.equals(target.name, ignoreCase = true)) {
            val step = File(target.parentFile, Writers.freeName(target.parentFile ?: return null, ".$newName.loop"))
            if (!target.renameTo(step)) return null
            return result.takeIf { step.renameTo(it) } ?: run {
                step.renameTo(target)
                null
            }
        }
        return result.takeIf { target.renameTo(it) }
    }

    override fun move(target: File, toDir: File): File? =
        File(toDir, target.name).takeIf { !it.exists() && target.renameTo(it) }
}

/** Writes to a pen drive with the access the user granted in the system picker. */
class TreeWriter(
    private val resolver: ContentResolver,
    private val root: File,
    private val volumeId: String,
    private val tree: Uri,
) : Writer {
    /** The system names each file on a volume by the volume id and the path inside it. */
    private fun uri(file: File): Uri {
        val relative = file.absolutePath.removePrefix(root.absolutePath).trimStart('/')
        return DocumentsContract.buildDocumentUriUsingTree(tree, "$volumeId:$relative")
    }

    override fun makeDir(parent: File, name: String): File? = runCatching {
        DocumentsContract.createDocument(resolver, uri(parent), DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?.let { File(parent, name) }
    }.getOrNull()

    override fun create(parent: File, name: String): Pair<File, OutputStream>? = runCatching {
        val created = DocumentsContract.createDocument(resolver, uri(parent), FileKind.mime(name), name) ?: return null
        val stream = resolver.openOutputStream(created) ?: return null
        File(parent, name) to stream
    }.getOrNull()

    override fun delete(target: File): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, uri(target)) }.getOrDefault(false)

    override fun rename(target: File, newName: String): File? = runCatching {
        DocumentsContract.renameDocument(resolver, uri(target), newName)?.let { File(target.parentFile, newName) }
    }.getOrNull()

    override fun move(target: File, toDir: File): File? = runCatching {
        val parent = target.parentFile ?: return null
        DocumentsContract.moveDocument(resolver, uri(target), uri(parent), uri(toDir))?.let { File(toDir, target.name) }
    }.getOrNull()
}

object Writers {
    /** Null when writing there needs the user's permission first; see [accessIntent]. */
    fun forDir(context: Context, dir: File): Writer? {
        val volume = Places.volumeOf(context, dir) ?: return LocalWriter
        val (storage, root) = volume
        // Some systems let apps write to pen drives directly.
        if (root.canWrite()) return LocalWriter
        val id = storage.uuid ?: return null
        val tree = context.contentResolver.persistedUriPermissions
            .filter { it.isWritePermission }
            .map { it.uri }
            .firstOrNull { runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull() == "$id:" }
            ?: return null
        return TreeWriter(context.contentResolver, root, id, tree)
    }

    /** The system screen where the user lets the app write to the pen drive that holds [dir]. */
    @Suppress("DEPRECATION")
    fun accessIntent(context: Context, dir: File): Intent? {
        val volume: StorageVolume = Places.volumeOf(context, dir)?.first ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) volume.createOpenDocumentTreeIntent() else volume.createAccessIntent(null)
    }

    /** Keeps the access after the app or the device restarts. */
    fun keepAccess(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /** A name not used in [dir] yet: "photo.jpg", then "photo (2).jpg" and so on. */
    fun freeName(dir: File, wanted: String): String {
        if (!File(dir, wanted).exists()) return wanted
        val dot = wanted.lastIndexOf('.')
        val base = if (dot > 0) wanted.substring(0, dot) else wanted
        val ext = if (dot > 0) wanted.substring(dot) else ""
        return generateSequence(2) { it + 1 }.map { "$base ($it)$ext" }.first { !File(dir, it).exists() }
    }
}
