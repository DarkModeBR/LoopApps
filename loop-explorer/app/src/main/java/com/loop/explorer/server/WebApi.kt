package com.loop.explorer.server

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.content.FileProvider
import com.loop.explorer.files.FileKind
import com.loop.explorer.files.Listing
import com.loop.explorer.files.Operations
import com.loop.explorer.files.OperationError
import com.loop.explorer.files.Places
import com.loop.explorer.files.Writers
import com.loop.explorer.settings.AppSettings
import com.loop.explorer.settings.SortOrder
import com.loop.explorer.theme.Themes
import com.loop.explorer.ui.BaseActivity
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import com.loop.explorer.files.FileItem
import com.loop.explorer.files.Thumbs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/** Everything the web page can ask for: files, device status, terminal, apps, screen and power. */
class WebApi(private val context: Context, private val shell: Shell) {
    private val icons = ConcurrentHashMap<String, ByteArray>()
    private val thumbs by lazy { Thumbs(context, THUMB) }
    private val main = Handler(Looper.getMainLooper())
    private val iconFont by lazy { context.assets.open("web/bootstrap-icons.woff2").use { it.readBytes() } }
    private val work: File get() = context.getExternalFilesDir(null) ?: context.cacheDir

    fun handle(request: Request): Response {
        val path = request.path
        refusal(request)?.let { return it }
        Clients.seen(request.remote, request.headers["user-agent"])
        return when {
            path == "/api/hello" && request.method == "POST" -> {
                Clients.hello(request.remote, request.json())
                Response.json(JSONObject().put("ok", true))
            }
            path == "/" || path == "/index.html" -> page()
            path == "/bi.woff2" -> Response.bytes(iconFont, "font/woff2", headers = mapOf("Cache-Control" to "max-age=604800"))
            path == "/api/thumb" -> thumb(request)
            path == "/api/info" -> info()
            path == "/api/roots" -> roots()
            path == "/api/list" -> list(request)
            path == "/api/file" -> file(request)
            path == "/api/upload" && request.method == "PUT" -> upload(request)
            path == "/api/mkdir" && request.method == "POST" -> mkdir(request)
            path == "/api/rename" && request.method == "POST" -> rename(request)
            path == "/api/delete" && request.method == "POST" -> operation(request) { ops, body -> ops.delete(files(body)) }
            path == "/api/copy" && request.method == "POST" -> operation(request) { ops, body ->
                val move = body.optBoolean("move")
                ops.copy(files(body, rootsToo = !move), folder(body.optString("dest")), move)
            }
            path == "/api/zip" && request.method == "POST" -> operation(request) { ops, body ->
                val name = body.optString("name").trim().ifBlank { "arquivos" }.removeSuffix(".zip") + ".zip"
                if (!valid(name)) throw OperationError(OperationError.Reason.FAILED)
                ops.zip(files(body, rootsToo = true), folder(body.optString("dir")), name)
            }
            path == "/api/unzip" && request.method == "POST" -> operation(request) { ops, body ->
                val zip = allowed(File(body.optString("path"))) ?: throw OperationError(OperationError.Reason.NO_ACCESS)
                ops.unzip(zip, zip.parentFile ?: throw OperationError(OperationError.Reason.FAILED))
            }
            path == "/api/status" -> Response.json(DeviceReport(context).collect())
            path == "/api/shell" && request.method == "POST" -> shell(request)
            path == "/api/apps" -> apps(request)
            path == "/api/appicon" -> appIcon(request)
            path.startsWith("/api/apps/") && request.method == "POST" -> appAction(path.removePrefix("/api/apps/"), request)
            path == "/api/install" && request.method == "PUT" -> install(request)
            path == "/api/screenshot" -> screenshot()
            path == "/api/power" && request.method == "POST" -> power(request)
            else -> Response.error(404, "not found")
        }
    }

    // ---------- Protection from other web pages

    /**
     * A web page on another site, opened in a browser on this network, could send requests here
     * without the user knowing. Two checks stop that: the address typed must be this device's
     * number (a site's own name pointing here is refused), and anything that changes something
     * must carry a header that only this page sends, which browsers do not let other sites add.
     */
    private fun refusal(request: Request): Response? {
        val host = request.headers["host"].orEmpty()
        val name = if (host.startsWith("[")) host.substringBefore(']') + "]" else host.substringBefore(':')
        val trusted = host.isEmpty() || name == "localhost" || name.startsWith("[") || IPV4.matches(name)
        if (!trusted) return Response.error(403, "host")
        if (request.method == "GET" || request.method == "HEAD") return null
        if (request.headers["x-loop"] != "1") return Response.error(403, "origin")
        val origin = request.headers["origin"]
        if (origin != null && origin != "http://$host") return Response.error(403, "origin")
        return null
    }

