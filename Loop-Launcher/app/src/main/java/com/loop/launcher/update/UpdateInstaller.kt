package com.loop.launcher.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.loop.launcher.ui.opened
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads the new version and hands it to Android's installer, which asks for confirmation. */
class UpdateInstaller(private val activity: Activity) : InstallTarget {
    suspend fun download(url: String): File? = withContext(Dispatchers.IO) {
        val folder = File(activity.cacheDir, "updates").apply { mkdirs() }
        val target = File(folder, "update.apk")
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode !in 200..299) return@withContext null
                connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            target
        } catch (_: IOException) {
            target.delete()
            null
        }
    }

    override fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    override fun openPermissionScreen(): Boolean = opened {
        activity.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
        )
    }

    override fun launchInstaller(apk: File): Boolean {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        return opened {
            activity.startActivity(
                // Goes straight to the system installer; a plain "view" also offers file managers.
                @Suppress("DEPRECATION")
                Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
