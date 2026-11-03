package com.ravager.launcher.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import com.ravager.launcher.R
import kotlinx.coroutines.CoroutineExceptionHandler

/** Boxes often lack system screens or refuse them; that must never crash the launcher. */
inline fun opened(start: () -> Unit): Boolean = try {
    start()
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/** Opens a system screen, telling the user when the device does not have it. */
fun Activity.openOrWarn(intent: Intent) {
    if (!opened { startActivity(intent) }) {
        Toast.makeText(this, R.string.screen_unavailable, Toast.LENGTH_LONG).show()
    }
}

/** This app is the home screen: a failure in background work must not take it down. */
object ScreenErrors {
    fun handler(onError: (Throwable) -> Unit = { android.util.Log.w("Ravager", "Background work failed", it) }) =
        CoroutineExceptionHandler { _, error -> onError(error) }
}
