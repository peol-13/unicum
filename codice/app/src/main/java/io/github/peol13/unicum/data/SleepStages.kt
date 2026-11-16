package io.github.peol13.unicum.data

/** Fase del sonno, dalla più "alta" (sveglio) alla più profonda, come nel grafico a gradini. */
enum class SleepStageType(val code: Char, val label: String) {
    SVEGLIO('A', "Sveglio"),
    REM('R', "REM"),
    LEGGERO('L', "Leggero"),
    PROFONDO('D', "Profondo");

    companion object {
        fun fromCode(c: Char): SleepStageType? = entries.firstOrNull { it.code == c }
    }
}

/** Un tratto continuo di una fase, con orari in millisecondi. */
data class SleepStage(val type: SleepStageType, val startMillis: Long, val endMillis: Long) {
    val minutes: Int get() = ((endMillis - startMillis) / 60_000L).toInt()
}

/**
 * Le fasi di una notte si salvano in una sola colonna di testo: "tipo:inizio:fine;tipo:inizio:fine;...".
 * Così non serve una tabella in più e una nuova sincronizzazione le sostituisce per intero.
 */
object SleepStages {

    fun encode(stages: List<SleepStage>): String =
        stages.joinToString(";") { "${it.type.code}:${it.startMillis}:${it.endMillis}" }

    /** Legge le fasi salvate; i pezzi illeggibili vengono ignorati, mai un'eccezione. */
    fun decode(text: String?): List<SleepStage> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(";").mapNotNull { part ->
            val p = part.split(":")
            if (p.size != 3) return@mapNotNull null
            val type = p[0].singleOrNull()?.let { SleepStageType.fromCode(it) } ?: return@mapNotNull null
            val start = p[1].toLongOrNull() ?: return@mapNotNull null
            val end = p[2].toLongOrNull() ?: return@mapNotNull null
            if (end <= start) null else SleepStage(type, start, end)
        }.sortedBy { it.startMillis }
    }

    /**
     * Ordina le fasi, le ritaglia dentro la sessione e unisce quelle adiacenti dello stesso tipo
     * (alcune app scrivono molti pezzetti consecutivi uguali).
     */
    fun normalize(stages: List<SleepStage>, sessionStart: Long, sessionEnd: Long): List<SleepStage> {
        val clipped = stages
            .map { SleepStage(it.type, maxOf(it.startMillis, sessionStart), minOf(it.endMillis, sessionEnd)) }
            .filter { it.endMillis > it.startMillis }
            .sortedBy { it.startMillis }
        val out = mutableListOf<SleepStage>()
        for (s in clipped) {
            val last = out.lastOrNull()
            if (last != null && last.type == s.type && s.startMillis - last.endMillis <= 1_000L) {
                out[out.lastIndex] = last.copy(endMillis = maxOf(last.endMillis, s.endMillis))
            } else {
                out += s
            }
        }
        return out
    }

    /** La fase in corso nell'istante [millis]; se cade in un buco, la più vicina. */
    fun stageAt(stages: List<SleepStage>, millis: Long): SleepStage? {
        if (stages.isEmpty()) return null
        stages.firstOrNull { millis >= it.startMillis && millis < it.endMillis }?.let { return it }
        return stages.minByOrNull { minOf(kotlin.math.abs(millis - it.startMillis), kotlin.math.abs(millis - it.endMillis)) }
    }
}
