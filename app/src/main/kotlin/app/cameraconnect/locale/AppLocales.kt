package app.cameraconnect.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/**
 * The language the UI is shown in, independent of the device language.
 *
 * On Android 13+ the choice is stored by the system ([LocaleManager]): it is the same setting as
 * Settings → Apps → Camera Connect → Language, the system applies it to every context and recreates
 * the activities when it changes. Below that, the choice is kept in SharedPreferences and applied to
 * each activity through [wrap]; only activity contexts see it, which is enough because no string is
 * read outside the UI.
 */
class AppLocales(private val context: Context) {

    fun current(): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromLanguage(locales[0].language)
        } else {
            AppLanguage.fromLanguage(storedLanguage(context))
        }

    /** Applies [language]; on Android 12 and below [activity] is recreated so its resources pick it up. */
    fun apply(language: AppLanguage, activity: Activity?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                language.tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (language.tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, language.tag)
            }
            activity?.recreate()
        }
    }

    companion object {
        private const val PREFS = "locale"
        private const val KEY_LANGUAGE = "language"

        private fun storedLanguage(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)

        /** For `Activity.attachBaseContext` on Android 12 and below; returns [base] untouched elsewhere. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = storedLanguage(base) ?: return base
            val configuration = Configuration(base.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags(tag))
            }
            return base.createConfigurationContext(configuration)
        }
    }
}