    // ---------- Page

    /** The page follows the app: dark or light, the chosen color and the language. */
    private fun page(): Response {
        val settings = AppSettings(context)
        val theme = Themes.build(settings.themeMode, settings.accent)
        val html = context.assets.open("web/index.html").use { it.readBytes().toString(Charsets.UTF_8) }
            .replace("{{THEME}}", if (theme.dark) "dark" else "light")
            .replace("{{ACCENT}}", Themes.hex(theme.accent))
            .replace("{{ON_ACCENT}}", Themes.hex(theme.onAccent))
            .replace("{{BAR}}", Themes.hex(theme.bar))
            .replace("{{ON_BAR}}", Themes.hex(theme.onBar))
            .replace("{{FOLDER}}", Themes.hex(theme.folder))
            .replace("{{MONO}}", if (theme.neutral) "1" else "0")
            .replace("{{LANG}}", BaseActivity.currentLanguage(context))
            .replace("{{DEVICE}}", (Build.MANUFACTURER + " " + Build.MODEL).replace("<", ""))
        return Response.text(html, "text/html; charset=utf-8")
    }

    private fun info(): Response = Response.json(
        JSONObject()
            .put("shell", shell.mode().name)
            .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            .put("api", Build.VERSION.SDK_INT),
    )

    // ---------- Files

    /** Only the storages can be reached from the network, never the private folders of apps. */
    private fun allowed(file: File): File? {
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val roots = Places.storages(context).mapNotNull { runCatching { it.dir.canonicalFile }.getOrNull() }
        return target.takeIf { candidate -> roots.any { Places.isInside(candidate, it) } }
    }

    private fun folder(path: String): File =
        allowed(File(path))?.takeIf { it.isDirectory } ?: throw OperationError(OperationError.Reason.NO_ACCESS)

    private fun files(body: JSONObject, rootsToo: Boolean = false): List<File> {
        val paths = body.optJSONArray("paths") ?: JSONArray()
        val list = (0 until paths.length()).map { allowed(File(paths.optString(it))) ?: throw OperationError(OperationError.Reason.NO_ACCESS) }
        if (!rootsToo && list.any(::isRoot)) throw OperationError(OperationError.Reason.NO_ACCESS)
        return list
    }

    private fun isRoot(file: File) = Places.storages(context).any { runCatching { it.dir.canonicalFile }.getOrNull() == file }

    private fun roots(): Response = Response.json(
        JSONArray(
            Places.storages(context).map {
                JSONObject().put("label", it.label).put("path", it.dir.path).put("total", it.total).put("free", it.free).put("usb", it.kind.name == "USB")
            },
        ),
    )

    private fun list(request: Request): Response {
        val dir = allowed(File(request.param("path").orEmpty())) ?: return Response.error(403, "forbidden")
        val hidden = request.param("hidden") == "1"
        val items = Listing.list(dir, hidden, SortOrder.NAME) ?: return Response.error(403, "unreadable")
        val parent = dir.parentFile?.let { allowed(it) }
        return Response.json(
            JSONObject()
                .put("path", dir.path)
                .put("parent", parent?.path ?: JSONObject.NULL)
                .put(
                    "items",
                    JSONArray(
                        items.map {
                            JSONObject().put("name", it.name).put("path", it.file.path).put("dir", it.isDir)
                                .put("size", it.size).put("modified", it.modified).put("kind", it.kind.name)
                                .put("count", if (it.isDir) it.file.list()?.size ?: JSONObject.NULL else JSONObject.NULL)
                        },
                    ),
                ),
        )
    }

    private fun file(request: Request): Response {
        val file = allowed(File(request.param("path").orEmpty()))?.takeIf { it.isFile } ?: return Response.error(404, "not found")
        return Response.file(file, FileKind.mime(file.name), request.headers["range"], request.param("download") == "1")
    }

    /** The body is the file itself, sent by the page with PUT, so nothing needs to be parsed. */
    private fun upload(request: Request): Response {
        val dir = allowed(File(request.param("dir").orEmpty()))?.takeIf { it.isDirectory } ?: return Response.error(403, "forbidden")
        val wanted = request.param("name").orEmpty().substringAfterLast('/').substringAfterLast('\\').trim()
        if (wanted.isEmpty() || wanted == "." || wanted == "..") return Response.error(400, "name")
        val writer = Writers.forDir(context, dir) ?: return Response.error(403, "no_access")
        val name = Writers.freeName(dir, wanted)
        val (target, output) = writer.create(dir, name) ?: return Response.error(500, "failed")
        return try {
            output.use { request.body.copyTo(it, Response.BUFFER) }
            Response.json(JSONObject().put("name", target.name))
        } catch (error: Throwable) {
            writer.delete(target)
            Response.error(500, error.message ?: "failed")
        }
    }

