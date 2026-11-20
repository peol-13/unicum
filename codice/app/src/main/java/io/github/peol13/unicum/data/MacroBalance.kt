package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import io.github.peol13.unicum.i18n.tk

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Macro(private val labelIt: String, val kcalPerGram: Int) {
    PROTEINE(tk("Proteine"), 4),
    CARBOIDRATI(tk("Carboidrati"), 4),
    GRASSI(tk("Grassi"), 9);

    val label: String get() = tr(labelIt)
}

/** Un macronutriente: quanto pesa davvero sulle calorie mangiate e quanto dovrebbe pesare. */
data class MacroShare(
    val macro: Macro,
    val eatenGrams: Float,
    val goalGrams: Int,
    /** Quota percentuale delle calorie mangiate che viene da questo macro; null se non si è mangiato nulla. */
    val effectivePct: Int?,
    /** Quota percentuale consigliata (dal Profilo). */
    val goalPct: Int
) {
    /** Differenza in punti percentuali tra effettivo e consigliato (negativo = ne hai mangiato meno del dovuto). */
    val deltaPct: Int? get() = effectivePct?.let { it - goalPct }
    val missingGrams: Int get() = (goalGrams - eatenGrams).roundToInt().coerceAtLeast(0)
}

/**
 * Ripartizione dei macro della giornata rispetto a quella consigliata, e cosa spostare nel prossimo pasto.
 * Le quote si calcolano sulle calorie dei macro (4 kcal/g proteine e carboidrati, 9 kcal/g grassi).
 */
object MacroBalance {

    /** Sotto questa differenza (in punti percentuali) la ripartizione è considerata in linea. */
    const val TOLERANCE_PCT = 3

    fun shares(
        protein: Float, carbs: Float, fats: Float,
        goalPct: Triple<Int, Int, Int>,
        goalGrams: Triple<Int, Int, Int>
    ): List<MacroShare> {
        val grams = listOf(protein, carbs, fats)
        val kcal = Macro.entries.mapIndexed { i, m -> grams[i] * m.kcalPerGram }
        val pcts = percentages(kcal)
        val goalPcts = listOf(goalPct.first, goalPct.second, goalPct.third)
        val goalG = listOf(goalGrams.first, goalGrams.second, goalGrams.third)
        return Macro.entries.mapIndexed { i, m ->
            MacroShare(m, grams[i], goalG[i], pcts?.get(i), goalPcts[i])
        }
    }

    /** Percentuali intere che sommano esattamente 100 (metodo del resto maggiore); null se il totale è zero. */
    fun percentages(values: List<Float>): List<Int>? {
        val total = values.sum()
        if (total <= 0f) return null
        val exact = values.map { it / total * 100f }
        val floors = exact.map { it.toInt() }.toMutableList()
        var missing = 100 - floors.sum()
        exact.withIndex().sortedByDescending { it.value - floors[it.index] }.forEach {
            if (missing > 0) {
                floors[it.index] += 1
                missing--
            }
        }
        return floors
    }

    /**
     * Frase per il prossimo pasto: cosa aumentare e cosa diminuire.
     * [remainingKcal] sono le calorie ancora disponibili oggi (l'obiettivo è fisso).
     */
    fun nextMealHint(shares: List<MacroShare>, remainingKcal: Int): String {
        if (shares.all { it.effectivePct == null }) {
            return tr("Registra il primo pasto: ti dirò come bilanciare i macro.")
        }
        if (remainingKcal <= 0) {
            return tr("Obiettivo calorico raggiunto: per il resto della giornata scegli cibi leggeri e poche calorie.")
        }
        val more = shares.filter { (it.deltaPct ?: 0) <= -TOLERANCE_PCT }.sortedBy { it.deltaPct }
        val less = shares.filter { (it.deltaPct ?: 0) >= TOLERANCE_PCT }.sortedByDescending { it.deltaPct }
        if (more.isEmpty() && less.isEmpty()) {
            return tr("La ripartizione è in linea con quella consigliata: nel prossimo pasto mantieni lo stesso equilibrio.")
        }
        val parts = mutableListOf<String>()
        if (more.isNotEmpty()) {
            parts += tr("più %s", join(more.map { name(it) + gramsLeft(it) }))
        }
        if (less.isNotEmpty()) {
            parts += tr("meno %s", join(less.map { name(it) }))
        }
        return tr("Nel prossimo pasto: %s.", parts.joinToString(", "))
    }

    private fun name(s: MacroShare) = s.macro.label.lowercase(I18n.locale)

    private fun gramsLeft(s: MacroShare): String =
        if (s.missingGrams > 0) " " + tr("(ne mancano %d g)", s.missingGrams) else ""

    private fun join(items: List<String>): String = when (items.size) {
        1 -> items[0]
        2 -> items[0] + " " + tr("e") + " " + items[1]
        else -> items.dropLast(1).joinToString(", ") + " e " + items.last()
    }

    /** Scostamento massimo dal consigliato, utile per i test e per evidenziare squilibri forti. */
    fun maxDeviation(shares: List<MacroShare>): Int = shares.maxOfOrNull { abs(it.deltaPct ?: 0) } ?: 0
}
