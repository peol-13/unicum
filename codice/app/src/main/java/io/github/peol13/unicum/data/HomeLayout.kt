package io.github.peol13.unicum.data

/** Le card che si possono mettere nella Home. */
enum class HomeCard(val label: String, val description: String) {
    BANNER("Messaggio del giorno", "Stato del giorno e consigli"),
    CALORIE("Calorie", "Anello, deficit e macronutrienti"),
    CALORIE_COMPATTA("Calorie in breve", "Calorie e linea del deficit in una riga"),
    ATTIVITA("Attività", "Minuti, passi e ultimi 7 giorni"),
    SONNO("Sonno", "Durata, efficienza e fasi della notte"),
    ATTIVITA_SONNO("Attività e sonno", "Due card piccole affiancate"),
    RECUPERO_SONNO("Recupero e sonno", "HRV e anello del sonno affiancati"),
    RECUPERO_ATTIVITA("Recupero e attività", "HRV e attività affiancati"),
    PASTI("Pasti", "Calorie di colazione, pranzo e cena"),
    ALLENAMENTI("Allenamenti", "Gli allenamenti del giorno"),
    PESO("Peso e composizione", "Ultima misura e tendenza")
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
