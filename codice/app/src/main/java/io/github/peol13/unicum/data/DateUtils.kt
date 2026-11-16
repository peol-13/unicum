package io.github.peol13.unicum.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Funzioni sulle date (formato di archiviazione: ISO "2026-10-16"). Tutto in italiano. */
object DateUtils {

    private val ITA = Locale.ITALIAN
    private val fullDay = DateTimeFormatter.ofPattern("EEEE d MMMM", ITA)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM", ITA)
    private val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", ITA)
    private val dayOnly = DateTimeFormatter.ofPattern("d", ITA)

    fun today(): LocalDate = LocalDate.now()

    fun parse(iso: String): LocalDate = LocalDate.parse(iso)

    fun isToday(iso: String): Boolean = iso == today().toString()

    /** Le ultime [n] date che finiscono con [end] (incluso), dalla più vecchia alla più recente. */
    fun lastDays(n: Int, end: LocalDate = today()): List<String> =
        ((n - 1) downTo 0).map { end.minusDays(it.toLong()).toString() }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** I sette giorni da lunedì a domenica della settimana che inizia a [monday]. */
    fun weekDays(monday: LocalDate): List<LocalDate> = (0L..6L).map { monday.plusDays(it) }

    /** "Venerdì 16 Ottobre": data completa con le iniziali maiuscole. */
    fun fullLabel(date: LocalDate): String = capitalizeWords(date.format(fullDay))

    /** "Ven 16 Ott" */
    fun shortLabel(date: LocalDate): String = capitalizeWords(date.format(shortDay).replace(".", ""))

    /** "Oggi", "Ieri" oppure "Ven 16 Ott". */
    fun relativeLabel(date: LocalDate, today: LocalDate = today()): String = when (date) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> shortLabel(date)
    }

    /** "13 – 19 Ott" oppure "28 Set – 4 Ott" quando la settimana attraversa due mesi. */
    fun weekLabel(monday: LocalDate): String {
        val sunday = monday.plusDays(6)
        val start = if (monday.month == sunday.month) monday.format(dayOnly)
        else capitalizeWords(monday.format(dayAndMonth).replace(".", ""))
        val end = capitalizeWords(sunday.format(dayAndMonth).replace(".", ""))
        return "$start – $end"
    }

    /** Iniziale del giorno per i grafici: L M M G V S D. */
    fun weekdayInitial(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "L"
        DayOfWeek.TUESDAY -> "M"
        DayOfWeek.WEDNESDAY -> "M"
        DayOfWeek.THURSDAY -> "G"
        DayOfWeek.FRIDAY -> "V"
        DayOfWeek.SATURDAY -> "S"
        DayOfWeek.SUNDAY -> "D"
    }

    private fun capitalizeWords(s: String): String =
        s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { c -> c.uppercase() } }
}