    private fun mkdir(request: Request): Response {
        val body = request.json()
        val dir = allowed(File(body.optString("dir")))?.takeIf { it.isDirectory } ?: return Response.error(403, "forbidden")
        val name = body.optString("name").trim()
        if (!valid(name)) return Response.error(400, "name")
        if (File(dir, name).exists()) return Response.error(409, "exists")
        val writer = Writers.forDir(context, dir) ?: return Response.error(403, "no_access")
        return if (writer.makeDir(dir, name) != null) Response.json(JSONObject().put("ok", true)) else Response.error(500, "failed")
    }

    private fun rename(request: Request): Response {
        val body = request.json()
        val file = allowed(File(body.optString("path")))?.takeIf { !isRoot(it) } ?: return Response.error(403, "forbidden")
        val name = body.optString("name").trim()
        if (!valid(name)) return Response.error(400, "name")
        // Storage ignores upper and lower case, so "foto" to "Foto" is the same file, not a taken name.
        if (File(file.parentFile, name).exists() && !name.equals(file.name, ignoreCase = true)) return Response.error(409, "exists")
        val writer = Writers.forDir(context, file.parentFile ?: file) ?: return Response.error(403, "no_access")
        return if (writer.rename(file, name) != null) Response.json(JSONObject().put("ok", true)) else Response.error(500, "failed")
    }

    private fun valid(name: String) = name.isNotEmpty() && name != "." && name != ".." && name.none { it in "/\\:*?\"<>|" }

    /** Copy, move, delete and ZIP run while the browser waits, with the same code as the app. */
    private fun operation(request: Request, work: suspend (Operations, JSONObject) -> Unit): Response {
        val body = request.json()
        return try {
            runBlocking { work(Operations(context) { _, _, _ -> }, body) }
            Response.json(JSONObject().put("ok", true))
        } catch (error: OperationError) {
            Response.error(400, error.reason.name)
        }
    }

    // ---------- Terminal

    private fun shell(request: Request): Response {
        val command = request.json().optString("command").trim()
        if (command.isEmpty()) return Response.error(400, "empty")
        val result = shell.run(command)
        return Response.json(JSONObject().put("exit", result.exitCode).put("output", result.output).put("mode", result.mode.name))
    }

    // ---------- Apps

    private fun apps(request: Request): Response {
        val manager = context.packageManager
        val showSystem = request.param("system") == "1"
        val list = runCatching { manager.getInstalledApplications(0) }.getOrDefault(emptyList())
            .filter { showSystem || it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || launchIntent(it.packageName) != null }
            .map { app ->
                val info = runCatching { manager.getPackageInfo(app.packageName, 0) }.getOrNull()
                JSONObject()
                    .put("package", app.packageName)
                    .put("label", runCatching { app.loadLabel(manager).toString() }.getOrDefault(app.packageName))
                    .put("version", info?.versionName.orEmpty())
                    .put("system", app.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    .put("enabled", app.enabled)
                    .put("launchable", launchIntent(app.packageName) != null)
                    .put("size", runCatching { File(app.sourceDir).length() }.getOrDefault(0L))
            }
            .sortedBy { it.optString("label").lowercase() }
        return Response.json(JSONObject().put("apps", JSONArray(list)).put("shell", shell.mode().name))
    }

    private fun launchIntent(pkg: String): Intent? {
        val manager = context.packageManager
        return manager.getLeanbackLaunchIntentForPackage(pkg) ?: manager.getLaunchIntentForPackage(pkg)
    }

    /**
     * The same small pictures the TV shows: photos, video frames, music covers and APK icons. The
     * thumbnail loader is used from the main thread only, as on the TV screen.
     */
    private fun thumb(request: Request): Response {
        val file = allowed(File(request.param("path").orEmpty()))?.takeIf { it.isFile } ?: return Response.error(404, "not found")
        val item = FileItem.of(file)
        if (!thumbs.hasPicture(item.kind)) return Response.error(404, "no picture")
        val latch = CountDownLatch(1)
        var picture: Bitmap? = null
        main.post {
            thumbs.load(item) {
                picture = it
                latch.countDown()
            }
        }
        if (!latch.await(THUMB_WAIT_S, TimeUnit.SECONDS)) return Response.error(404, "slow")
        val bitmap = picture ?: return Response.error(404, "no picture")
        val png = item.kind == FileKind.APK
        val data = ByteArrayOutputStream().also {
            bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, if (png) 100 else 82, it)
        }.toByteArray()
        return Response.bytes(data, if (png) "image/png" else "image/jpeg", headers = mapOf("Cache-Control" to "max-age=86400"))
    }

