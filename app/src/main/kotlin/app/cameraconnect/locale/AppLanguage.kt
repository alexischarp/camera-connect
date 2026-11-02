package app.cameraconnect.locale

/** UI languages the app ships (see `res/xml/locales_config.xml`); [SYSTEM] follows the device language. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    FRENCH("fr");

    companion object {
        /** Maps an ISO 639 language code ("fr") back to the app's choice; unknown or null means [SYSTEM]. */
        fun fromLanguage(language: String?): AppLanguage =
            entries.firstOrNull { it.tag != null && it.tag == language } ?: SYSTEM
    }
}
