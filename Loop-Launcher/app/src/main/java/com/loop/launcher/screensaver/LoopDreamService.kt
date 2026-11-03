package com.loop.launcher.screensaver

import android.service.dreams.DreamService
import com.loop.launcher.settings.AppSettings
import com.loop.launcher.ui.AppFonts
import com.loop.launcher.ui.BaseActivity

/** Lets the same screen be chosen as the system screensaver, on devices that offer the choice. */
class LoopDreamService : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        AppFonts.use(this)
        setContentView(SaverViews.create(BaseActivity.localized(this), AppSettings(this).saverStyle))
    }
}
