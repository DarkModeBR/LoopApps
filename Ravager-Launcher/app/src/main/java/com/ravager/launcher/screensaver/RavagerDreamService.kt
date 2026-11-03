package com.ravager.launcher.screensaver

import android.service.dreams.DreamService
import com.ravager.launcher.settings.AppSettings
import com.ravager.launcher.ui.AppFonts
import com.ravager.launcher.ui.BaseActivity

/** Lets the same screen be chosen as the system screensaver, on devices that offer the choice. */
class RavagerDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        AppFonts.use(this)
        setContentView(SaverViews.create(BaseActivity.localized(this), AppSettings(this).saverStyle))
    }
}
