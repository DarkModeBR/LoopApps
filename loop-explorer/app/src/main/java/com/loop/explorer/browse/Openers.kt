package com.loop.explorer.browse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.loop.explorer.R
import com.loop.explorer.apk.ApkActivity
import com.loop.explorer.files.FileItem
import com.loop.explorer.files.FileKind
import com.loop.explorer.media.ImageActivity
import com.loop.explorer.media.PlayerActivity
import com.loop.explorer.ui.opened
import java.io.File

/** What happens when a file is opened: the app's own viewers first, then other apps. */
object Openers {
    fun uri(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    /** False for kinds this app handles in its own menu, like ZIP files. */
    fun open(activity: Activity, item: FileItem): Boolean {
        when (item.kind) {
            FileKind.IMAGE -> activity.startActivity(ImageActivity.intent(activity, item.file))
            FileKind.VIDEO, FileKind.AUDIO -> activity.startActivity(PlayerActivity.intent(activity, item.file))
            FileKind.APK -> activity.startActivity(ApkActivity.intent(activity, item.file))
            FileKind.ZIP, FileKind.FOLDER -> return false
            else -> openWith(activity, item.file, chooser = false)
        }
        return true
    }

    /** Hands the file to another app; [chooser] always asks which one. */
    fun openWith(activity: Activity, file: File, chooser: Boolean) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri(activity, file), FileKind.mime(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = if (chooser) Intent.createChooser(view, activity.getString(R.string.open_with_title)) else view
        if (!opened { activity.startActivity(intent) }) {
            Toast.makeText(activity, R.string.open_no_app, Toast.LENGTH_LONG).show()
        }
    }
}
