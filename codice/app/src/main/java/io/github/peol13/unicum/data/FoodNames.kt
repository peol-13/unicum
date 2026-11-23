package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.AppLanguage
import io.github.peol13.unicum.i18n.I18n

/**
 * Nomi degli alimenti del catalogo e delle porzioni ("fetta", "mela media"…) nelle lingue dell'app.
 * Nel database e nei pasti salvati resta il nome italiano del catalogo: si traduce solo quando si mostra
 * ([label]) e la ricerca trova l'alimento sia in italiano sia nella lingua dell'app.
 * Tabella in `resources/io/github/peol13/unicum/i18n/foods.psv` (una riga per voce, colonne nell'ordine di
 * [AppLanguage]); `FoodNamesTest` controlla che ogni alimento e ogni porzione abbia tutte le lingue.
 */
object FoodNames {
    private const val RESOURCE = "/io/github/peol13/unicum/i18n/foods.psv"

    /** Nome italiano → nomi in tutte le lingue (nell'ordine di [AppLanguage]). */
    val table: Map<String, List<String>> by lazy {
        FoodNames::class.java.getResourceAsStream(RESOURCE)?.bufferedReader(Charsets.UTF_8)?.use { parse(it.readText()) }
            ?: emptyMap()
    }

    fun parse(text: String): Map<String, List<String>> =
        text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line -> line.split('|').map { it.trim() } }
            .filter { it.size == AppLanguage.entries.size && it.all { c -> c.isNotEmpty() } }
            .associateBy { it[0] }

    /** Il nome da mostrare nella lingua dell'app; quelli che non sono nel catalogo (personali, marche) restano così. */
    fun label(name: String, language: AppLanguage = I18n.language): String {
        if (language == AppLanguage.IT) return name
        return table[name]?.getOrNull(language.ordinal) ?: name
    }
}
