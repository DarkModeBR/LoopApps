package com.ravager.launcher.trays

enum class MoveStep { LEFT, RIGHT, UP, DOWN }

object TrayLayouts {
    const val OTHER_TRAY_ID = "other"

    fun visibleTrays(layout: TrayLayout): List<Tray> = layout.trays.filter { it.packages.isNotEmpty() }

    fun createTray(layout: TrayLayout, name: String): TrayLayout {
        val taken = layout.trays.map { it.id }.toSet()
        val id = generateSequence(1) { it + 1 }.map { "user$it" }.first { it !in taken }
        return layout.copy(trays = layout.trays + Tray(id, null, name, emptyList()))
    }

    fun renameTray(layout: TrayLayout, id: String, name: String): TrayLayout =
        layout.copy(trays = layout.trays.map { if (it.id == id) it.copy(customName = name) else it })

    fun moveTray(layout: TrayLayout, id: String, delta: Int): TrayLayout {
        val from = layout.trays.indexOfFirst { it.id == id }
        if (from < 0) return layout
        val to = (from + delta).coerceIn(0, layout.trays.lastIndex)
        val trays = layout.trays.toMutableList()
        trays.add(to, trays.removeAt(from))
        return layout.copy(trays = trays)
    }

    fun deleteTray(layout: TrayLayout, id: String): TrayLayout {
        val doomed = layout.trays.firstOrNull { it.id == id } ?: return layout
        val rest = layout.copy(trays = layout.trays - doomed)
        return appendToOther(rest, doomed.packages)
    }

    fun moveApp(layout: TrayLayout, pkg: String, targetTrayId: String): TrayLayout {
        if (layout.trays.none { it.id == targetTrayId }) return layout
        val cleared = removeFromTrays(layout, pkg)
        return cleared.copy(
            trays = cleared.trays.map {
                if (it.id == targetTrayId) it.copy(packages = it.packages + pkg) else it
            },
        )
    }

    fun moveToOther(layout: TrayLayout, pkg: String): TrayLayout = appendToOther(removeFromTrays(layout, pkg), listOf(pkg))

    /** One step of moving a card by hand. Empty trays are skipped because they are not on screen. */
    fun moveStep(layout: TrayLayout, pkg: String, step: MoveStep): TrayLayout {
        val visible = visibleTrays(layout)
        val row = visible.indexOfFirst { pkg in it.packages }
        if (row < 0) return layout
        val column = visible[row].packages.indexOf(pkg)
        return when (step) {
            MoveStep.LEFT -> placeInOwnTray(layout, pkg) { column - 1 }
            MoveStep.RIGHT -> placeInOwnTray(layout, pkg) { column + 1 }
            MoveStep.UP -> visible.getOrNull(row - 1)?.let { insert(layout, pkg, it.id, column) } ?: layout
            MoveStep.DOWN -> visible.getOrNull(row + 1)?.let { insert(layout, pkg, it.id, column) } ?: layout
        }
    }

    private fun placeInOwnTray(layout: TrayLayout, pkg: String, target: (last: Int) -> Int): TrayLayout {
        val tray = layout.trays.firstOrNull { pkg in it.packages } ?: return layout
        val last = tray.packages.lastIndex
        return insert(layout, pkg, tray.id, target(last).coerceIn(0, last))
    }

    private fun insert(layout: TrayLayout, pkg: String, trayId: String, index: Int): TrayLayout {
        val cleared = removeFromTrays(layout, pkg)
        return cleared.copy(
            trays = cleared.trays.map { tray ->
                if (tray.id != trayId) return@map tray
                val packages = tray.packages.toMutableList()
                packages.add(index.coerceIn(0, packages.size), pkg)
                tray.copy(packages = packages)
            },
        )
    }

    fun hideApp(layout: TrayLayout, pkg: String): TrayLayout {
        val cleared = removeFromTrays(layout, pkg)
        return cleared.copy(hidden = cleared.hidden + pkg)
    }

    fun unhideApp(layout: TrayLayout, pkg: String, targetTrayId: String): TrayLayout =
        moveApp(layout.copy(hidden = layout.hidden - pkg), pkg, targetTrayId)

    /** Adds [packages] to the Other tray, creating it when the user has deleted it. */
    internal fun appendToOther(layout: TrayLayout, packages: List<String>): TrayLayout {
        if (layout.trays.any { it.category == AppCategory.OTHER }) {
            return layout.copy(
                trays = layout.trays.map {
                    if (it.category == AppCategory.OTHER) it.copy(packages = it.packages + packages) else it
                },
            )
        }
        val other = Tray(OTHER_TRAY_ID, AppCategory.OTHER, null, packages)
        return layout.copy(trays = layout.trays + other)
    }

    private fun removeFromTrays(layout: TrayLayout, pkg: String) =
        layout.copy(trays = layout.trays.map { it.copy(packages = it.packages - pkg) })
}
