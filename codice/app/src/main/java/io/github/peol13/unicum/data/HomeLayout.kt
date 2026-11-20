package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tk

import io.github.peol13.unicum.i18n.tr

/** Le card che si possono mettere nella Home. */
enum class HomeCard(private val labelIt: String, private val descriptionIt: String) {
    BANNER(tk("Messaggio del giorno"), tk("Stato del giorno e consigli")),
    CALORIE(tk("Calorie"), tk("Anello, deficit e macronutrienti")),
    CALORIE_COMPATTA(tk("Calorie in breve"), tk("Calorie e linea del deficit in una riga")),
    ATTIVITA(tk("Attività"), tk("Minuti, passi e ultimi 7 giorni")),
    SONNO(tk("Sonno"), tk("Durata, efficienza e fasi della notte")),
    ATTIVITA_SONNO(tk("Attività e sonno"), tk("Due card piccole affiancate")),
    RECUPERO_SONNO(tk("Recupero e sonno"), tk("HRV e anello del sonno affiancati")),
    RECUPERO_ATTIVITA(tk("Recupero e attività"), tk("HRV e attività affiancati")),
    PASTI(tk("Pasti"), tk("Calorie di colazione, pranzo e cena")),
    ALLENAMENTI(tk("Allenamenti"), tk("Gli allenamenti del giorno")),
    PESO(tk("Peso e composizione"), tk("Ultima misura e tendenza"));

    val label: String get() = tr(labelIt)
    val description: String get() = tr(descriptionIt)
}

/**
 * Ordine delle card nella Home. Ogni modalità (Nutrizione, Attività, Recupero) ha il suo ordine di partenza;
 * l'utente può riordinare, togliere e aggiungere card. Logica pura, con test.
 */
object HomeLayout {

    fun default(mode: DashboardMode): List<HomeCard> = when (mode) {
        DashboardMode.NUTRIZIONE -> listOf(
            HomeCard.BANNER, HomeCard.CALORIE, HomeCard.ATTIVITA_SONNO, HomeCard.PASTI, HomeCard.PESO
        )
        DashboardMode.ATTIVITA -> listOf(
            HomeCard.BANNER, HomeCard.ATTIVITA, HomeCard.RECUPERO_SONNO, HomeCard.CALORIE_COMPATTA,
            HomeCard.ALLENAMENTI, HomeCard.PESO
        )
        DashboardMode.RECUPERO -> listOf(
            HomeCard.BANNER, HomeCard.SONNO, HomeCard.RECUPERO_ATTIVITA, HomeCard.CALORIE_COMPATTA,
            HomeCard.PASTI, HomeCard.PESO
        )
    }

    fun encode(cards: List<HomeCard>): String = cards.joinToString(",") { it.name }

    /** Legge l'ordine salvato: nomi sconosciuti e doppioni si ignorano; null se non resta nulla. */
    fun decode(text: String?): List<HomeCard>? =
        text?.split(",")
            ?.mapNotNull { name -> HomeCard.entries.firstOrNull { it.name == name.trim() } }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }

    /** Sposta la card in posizione [from] alla posizione [to]. */
    fun move(cards: List<HomeCard>, from: Int, to: Int): List<HomeCard> {
        if (from !in cards.indices || to !in cards.indices || from == to) return cards
        return cards.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Le card che non sono nella Home, nell'ordine dell'elenco. */
    fun hidden(cards: List<HomeCard>): List<HomeCard> = HomeCard.entries.filter { it !in cards }
}
