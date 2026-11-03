package com.loop.explorer.browse

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.loop.explorer.BuildConfig
import com.loop.explorer.R
import com.loop.explorer.access.AccessActivity
import com.loop.explorer.files.Clipboard
import com.loop.explorer.files.FileItem
import com.loop.explorer.files.JobState
import com.loop.explorer.files.Jobs
import com.loop.explorer.files.Listing
import com.loop.explorer.files.Place
import com.loop.explorer.files.Places
import com.loop.explorer.files.Thumbs
import com.loop.explorer.files.Writers
import com.loop.explorer.menus.FileMenuActivity
import com.loop.explorer.menus.SettingsMenuActivity
import com.loop.explorer.net.UrlConnectionHttpGet
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.settings.ViewMode
import com.loop.explorer.theme.ThemeDefinition
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import com.loop.explorer.ui.BaseActivity.Companion.currentLanguage
import com.loop.explorer.ui.Chip
import com.loop.explorer.ui.Fab
import com.loop.explorer.ui.ToolButton
import com.loop.explorer.ui.FontWeight
import com.loop.explorer.ui.ScreenErrors
import com.loop.explorer.ui.face
import com.loop.explorer.update.UpdateChecker
import com.loop.explorer.update.UpdateInfo
import com.loop.explorer.update.UpdateMenuActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** Places on the left, the open folder on the right, and a bar at the bottom while files are copied. */
class MainActivity : BaseActivity(), FileListener {
    private val scope = MainScope() + ScreenErrors.handler()
    private lateinit var settings: AppSettings
    private lateinit var theme: ThemeDefinition
    private lateinit var thumbs: Thumbs
    private lateinit var placesAdapter: PlacesAdapter
    private lateinit var filesAdapter: FilesAdapter
    private lateinit var placesList: RecyclerView
    private lateinit var filesList: RecyclerView
    private lateinit var title: TextView
    private lateinit var path: TextView
    private lateinit var empty: TextView
    private lateinit var status: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var statusBar: ProgressBar
    private lateinit var pasteButton: ToolButton
    private lateinit var selectButton: ToolButton
    private lateinit var updateButton: ToolButton
    private lateinit var serverRow: ServerRow
    private lateinit var accessChip: Chip
    private lateinit var fab: Fab
    private lateinit var toolRow: LinearLayout
    private var lastPosition = 0

    /** True until the files appear for the first time, so the focus starts on them, not on the side panel. */
    private var focusOnFirstLoad = true
    private var unit = 0f
    private var language = ""

    private var place: Place? = null
    private var dir: File? = null
    private var loadJob: Job? = null
    private var focusName: String? = null
    private val positions = HashMap<String, Int>()
    private var selecting = false
    private val selected = LinkedHashSet<File>()
    private var availableUpdate: UpdateInfo? = null
    private var updateJob: Job? = null
    private var askedForAccess = false

    private val jobListener: (JobState?) -> Unit = { showJob(it) }
    private val serverListener: () -> Unit = { serverRow.update() }

    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            AppSettings.KEY_THEME, AppSettings.KEY_ACCENT, AppSettings.KEY_FONT -> recreate()
            in AppSettings.LIVE -> {
                applyViewMode()
                reload()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        theme = Themes.byId(settings.themeId)
        unit = resources.displayMetrics.widthPixels / 100f
        language = currentLanguage(this)
        thumbs = Thumbs(this, (unit * 12).toInt())
        buildScreen()
        settings.register(settingsChanged)
        // An update downloaded earlier has been installed or given up by now; not when the screen is only rebuilt.
        if (savedInstanceState == null) Thread { File(cacheDir, "updates/update.apk").delete() }.start()
        savedInstanceState?.getString(STATE_DIR)?.let { saved -> dir = File(saved) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        dir?.let { outState.putString(STATE_DIR, it.path) }
    }

    override fun onResume() {
        super.onResume()
        if (currentLanguage(this) != language) {
            recreate()
            return
        }
        if (!hasAccess()) {
            showNoAccess()
            if (!askedForAccess) {
                askedForAccess = true
                requestAccess()
            }
            return
        }
        accessChip.visibility = View.GONE
        serverRow.update()
        loadPlaces()
        val current = dir
        if (current == null || existingDir(current) == null) open(placesAdapter.places.firstOrNull() ?: return) else reload()
        showJob(Jobs.state)
        showPaste()
    }

    override fun onStart() {
        super.onStart()
        Jobs.addListener(jobListener)
        com.loop.explorer.server.ServerService.addListener(serverListener)
        checkForUpdate()
    }

    override fun onStop() {
        super.onStop()
        Jobs.removeListener(jobListener)
        com.loop.explorer.server.ServerService.removeListener(serverListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        settings.unregister(settingsChanged)
        scope.cancel()
    }

    // ---------- Screen

    private fun px(percent: Float) = (unit * percent).toInt()

    private fun text(size: Float, color: Int, weight: FontWeight = FontWeight.REGULAR) = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, unit * size)
        setTextColor(color)
        typeface = theme.face(weight)
        maxLines = 1
    }

