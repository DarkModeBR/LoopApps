package com.ravager.launcher.settings

enum class LanguageChoice { SYSTEM, PT, EN }

object LocalePolicy {
    fun resolve(choice: LanguageChoice, systemLanguage: String): String = when (choice) {
        LanguageChoice.PT -> "pt"
        LanguageChoice.EN -> "en"
        LanguageChoice.SYSTEM -> if (systemLanguage.lowercase().startsWith("pt")) "pt" else "en"
    }
}
