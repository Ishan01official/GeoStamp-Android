package com.geostamp.camera.i18n

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's display language. By default (no choice stored) the app follows the system language, and
 * English is the fallback for anything not translated.
 *
 * Android 13 and later keep the choice in the system's per-app language setting, so it also appears under
 * Settings > Apps > GeoStamp > Language. Older versions store it here and apply it to each activity.
 */
object AppLanguage {
    /** Language tags with complete translations; must match res/xml/locales_config.xml. */
    val SUPPORTED: List<String> = listOf("en", "ar", "de", "es", "fr", "hi", "ja", "ko", "pt", "zh-CN")

    private const val PREFS = "app_language"
    private const val KEY_TAG = "tag"

    /** The chosen language tag, or null when following the system. */
    fun current(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales
                ?.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()?.let(::normalize)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
        }

    /** Applies [tag] (null for the system language). The activity is recreated to show the new language. */
    fun set(activity: Activity, tag: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The system persists the choice and recreates the activity itself.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
            }.apply()
            Locale.setDefault(tag?.let(Locale::forLanguageTag) ?: systemLocale())
            activity.recreate()
        }
    }

    /**
     * Before Android 13: returns [base] with the chosen language applied, for attachBaseContext and for
     * resources used outside an activity. On Android 13+ the system already does this, so [base] is returned.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = current(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(configuration)
    }

    /** The language's own name, e.g. "Español", so people can find their language whatever is shown now. */
    fun nativeName(tag: String): String {
        if (tag == "zh-CN") return "简体中文"
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }

    private fun normalize(tag: String): String = SUPPORTED.firstOrNull { it.equals(tag, ignoreCase = true) }
        ?: SUPPORTED.firstOrNull { it.substringBefore('-') == tag.substringBefore('-') } ?: tag

    private fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0]
}