    private fun chip(icon: Int, label: Int, onClick: () -> Unit) =
        Chip(this, theme, unit).set(icon, getString(label)).apply { setOnClickListener { onClick() } }

    private fun tool(icon: Int, label: Int, onClick: () -> Unit) =
        ToolButton(this, theme, unit).set(icon, getString(label)).apply { setOnClickListener { onClick() } }

    /**
     * Inspired by AnExplorer: storages in a panel on the left, a top bar in the chosen color with
     * icon buttons, the files on a plain background and a round button to create a folder.
     */
    private fun buildScreen() {
        placesAdapter = PlacesAdapter(theme, unit, language) { open(it) }
        filesAdapter = FilesAdapter(theme, unit, language, thumbs, this)

        val drawerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.bar)
            setPadding(px(2f), 0, px(1f), 0)
            addView(text(1.45f, theme.onBar, FontWeight.MEDIUM).apply { text = getString(R.string.app_name) })
        }
        serverRow = ServerRow(this, theme, unit).apply {
            setOnClickListener { startActivity(Intent(this@MainActivity, com.loop.explorer.server.ServerActivity::class.java)) }
        }
        val section = text(0.95f, theme.muted, FontWeight.MEDIUM).apply {
            text = getString(R.string.drawer_storage)
            setPadding(px(1.8f), px(1.6f), px(1f), px(0.6f))
        }
        placesList = RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            adapter = placesAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(px(0.7f), 0, px(0.9f), 0)
        }
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.surface)
            elevation = unit * 0.4f
            addView(drawerHeader, LinearLayout.LayoutParams(MATCH, px(TOOLBAR)))
            addView(serverRow, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                setMargins(px(0.7f), px(1.2f), px(0.9f), 0)
            })
            addView(section)
            addView(placesList, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }

        title = text(1.7f, theme.onBar, FontWeight.MEDIUM)
        path = text(0.95f, androidx.core.graphics.ColorUtils.setAlphaComponent(theme.onBar, 0xCC)).apply { ellipsize = TextUtils.TruncateAt.START }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title)
            addView(path)
        }
        updateButton = tool(R.drawable.ic_update, R.string.chip_update) { availableUpdate?.let { startActivity(UpdateMenuActivity.intent(this, it)) } }
        pasteButton = tool(R.drawable.ic_paste, R.string.chip_paste) { paste() }
        selectButton = tool(R.drawable.ic_select, R.string.chip_select) { onSelectChip() }
        toolRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            listOf(
                updateButton,
                pasteButton,
                selectButton,
                tool(R.drawable.ic_settings, R.string.chip_settings) { startActivity(Intent(this@MainActivity, SettingsMenuActivity::class.java)) },
            ).forEach { addView(it, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = px(0.3f) }) }
        }
        updateButton.visibility = View.GONE
        pasteButton.visibility = View.GONE
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.bar)
            elevation = unit * 0.3f
            setPadding(px(2f), 0, px(1.2f), 0)
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(toolRow)
        }

        filesList = RecyclerView(this).apply {
            adapter = filesAdapter
            itemAnimator = null
            clipToPadding = false
            clipChildren = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(px(1.2f), px(1f), px(1.2f), px(7f))
        }
        applyViewMode()
        empty = text(1.3f, theme.muted).apply {
            gravity = Gravity.CENTER
            maxLines = 4
        }
        accessChip = chip(R.drawable.ic_check, R.string.chip_access) { requestAccess() }
        accessChip.visibility = View.GONE
        val emptyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            addView(empty)
            addView(accessChip, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(1.2f) })
        }
        emptyHolder = emptyBox
        fab = Fab(this, theme, unit).set(R.drawable.ic_plus, getString(R.string.chip_new_folder))
        fab.setOnClickListener { dir?.let { askFor(FileMenuActivity.newFolder(this@MainActivity, it)) } }

        statusText = text(1.05f, 0xFFFFFFFF.toInt())
        statusBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent)
        }
        val cancel = chip(R.drawable.ic_close, R.string.job_cancel) { if (Jobs.running) Jobs.cancel() else if (selecting) stopSelecting() }
        // A snackbar, dark in both themes as in material apps.
        status = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            elevation = unit * 0.5f
            setPadding(px(1.4f), px(0.7f), px(1f), px(0.7f))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF323232.toInt())
                cornerRadius = unit * 0.4f
            }
            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(statusText)
                addView(statusBar, LinearLayout.LayoutParams(MATCH, px(0.7f)).apply { topMargin = px(0.3f) })
            }
            addView(column, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(cancel, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = px(1.2f) })
        }
        val body = FrameLayout(this).apply {
            clipChildren = false
            addView(filesList, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emptyBox, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            addView(status, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply {
                setMargins(px(1.6f), 0, px(8f), px(1.6f))
            })
            addView(fab, FrameLayout.LayoutParams(px(4.4f), px(4.4f), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, px(2f), px(1.8f))
            })
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(toolbar, LinearLayout.LayoutParams(MATCH, px(TOOLBAR)))
            addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setBackgroundColor(Themes.background(theme))
            addView(drawer, LinearLayout.LayoutParams(px(SIDEBAR), MATCH))
            addView(content, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        setContentView(row)
    }

    private lateinit var emptyHolder: View

    private fun applyViewMode() {
        val grid = settings.viewMode == ViewMode.GRID
        filesAdapter.grid = grid
        filesList.layoutManager = GridLayoutManager(this, if (grid) COLUMNS else 1)
        filesAdapter.notifyDataSetChanged()
    }

    // ---------- Access

    private fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        STORAGE_PERMISSIONS.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(this, AccessActivity::class.java))
        } else {
            requestPermissions(STORAGE_PERMISSIONS, REQUEST_STORAGE)
        }
    }

    private fun showNoAccess() {
        title.text = getString(R.string.app_name)
        path.text = null
        filesAdapter.submit(emptyList())
        empty.text = getString(R.string.access_needed)
        emptyHolder.visibility = View.VISIBLE
        accessChip.visibility = View.VISIBLE
        accessChip.requestFocus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // onResume follows and shows the files, or the message asking for access.
    }

    /** Pen drives need the user to point at them once in the system picker before anything is written. */
    private fun requestWriteAccess(target: File) {
        val intent = Writers.accessIntent(this, target)
        if (intent == null) {
            Toast.makeText(this, R.string.error_no_access, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.usb_access_hint, Toast.LENGTH_LONG).show()
        if (!com.loop.explorer.ui.opened { startActivityForResult(intent, REQUEST_TREE) }) {
            Toast.makeText(this, R.string.usb_access_missing, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Activity results are enough here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_TREE -> data?.data?.takeIf { resultCode == RESULT_OK }?.let { uri ->
                Writers.keepAccess(this, uri)
                Toast.makeText(this, R.string.usb_access_done, Toast.LENGTH_LONG).show()
            }
            REQUEST_MENU -> when (resultCode) {
                FileMenuActivity.RESULT_SELECT -> data?.getStringExtra(FileMenuActivity.EXTRA_PATH)?.let { startSelecting(File(it)) }
                FileMenuActivity.RESULT_NEEDS_ACCESS -> data?.getStringExtra(FileMenuActivity.EXTRA_PATH)?.let { requestWriteAccess(File(it)) }
                FileMenuActivity.RESULT_DONE -> if (selecting) stopSelecting()
                FileMenuActivity.RESULT_OPEN_FOLDER -> data?.getStringExtra(FileMenuActivity.EXTRA_PATH)?.let { openDir(File(it)) }
            }
        }
    }

    private fun askFor(intent: Intent) {
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_MENU)
    }

    // ---------- Places and folders

    private fun loadPlaces() {
        val list = Places.all(this)
        val current = place?.let { chosen -> list.firstOrNull { it.dir == chosen.dir } }
        placesAdapter.submit(list, current ?: place)
    }

    private fun open(target: Place) {
        place = target
        placesAdapter.select(target)
        positions.clear()
        openDir(target.dir, focusFirst = true)
    }

    /** The place that holds [folder], so the left side shows where the user is. */
    private fun placeFor(folder: File): Place? =
        placesAdapter.places.filter { Places.isInside(folder, it.dir) }.maxByOrNull { it.dir.path.length }

    private fun openDir(target: File, focusFirst: Boolean = true, keepFocus: String? = null) {
        dir?.let { old -> currentPosition()?.let { positions[old.path] = it } }
        dir = target
        placeFor(target)?.let {
            place = it
            placesAdapter.select(it)
        }
        focusName = keepFocus
        load(target, focusFirst)
    }

    private fun reload() {
        val current = dir ?: return
        // The folder may be gone: deleted from the browser, or its pen drive pulled out.
        val still = existingDir(current)
        if (still == null) {
            open(placesAdapter.places.firstOrNull() ?: return)
            return
        }
        if (still != current) {
            openDir(still, focusFirst = true)
            return
        }
        val focused = currentFocusedItem()?.name
        load(current, focusFirst = false, keep = focused)
    }

    /** [folder], or the closest folder above it that still exists inside a storage; null when none does. */
    private fun existingDir(folder: File): File? =
        generateSequence(folder) { it.parentFile }
            .takeWhile { candidate -> placesAdapter.places.any { Places.isInside(candidate, it.dir) } }
            .firstOrNull { it.isDirectory }

    private fun load(target: File, focusFirst: Boolean, keep: String? = focusName) {
        // After the screen is rebuilt, for a new theme for example, the place has to be found again.
        if (place == null || !Places.isInside(target, place!!.dir)) placeFor(target)?.let {
            place = it
            placesAdapter.select(it)
        }
        title.text = if (target == place?.dir) place?.label else target.name
        path.text = target.path
        loadJob?.cancel()
        loadJob = scope.launch {
            val items = withContext(Dispatchers.IO) { Listing.list(target, settings.showHidden, settings.sortOrder) }
            if (dir != target) return@launch
            show(items, focusFirst, keep)
        }
    }

    private fun show(items: List<FileItem>?, focusFirst: Boolean, keep: String?) {
        val wasInList = currentFocus?.let { isInside(it, filesList) } == true
        filesAdapter.submit(items.orEmpty())
        emptyHolder.visibility = if (items.isNullOrEmpty()) View.VISIBLE else View.GONE
        empty.text = getString(if (items == null) R.string.folder_unreadable else R.string.folder_empty)
        if (items.isNullOrEmpty()) {
            // Nothing to focus in the folder, so the focus goes where something can be done here.
            if (focusFirst || wasInList) (if (pasteButton.visibility == View.VISIBLE) pasteButton else fab).requestFocus()
            return
        }
        val byName = keep?.let { name -> items.indexOfFirst { it.name == name } }?.takeIf { it >= 0 }
        val target = byName ?: positions[dir?.path]?.coerceAtMost(items.lastIndex) ?: 0
        if (focusFirst || wasInList || byName != null || currentFocus == null || focusOnFirstLoad) focusItem(target)
        focusOnFirstLoad = false
    }

    private fun focusItem(position: Int, attempts: Int = 10) {
        filesList.scrollToPosition(position)
        filesList.post {
            val view = filesList.findViewHolderForAdapterPosition(position)?.itemView
            if (view != null) view.requestFocus() else if (attempts > 0) filesList.postDelayed({ focusItem(position, attempts - 1) }, 40)
        }
    }

    private fun currentFocusedItem(): FileItem? {
        val view = currentFocus ?: return null
        if (!isInside(view, filesList)) return null
        val holder = filesList.findContainingViewHolder(view) ?: return null
        return filesAdapter.items.getOrNull(holder.bindingAdapterPosition)
    }

    private fun currentPosition(): Int? {
        val view = currentFocus ?: return null
        if (!isInside(view, filesList)) return null
        return filesList.findContainingViewHolder(view)?.bindingAdapterPosition
    }

    private fun isInside(view: View, parent: View): Boolean =
        generateSequence(view as View?) { it.parent as? View }.any { it === parent }

    // ---------- Files

    override fun onFocused(position: Int, item: FileItem) {
        lastPosition = position
    }

    override fun onOpen(position: Int, item: FileItem) {
        if (selecting) {
            if (!selected.remove(item.file)) selected += item.file
            filesAdapter.notifyItemChanged(position)
            showSelection()
            return
        }
        if (item.isDir) {
            openDir(item.file)
            return
        }
        if (!Openers.open(this, item)) askFor(FileMenuActivity.forFiles(this, listOf(item.file), dir ?: return))
    }

    override fun onMenu(position: Int, item: FileItem) {
        val current = dir ?: return
        val files = if (selecting && selected.isNotEmpty()) selected.toList() else listOf(item.file)
        askFor(FileMenuActivity.forFiles(this, files, current, canSelect = !selecting))
    }

    override fun isSelected(item: FileItem) = selecting && item.file in selected

    private fun startSelecting(first: File) {
        selecting = true
        selected.clear()
        selected += first
        filesAdapter.notifyDataSetChanged()
        showSelection()
    }

    private fun stopSelecting() {
        selecting = false
        selected.clear()
        filesAdapter.notifyDataSetChanged()
        showSelection()
    }

    private fun showSelection() {
        selectButton.set(
            R.drawable.ic_select,
            if (selecting) getString(R.string.chip_selected, selected.size) else getString(R.string.chip_select),
        )
        if (!Jobs.running) {
            status.visibility = if (selecting) View.VISIBLE else View.GONE
            statusText.text = getString(R.string.selection_hint)
            statusBar.visibility = View.GONE
        }
    }

    private fun onSelectChip() {
        val current = dir ?: return
        if (!selecting) {
            selecting = true
            selected.clear()
            filesAdapter.notifyDataSetChanged()
            showSelection()
            Toast.makeText(this, R.string.selection_hint, Toast.LENGTH_LONG).show()
        } else if (selected.isNotEmpty()) {
            askFor(FileMenuActivity.forFiles(this, selected.toList(), current, canSelect = false))
        } else {
            stopSelecting()
        }
    }

    private fun showPaste() {
        val files = Clipboard.files.filter { it.exists() }
        pasteButton.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        if (files.isNotEmpty()) pasteButton.set(R.drawable.ic_paste, getString(R.string.chip_paste_count, files.size))
    }

    private fun paste() {
        val target = dir ?: return
        val files = Clipboard.files.filter { it.exists() }
        if (files.isEmpty()) return showPaste()
        if (Jobs.running) {
            Toast.makeText(this, R.string.job_busy, Toast.LENGTH_SHORT).show()
            return
        }
        if (Writers.forDir(this, target) == null) return requestWriteAccess(target)
        val cut = Clipboard.cut
        val touched = files.mapNotNull { it.parentFile }.toSet() + target
        val title = getString(if (cut) R.string.job_moving else R.string.job_copying)
        Jobs.start(this, title, touched) { it.copy(files, target, deleteAfter = cut) }
        if (cut) Clipboard.clear()
        showPaste()
    }

    private fun showJob(state: JobState?) {
        if (state == null) {
            if (!selecting) status.visibility = View.GONE
            return
        }
        if (state.finished) {
            Jobs.acknowledge()
            status.visibility = if (selecting) View.VISIBLE else View.GONE
            if (selecting) showSelection()
            val message = when {
                state.error != null -> state.error
                state.cancelled -> getString(R.string.job_cancelled)
                else -> getString(R.string.job_done)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            showPaste()
            reload()
            loadPlaces()
            return
        }
        status.visibility = View.VISIBLE
        statusBar.visibility = View.VISIBLE
        val percent = if (state.total > 0) (state.done * 1000 / state.total).toInt() else 0
        statusBar.isIndeterminate = state.total <= 0
        statusBar.progress = percent
        statusText.text = if (state.current.isEmpty()) state.title else getString(R.string.job_line, state.title, state.current, percent / 10)
    }

    // ---------- Keys

    @SuppressLint("GestureBackNavigation")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) goBack()
            return true
        }
        // The server row is the top of the side panel: up stays there, instead of jumping to the files.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && currentFocus === serverRow) return true
        // Left from the files goes to the open storage, not to whichever row of the panel is level with it.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN &&
            currentFocus?.let { isInside(it, filesList) } == true
        ) {
            val position = currentPosition()
            val columns = if (filesAdapter.grid) COLUMNS else 1
            if (position != null && position % columns == 0) {
                val index = placesAdapter.places.indexOfFirst { it.dir == place?.dir }.coerceAtLeast(0)
                placesList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
                return true
            }
        }
        // Down from the top buttons goes to the files, not to the places on the left.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN &&
            currentFocus?.let { isInside(it, toolRow) } == true && filesAdapter.itemCount > 0
        ) {
            focusItem(positions[dir?.path]?.coerceAtMost(filesAdapter.itemCount - 1) ?: 0)
            return true
        }
        // Up from the first row goes to the top buttons, not to the places on the left.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
            val position = currentPosition()
            val columns = if (filesAdapter.grid) COLUMNS else 1
            if (position != null && position < columns) {
                (0 until toolRow.childCount).map { toolRow.getChildAt(it) }.firstOrNull { it.visibility == View.VISIBLE }?.requestFocus()
                return true
            }
        }
        // At the end of a row the focus goes to the round button, instead of jumping to the top bar.
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
            val position = currentPosition()
            val columns = if (filesAdapter.grid) COLUMNS else 1
            if (position != null && (position % columns == columns - 1 || position == filesAdapter.itemCount - 1)) {
                fab.requestFocus()
                return true
            }
        }
        // From the round button, left returns to the file that had the focus and up goes to the top bar.
        if (currentFocus === fab && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (filesAdapter.itemCount > 0) {
                    focusItem(lastPosition.coerceAtMost(filesAdapter.itemCount - 1))
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    (0 until toolRow.childCount).map { toolRow.getChildAt(it) }.lastOrNull { it.visibility == View.VISIBLE }?.requestFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Back leaves the selection, then goes up one folder, then to the places, then closes the app. */
    private fun goBack() {
        if (selecting) {
            stopSelecting()
            return
        }
        val current = dir
        val root = place?.dir
        val inFiles = currentFocus?.let { isInside(it, filesList) } == true
        if (current != null && root != null && current != root && Places.isInside(current, root)) {
            val parent = current.parentFile ?: return finish()
            openDir(parent, focusFirst = false, keepFocus = current.name)
            return
        }
        if (inFiles || currentFocus?.let { isInside(it, placesList) } != true) {
            val index = placesAdapter.places.indexOfFirst { it.dir == place?.dir }.coerceAtLeast(0)
            placesList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
            return
        }
        finish()
    }

    // ---------- Updates

    private fun checkForUpdate() {
        if (!settings.updateCheckEnabled) {
            availableUpdate = null
            updateButton.visibility = View.GONE
            return
        }
        if (availableUpdate != null || updateJob?.isActive == true) return
        val checker = UpdateChecker(
            UrlConnectionHttpGet(),
            BuildConfig.VERSION_NAME,
            BuildConfig.UPDATE_REPO,
            BuildConfig.UPDATE_TAG_PREFIX,
            settings,
            System::currentTimeMillis,
        )
        updateJob = scope.launch {
            availableUpdate = checker.checkIfDue() ?: availableUpdate
            updateButton.visibility = if (availableUpdate == null) View.GONE else View.VISIBLE
        }
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val SIDEBAR = 23f
        const val TOOLBAR = 6.4f
        const val COLUMNS = 6
        const val REQUEST_STORAGE = 5101
        const val REQUEST_TREE = 5102
        const val REQUEST_MENU = 5103
        const val STATE_DIR = "dir"
        val STORAGE_PERMISSIONS = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
