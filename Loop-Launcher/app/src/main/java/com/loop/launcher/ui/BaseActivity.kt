package com.loop.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import com.loop.launcher.settings.AppSettings
import com.loop.launcher.settings.LocalePolicy
import java.util.Locale

/** Applies the language chosen in the launcher settings to every screen. */
open class BaseActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        AppFonts.use(newBase)
        super.attachBaseContext(localized(newBase))
    }

    companion object {
        /** "pt" or "en", following the settings and then the system language. */
        fun currentLanguage(context: Context): String {
            val system = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)?.language.orEmpty()
            return LocalePolicy.resolve(AppSettings(context).language, system)
        }

        fun localized(base: Context): Context {
            val config = Configuration(base.resources.configuration)
            config.setLocale(Locale.forLanguageTag(currentLanguage(base)))
            return base.createConfigurationContext(config)
        }
    }
}
