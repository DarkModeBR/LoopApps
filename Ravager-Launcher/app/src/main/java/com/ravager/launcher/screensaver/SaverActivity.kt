package com.ravager.launcher.screensaver

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.ui.BaseActivity

/** Screensaver shown when the home screen sits idle. Any key closes it without acting on the card behind. */
class SaverActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = intent.getStringExtra(EXTRA_STYLE)?.let(SaverStyle::fromSaved) ?: AppSettings(this).saverStyle
        setContentView(SaverViews.create(this, style))
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) finish()
        return true
    }

    companion object {
        private const val EXTRA_STYLE = "style"

        /** Shows a model before it is chosen. */
        fun preview(context: Context, style: SaverStyle): Intent =
            Intent(context, SaverActivity::class.java).putExtra(EXTRA_STYLE, style.name)
    }
}
