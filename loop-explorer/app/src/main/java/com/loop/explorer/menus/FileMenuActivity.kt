package com.loop.explorer.menus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import com.loop.explorer.R
import com.loop.explorer.browse.Openers
import com.loop.explorer.files.Clipboard
import com.loop.explorer.files.FileItem
import com.loop.explorer.files.FileKind
import com.loop.explorer.files.Jobs
import com.loop.explorer.files.Sizes
import com.loop.explorer.files.Writers
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.menu.MenuPage
import com.loop.explorer.ui.menu.MenuRow
import com.loop.explorer.ui.menu.SideMenuActivity
import java.io.File
import java.util.Date

/** What can be done with one file, several files, or a new folder, in the menu on the left. */
class FileMenuActivity : SideMenuActivity() {
    private val files by lazy { intent.getStringArrayExtra(EXTRA_FILES).orEmpty().map(::File) }
    private val folder by lazy { File(intent.getStringExtra(EXTRA_FOLDER).orEmpty()) }
    private val language by lazy { BaseActivity.currentLanguage(this) }

    private fun text(id: Int) = getString(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_FOLDER) == null) finish()
    }

    override fun rootPage(): MenuPage {
        if (intent.getBooleanExtra(EXTRA_NEW_FOLDER, false)) return nameNewFolder()
        val existing = files.filter { it.exists() }
        if (existing.size == 1) return single(FileItem.of(existing.first()))
        return many(existing)
    }

    private fun single(item: FileItem) = MenuPage(item.name, { detailLine(item) }, icon = getDrawable(item.kind.iconRes)?.mutate()?.apply { setTint(style.text) }) {
        buildList {
            when (item.kind) {
                FileKind.FOLDER -> add(MenuRow.Action(text(R.string.menu_open), R.drawable.ic_open) { finishWith(RESULT_OPEN_FOLDER, item.file) })
                FileKind.ZIP -> add(MenuRow.Action(text(R.string.menu_extract), R.drawable.ic_zip) { runJob(R.string.job_extracting) { it.unzip(item.file, folder) } })
                else -> add(MenuRow.Action(text(R.string.menu_open), R.drawable.ic_open) {
                    if (!Openers.open(this@FileMenuActivity, item)) Openers.openWith(this@FileMenuActivity, item.file, chooser = false)
                    closeMenu()
                })
            }
            if (!item.isDir) {
                add(MenuRow.Action(text(R.string.menu_open_with), R.drawable.ic_apps) {
                    Openers.openWith(this@FileMenuActivity, item.file, chooser = true)
                    closeMenu()
                })
            }
            addAll(sharedRows(listOf(item.file)))
            add(MenuRow.Nav(text(R.string.menu_rename), R.drawable.ic_edit) { open(renamePage(item.file)) })
            add(MenuRow.Nav(text(R.string.menu_details), R.drawable.ic_info) { open(detailsPage(item)) })
            if (intent.getBooleanExtra(EXTRA_CAN_SELECT, true)) {
                add(MenuRow.Action(text(R.string.menu_select), R.drawable.ic_select) { finishWith(RESULT_SELECT, item.file) })
            }
            add(MenuRow.Nav(text(R.string.menu_delete), R.drawable.ic_trash) { open(deletePage(listOf(item.file))) })
        }
    }

    private fun many(items: List<File>) = MenuPage(getString(R.string.menu_many, items.size)) {
        sharedRows(items) + MenuRow.Nav(text(R.string.menu_delete), R.drawable.ic_trash) { open(deletePage(items)) }
    }

    private fun sharedRows(items: List<File>): List<MenuRow> = listOf(
        MenuRow.Action(text(R.string.menu_copy), R.drawable.ic_copy) { toClipboard(items, cut = false) },
        MenuRow.Action(text(R.string.menu_cut), R.drawable.ic_cut) { toClipboard(items, cut = true) },
        MenuRow.Action(text(R.string.menu_zip), R.drawable.ic_zip) {
            // Several files take the folder's name, except at the top of a storage, where it is only "0" or a code.
            val atTop = com.loop.explorer.files.Places.storages(this).any { it.dir == folder }
            val many = if (atTop) text(R.string.zip_default_name) else folder.name.ifBlank { text(R.string.zip_default_name) }
            val name = (if (items.size == 1) items.first().name.substringBeforeLast('.').ifBlank { items.first().name } else many) + ".zip"
            runJob(R.string.job_zipping) { it.zip(items, folder, name) }
        },
    )

    private fun toClipboard(items: List<File>, cut: Boolean) {
        Clipboard.set(items, cut)
        Toast.makeText(this, if (cut) R.string.clipboard_cut else R.string.clipboard_copied, Toast.LENGTH_LONG).show()
        finishWith(RESULT_DONE, null)
    }

    /** Long work goes to the bar at the bottom of the main screen, so this menu can close. */
    private fun runJob(title: Int, work: suspend (com.loop.explorer.files.Operations) -> Unit) {
        if (Jobs.running) {
            Toast.makeText(this, R.string.job_busy, Toast.LENGTH_SHORT).show()
            return
        }
        if (Writers.forDir(this, folder) == null) return finishWith(RESULT_NEEDS_ACCESS, folder)
        Jobs.start(this, text(title), setOf(folder), work)
        finishWith(RESULT_DONE, null)
    }

    private fun deletePage(items: List<File>) = MenuPage(text(R.string.menu_delete)) {
        listOf(
            MenuRow.Note(if (items.size == 1) getString(R.string.delete_one, items.first().name) else getString(R.string.delete_many, items.size)),
            MenuRow.Action(text(R.string.menu_delete), R.drawable.ic_trash, danger = true) {
                runJob(R.string.job_deleting) { it.delete(items) }
            },
            MenuRow.Action(text(R.string.action_cancel), R.drawable.ic_close) { goBack() },
        )
    }

    private fun renamePage(file: File): MenuPage {
        var typed = file.name
        fun save() {
            val name = typed.trim()
            if (!validName(name) || name == file.name) return
            // Storage ignores upper and lower case, so "foto" to "Foto" is the same file, not a taken name.
            if (File(file.parentFile, name).exists() && !name.equals(file.name, ignoreCase = true)) return toast(R.string.name_taken)
            val writer = Writers.forDir(this, file.parentFile ?: folder) ?: return finishWith(RESULT_NEEDS_ACCESS, folder)
            if (writer.rename(file, name) == null) toast(R.string.error_failed) else finishWith(RESULT_DONE, null)
        }
        return MenuPage(text(R.string.menu_rename)) {
            listOf(
                MenuRow.Input(text(R.string.name_hint), typed, { typed = it }, ::save),
                MenuRow.Action(text(R.string.action_save), R.drawable.ic_check, run = ::save),
            )
        }
    }

    private fun nameNewFolder(): MenuPage {
        var typed = ""
        fun save() {
            val name = typed.trim()
            if (!validName(name)) return
            if (File(folder, name).exists()) return toast(R.string.name_taken)
            val writer = Writers.forDir(this, folder) ?: return finishWith(RESULT_NEEDS_ACCESS, folder)
            if (writer.makeDir(folder, name) == null) toast(R.string.error_failed) else finishWith(RESULT_DONE, null)
        }
        return MenuPage(text(R.string.chip_new_folder), { folder.path }) {
            listOf(
                MenuRow.Input(text(R.string.name_hint), typed, { typed = it }, ::save),
                MenuRow.Action(text(R.string.action_save), R.drawable.ic_check, run = ::save),
            )
        }
    }

    /** Android refuses some characters in names, and pen drives refuse even more. */
    private fun validName(name: String): Boolean {
        if (name.isEmpty() || name == "." || name == ".." || name.any { it in BAD_CHARACTERS }) {
            toast(R.string.name_invalid)
            return false
        }
        return true
    }

    private var folderSize: Long? = null

    private fun detailsPage(item: FileItem): MenuPage {
        if (item.isDir && folderSize == null) {
            Thread {
                val size = runCatching { Sizes.of(item.file) }.getOrDefault(0L)
                runOnUiThread {
                    folderSize = size
                    if (!isDestroyed) refresh()
                }
            }.start()
        }
        return MenuPage(text(R.string.menu_details), { item.name }) {
            val size = if (item.isDir) folderSize?.let { Sizes.text(it, language) } ?: text(R.string.details_counting) else Sizes.text(item.size, language)
            listOfNotNull(
                MenuRow.Meter(text(R.string.details_kind), kindName(item.kind)),
                MenuRow.Meter(text(R.string.details_size), size),
                if (item.isDir) MenuRow.Meter(text(R.string.details_items), item.file.list()?.size?.toString() ?: "-") else null,
                MenuRow.Meter(text(R.string.details_modified), DateFormat.getDateFormat(this).format(Date(item.modified)) + " " + DateFormat.getTimeFormat(this).format(Date(item.modified))),
                MenuRow.Note(item.file.path),
            )
        }
    }

    private fun detailLine(item: FileItem): String = if (item.isDir) kindName(item.kind) else "${kindName(item.kind)} · ${Sizes.text(item.size, language)}"

    private fun kindName(kind: FileKind) = text(
        when (kind) {
            FileKind.FOLDER -> R.string.kind_folder
            FileKind.IMAGE -> R.string.kind_image
            FileKind.VIDEO -> R.string.kind_video
            FileKind.AUDIO -> R.string.kind_audio
            FileKind.APK -> R.string.kind_apk
            FileKind.ZIP -> R.string.kind_zip
            FileKind.TEXT -> R.string.kind_text
            FileKind.OTHER -> R.string.kind_other
        },
    )

    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()

    private fun finishWith(result: Int, file: File?) {
        setResult(result, Intent().apply { file?.let { putExtra(EXTRA_PATH, it.path) } })
        closeMenu()
    }

    companion object {
        const val RESULT_DONE = 10
        const val RESULT_SELECT = 11
        const val RESULT_NEEDS_ACCESS = 12
        const val RESULT_OPEN_FOLDER = 13
        const val EXTRA_PATH = "path"
        private const val EXTRA_FILES = "files"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_NEW_FOLDER = "new_folder"
        private const val EXTRA_CAN_SELECT = "can_select"
        private const val BAD_CHARACTERS = "/\\:*?\"<>|"

        fun forFiles(context: Context, files: List<File>, folder: File, canSelect: Boolean = true): Intent =
            Intent(context, FileMenuActivity::class.java)
                .putExtra(EXTRA_FILES, files.map { it.path }.toTypedArray())
                .putExtra(EXTRA_FOLDER, folder.path)
                .putExtra(EXTRA_CAN_SELECT, canSelect)

        fun newFolder(context: Context, folder: File): Intent =
            Intent(context, FileMenuActivity::class.java)
                .putExtra(EXTRA_FOLDER, folder.path)
                .putExtra(EXTRA_NEW_FOLDER, true)
    }
}
