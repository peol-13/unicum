package io.github.peol13.unicum.i18n

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Lingua per app di Android 13+ (Impostazioni → App → UNICUM → Lingua). Sotto Android 13 non esiste:
 * vale solo la scelta fatta nel Profilo. Il file `res/xml/locales_config.xml` elenca le lingue.
 */
internal object AppLocales {

    /** Lingua scelta per l'app nelle impostazioni di sistema, se c'è. */
    fun applicationLocale(context: Context): Locale? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = runCatching { context.getSystemService(LocaleManager::class.java)?.applicationLocales }.getOrNull()
        return locales?.takeIf { !it.isEmpty }?.get(0)
    }

    /** Lingua del telefono (non quella scelta per l'app). */
    fun systemLocale(): Locale = Resources.getSystem().configuration.locales[0] ?: Locale.getDefault()

    /** Allinea la lingua di sistema dell'app (testi di Android: nome, widget, notifiche) alla scelta. */
    fun apply(context: Context, code: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        }
    }
}
