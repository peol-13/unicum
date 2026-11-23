package io.github.peol13.unicum.i18n

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Lingue dell'app. L'italiano è la lingua del codice: i testi si scrivono in italiano e si traducono. */
enum class AppLanguage(val code: String, val nativeName: String) {
    IT("it", "Italiano"),
    EN("en", "English"),
    ES("es", "Español"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    PT("pt", "Português");

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        fun ofCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

        /** Lingua del telefono se l'app la conosce, altrimenti inglese. */
        fun fromSystem(locale: Locale = Locale.getDefault()): AppLanguage = ofCode(locale.language) ?: EN
    }
}

/**
 * Traduzioni "alla gettext": la chiave è il testo italiano scritto nel codice, i file in
 * `resources/io/github/peol13/unicum/i18n/` danno le altre lingue (righe `it:`, `en:`, `es:`…, una voce per blocco).
 *
 * - `tr("Calorie rimaste")` restituisce il testo nella lingua scelta;
 * - `tr("Mancano %d minuti", n)` lo formatta con `String.format` (con argomenti, `%` letterale = `%%`);
 * - `trp(n, "%d giorno", "%d giorni")` sceglie singolare/plurale e formatta con `n`.
 *
 * La lingua è uno stato di Compose: cambiandola le schermate si ridisegnano da sole.
 * Fuori dall'app (test unitari) resta l'italiano, così i test leggono i testi originali.
 * `I18nTest` controlla che ogni chiave usata nel codice sia tradotta in tutte le lingue.
 */
object I18n {
    const val PREF_KEY = "language"
    private const val PREFS = "unicum_settings"

    /** File del catalogo, in ordine; ogni voce può stare in uno qualunque. */
    val FILES = listOf(
        "common", "home", "meals", "workouts", "profile", "setup", "stats", "data"
    )

    private val current = mutableStateOf(AppLanguage.IT)

    /** "" = segue il telefono; altrimenti il codice della lingua scelta a mano. */
    var choice = mutableStateOf("")
        private set

    val language: AppLanguage get() = current.value
    val locale: Locale get() = current.value.locale

    @Volatile private var catalog: Map<String, Map<AppLanguage, String>>? = null

    /** Legge la scelta salvata (chiamata da [io.github.peol13.unicum.UnicumApplication] all'avvio). */
    fun init(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_KEY, "") ?: ""
        val system = AppLocales.applicationLocale(context)
        // Android 13+: la lingua scelta nelle impostazioni di sistema per l'app vince sulla preferenza
        val code = system?.let { AppLanguage.ofCode(it.language)?.code } ?: saved
        choice.value = if (AppLanguage.ofCode(code) != null) code else ""
        current.value = AppLanguage.ofCode(choice.value) ?: AppLanguage.fromSystem()
    }

    /** Scelta dal Profilo: "" = come il telefono. */
    fun choose(context: Context, code: String) {
        val valid = if (AppLanguage.ofCode(code) != null) code else ""
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_KEY, valid).apply()
        choice.value = valid
        current.value = AppLanguage.ofCode(valid) ?: AppLanguage.fromSystem(AppLocales.systemLocale())
        AppLocales.apply(context, valid)
    }

    /** Solo per i test: forza una lingua. */
    fun setForTest(language: AppLanguage) {
        current.value = language
    }

    fun translate(key: String, language: AppLanguage = this.language): String {
        if (language == AppLanguage.IT) return key
        return entries()[key]?.get(language) ?: key
    }

    /** Tutte le voci del catalogo: chiave italiana → traduzioni. */
    fun entries(): Map<String, Map<AppLanguage, String>> =
        catalog ?: synchronized(this) { catalog ?: load().also { catalog = it } }

    private fun load(): Map<String, Map<AppLanguage, String>> {
        val out = HashMap<String, Map<AppLanguage, String>>(4096)
        for (name in FILES) {
            val text = I18n::class.java.getResourceAsStream("/io/github/peol13/unicum/i18n/$name.txt")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: continue
            parse(text).forEach { (k, v) -> out[k] = v }
        }
        return out
    }

    /**
     * Blocchi separati da righe vuote; ogni riga `xx: testo` (xx = codice lingua). `#` = commento.
     * Nel testo `\n` è un a capo.
     */
    fun parse(text: String): List<Pair<String, Map<AppLanguage, String>>> {
        val out = mutableListOf<Pair<String, Map<AppLanguage, String>>>()
        var block = LinkedHashMap<AppLanguage, String>()
        fun flush() {
            block[AppLanguage.IT]?.let { out += it to block }
            block = LinkedHashMap()
        }
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd('\r')
            if (line.isBlank()) { flush(); continue }
            if (line.startsWith("#")) continue
            val colon = line.indexOf(": ")
            if (colon != 2) continue
            val lang = AppLanguage.ofCode(line.substring(0, 2)) ?: continue
            block[lang] = line.substring(4).replace("\\n", "\n")
        }
        flush()
        return out
    }
}

/** Testo tradotto nella lingua dell'app. Con argomenti è un formato di `String.format`. */
fun tr(key: String): String = I18n.translate(key)

fun tr(key: String, vararg args: Any?): String = String.format(I18n.locale, I18n.translate(key), *args)

/**
 * Segna un testo da tradurre più tardi con [tr] (etichette degli enum, nomi salvati come i tipi di pasto):
 * restituisce il testo così com'è, serve solo a `I18nTest` per sapere che va nel catalogo.
 */
fun tk(key: String): String = key

/** Singolare o plurale (in francese anche lo 0 è singolare), formattato con [n]. */
fun trp(n: Int, one: String, other: String): String {
    val singular = if (I18n.language == AppLanguage.FR) n in -1..1 else n == 1 || n == -1
    return tr(if (singular) one else other, n)
}

/** Numero intero con il separatore delle migliaia della lingua dell'app ("1.795", "1,795"). */
fun lnum(v: Int): String = java.text.NumberFormat.getIntegerInstance(I18n.locale).format(v)

/** Formatta un numero con la lingua dell'app (es. "%.1f"). */
fun lfmt(format: String, vararg args: Any?): String = String.format(I18n.locale, format, *args)