    private fun appIcon(request: Request): Response {
        val pkg = request.param("package").orEmpty()
        val png = icons.getOrPut(pkg) {
            val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return Response.error(404, "not found")
            val bitmap = Bitmap.createBitmap(ICON, ICON, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, ICON, ICON)
            drawable.draw(Canvas(bitmap))
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        return Response.bytes(png, "image/png")
    }

    private fun appAction(action: String, request: Request): Response {
        val pkg = request.json().optString("package")
        if (pkg.isEmpty() || pkg.any { !it.isLetterOrDigit() && it != '.' && it != '_' }) return Response.error(400, "package")
        val result = when (action) {
            "launch" -> {
                val component = launchIntent(pkg)?.component?.flattenToShortString() ?: return Response.error(400, "not_launchable")
                shell.adb(listOf("am", "start", "-n", component)) ?: return startHere(launchIntent(pkg))
            }
            "stop" -> shell.adb(listOf("am", "force-stop", pkg)) ?: return Response.error(403, "needs_shizuku")
            "uninstall" -> {
                val app = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return Response.error(404, "not found")
                if (app.flags and ApplicationInfo.FLAG_SYSTEM != 0) return Response.error(403, "system_app")
                if (pkg == context.packageName) return Response.error(403, "self")
                shell.adb(listOf("pm", "uninstall", pkg))
                    ?: return startHere(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")))
            }
            else -> return Response.error(404, "not found")
        }
        return Response.json(JSONObject().put("exit", result.exitCode).put("output", result.output.trim()))
    }

    /** Without Shizuku the TV has to show the system screen; Android only allows it while the app is open. */
    private fun startHere(intent: Intent?): Response {
        intent ?: return Response.error(400, "failed")
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        return if (started) Response.json(JSONObject().put("onTv", true)) else Response.error(403, "needs_shizuku")
    }

    /** The APK is kept where the shell can read it, then installed with pm, or by the TV's installer. */
    private fun install(request: Request): Response {
        // APKs handed to the TV's installer cannot be removed while it reads them; old ones go now.
        work.listFiles { f -> f.name.startsWith("upload-") && f.name.endsWith(".apk") }?.forEach { it.delete() }
        val apk = File(work, "upload-${System.nanoTime()}.apk")
        runCatching { apk.outputStream().use { request.body.copyTo(it, Response.BUFFER) } }.onFailure {
            apk.delete()
            return Response.error(500, "failed")
        }
        val result = shell.adb(listOf("pm", "install", "-r", apk.path))
        if (result != null) {
            apk.delete()
            return Response.json(JSONObject().put("exit", result.exitCode).put("output", result.output.trim()))
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return startHere(intent)
    }

    // ---------- Screen and power

    private fun screenshot(): Response {
        // One file per request: the page can ask again before the last capture is read.
        val shot = File(work, "screen-${System.nanoTime()}.png")
        shell.adb(listOf("screencap", "-p", shot.path)) ?: return Response.error(403, "needs_shizuku")
        val bytes = runCatching { shot.readBytes() }.getOrNull()
        shot.delete()
        if (bytes == null || bytes.isEmpty()) return Response.error(500, "failed")
        return Response.bytes(bytes, "image/png")
    }

    private fun power(request: Request): Response {
        val action = PowerAction.entries.firstOrNull { it.name.equals(request.json().optString("action"), ignoreCase = true) }
            ?: return Response.error(400, "action")
        // The answer goes first: restarting takes the server down with it.
        Thread {
            Thread.sleep(POWER_DELAY_MS)
            for (attempt in action.attempts) {
                val result = shell.adb(attempt) ?: break
                if (result.exitCode == 0) break
            }
        }.start()
        return if (shell.mode() == Shell.Mode.ADB) Response.json(JSONObject().put("ok", true)) else Response.error(403, "needs_shizuku")
    }

    private companion object {
        const val ICON = 96
        const val THUMB = 240
        const val THUMB_WAIT_S = 8L
        const val POWER_DELAY_MS = 600L
        val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    }
}
