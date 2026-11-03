package com.loop.launcher.wallpaper

import android.os.Environment
import android.widget.Toast
import com.loop.launcher.R
import com.loop.launcher.settings.AppSettings
import com.loop.launcher.settings.SettingsMenuActivity
import com.loop.launcher.ui.menu.MenuPage
import com.loop.launcher.ui.menu.MenuRow
import java.io.File

/** Pages for choosing a wallpaper and bringing in files from the device or a pen drive. */
class WallpaperMenus(private val menu: SettingsMenuActivity, private val settings: AppSettings) {
    private val files = WallpaperFiles(File(menu.filesDir, "wallpapers"))
    private val thumbs = WallpaperThumbs(menu)

    private fun text(id: Int) = menu.getString(id)

    fun currentName(): String = when (val choice = settings.wallpaper) {
        Wallpaper.None -> text(R.string.wallpaper_none)
        is Wallpaper.Bundled -> BundledWallpapers.all.firstOrNull { it.asset == choice.asset }?.let { text(it.nameRes) }.orEmpty()
        is Wallpaper.Imported -> choice.name
    }

    fun rootPage() = MenuPage(text(R.string.settings_wallpaper), { text(R.string.wallpaper_hint) }) {
        val current = settings.wallpaper
        buildList {
            add(MenuRow.Option(text(R.string.wallpaper_none), current == Wallpaper.None) { choose(Wallpaper.None) })
            add(
                MenuRow.Nav(text(R.string.wallpaper_add), R.drawable.ic_plus) {
                    menu.withFileAccess { menu.open(rootsPage()) }
                },
            )
            add(MenuRow.Nav(text(R.string.wallpaper_dim), R.drawable.ic_moon, value = text(settings.wallpaperDim.nameRes)) { menu.open(dimPage()) })
            val imported = files.list()
            if (imported.isNotEmpty()) {
                add(MenuRow.Section(text(R.string.wallpaper_yours)))
                imported.forEach { name ->
                    add(
                        MenuRow.Nav(
                            name,
                            value = kindLabel(name),
                            detail = inUse(current == Wallpaper.Imported(name)),
                            thumb = thumbs.imported(name, files.resolve(name)),
                        ) { menu.open(importedPage(name)) },
                    )
                }
            }
            add(MenuRow.Section(text(R.string.wallpaper_included)))
            BundledWallpapers.all.forEach { wallpaper ->
                val choice = Wallpaper.Bundled(wallpaper.asset)
                add(MenuRow.Option(text(wallpaper.nameRes), current == choice, thumb = thumbs.bundled(wallpaper.asset)) { choose(choice) })
            }
        }
    }

    private fun inUse(using: Boolean) = if (using) text(R.string.wallpaper_in_use) else null

    private fun choose(choice: Wallpaper) {
        settings.wallpaper = choice
        menu.refresh()
    }

    private fun kindLabel(name: String) = when (WallpaperKind.of(name)) {
        WallpaperKind.IMAGE -> text(R.string.wallpaper_kind_image)
        WallpaperKind.GIF -> "GIF"
        WallpaperKind.VIDEO -> text(R.string.wallpaper_kind_video)
        null -> null
    }

    private fun dimPage() = MenuPage(text(R.string.wallpaper_dim), { text(R.string.wallpaper_dim_hint) }) {
        DimLevel.entries.map { level ->
            MenuRow.Option(text(level.nameRes), settings.wallpaperDim == level) {
                settings.wallpaperDim = level
                menu.refresh()
            }
        }
    }

    private fun importedPage(name: String) = MenuPage(name, { kindLabel(name) }) {
        listOf(
            MenuRow.Action(text(R.string.wallpaper_use), R.drawable.ic_check) {
                settings.wallpaper = Wallpaper.Imported(name)
                menu.goBack()
            },
            MenuRow.Action(text(R.string.wallpaper_remove), R.drawable.ic_trash, danger = true) {
                if (settings.wallpaper == Wallpaper.Imported(name)) settings.wallpaper = Wallpaper.None
                files.delete(name)
                menu.goBack()
            },
        )
    }

    private fun rootsPage() = MenuPage(text(R.string.wallpaper_add), { text(R.string.wallpaper_add_hint) }) {
        val internal = Environment.getExternalStorageDirectory()
        val removable = File("/storage").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead() }
        val rows = mutableListOf<MenuRow>(
            MenuRow.Nav(text(R.string.storage_internal), R.drawable.ic_home) { menu.open(folderPage(internal, 2)) },
        )
        removable.forEach { dir ->
            rows += MenuRow.Nav(text(R.string.storage_usb), R.drawable.ic_usb, detail = dir.name) { menu.open(folderPage(dir, 2)) }
        }
        rows
    }

    /** [depth] is how many pages to close to return to the wallpaper list after choosing. */
    private fun folderPage(dir: File, depth: Int): MenuPage = MenuPage(dir.name.ifEmpty { "/" }) {
        val entries = FileBrowser.entries(dir)
        if (entries.isEmpty()) {
            listOf(MenuRow.Note(text(R.string.wallpaper_folder_empty)))
        } else {
            entries.map { entry ->
                if (entry.isFolder) {
                    MenuRow.Nav(entry.name, R.drawable.ic_grid) { menu.open(folderPage(entry.file, depth + 1)) }
                } else {
                    MenuRow.Action(entry.name, value = kindLabel(entry.name)) { import(entry.file, depth) }
                }
            }
        }
    }

    private fun import(file: File, depth: Int) {
        Toast.makeText(menu, R.string.wallpaper_copying, Toast.LENGTH_SHORT).show()
        Thread {
            // This thread has nothing above it to catch an error, which would close the launcher.
            val name = runCatching { files.import(file) }.getOrNull()
            menu.runOnUiThread {
                if (name == null) {
                    Toast.makeText(menu, R.string.wallpaper_copy_failed, Toast.LENGTH_LONG).show()
                } else {
                    settings.wallpaper = Wallpaper.Imported(name)
                    if (!menu.isDestroyed) repeat(depth) { menu.goBack() }
                }
            }
        }.start()
    }
}
