package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tk

/**
 * Le novità di ogni versione, mostrate una volta dopo l'aggiornamento (finestra nella Home) e dal
 * Profilo → Informazioni. Testi in italiano con [tk]: si traducono quando si mostrano.
 * A ogni release: una voce in cima a [releases] con la versione uguale a `versionName`.
 */
object WhatsNew {
    data class Release(val version: String, val items: List<String>)

    val releases = listOf(
        Release(
            "1.1.0", listOf(
                tk("Pesi e corpo libero negli Allenamenti: sedute con esercizi, serie e carichi, e i kg di ogni esercizio nel tempo."),
                tk("Registrare un pasto è più veloce: il + aggiunge al volo, la quantità si sceglie in un foglio a parte e il pasto sta in una barra in basso."),
                tk("Copia un pasto in un altro giorno dal Diario (o \"Ripeti da ieri\"), e \"Ripristina\" dopo un'eliminazione."),
                tk("Primo avvio più chiaro: lingua, collegamento a Health Connect, una guida rapida alle funzioni principali e la card \"Primi passi\" nella Home."),
                tk("Alimenti e porzioni tradotti, e unità imperiali (lb, ft) per peso, altezza e carichi."),
                tk("Giornate complete: le medie e il peso stimato contano solo i giorni registrati per intero."),
                tk("Il lunedì una notifica con il riepilogo della settimana appena finita."),
                tk("Un avviso nella Home quando esce una nuova versione.")
            )
        ),
        Release(
            "1.0.2", listOf(
                tk("L'app parla inglese, spagnolo, francese, tedesco e portoghese."),
                tk("Più stabile in molte schermate.")
            )
        ),
        Release(
            "1.0.1", listOf(
                tk("Statistiche d'uso anonime, solo se dici sì."),
                tk("Import dei PDF più preciso.")
            )
        )
    )

    /** La versione senza suffissi ("1.1.0-debug" → "1.1.0"). */
    fun core(version: String): String = version.substringBefore('-').substringBefore('+').trim()

    /** Le novità della versione installata, se ci sono. */
    fun forVersion(version: String): Release? = releases.firstOrNull { it.version == core(version) }
}
