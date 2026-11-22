package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.tk
import io.github.peol13.unicum.i18n.tr

/**
 * Lo sport di una seduta del programma o di un allenamento registrato. [key] è il valore salvato (in italiano,
 * come gli altri valori nel database e nel programma) e il nome del tipo di allenamento letto da Health Connect;
 * si traduce solo quando si mostra ([label]).
 *
 * Gli sport si raggruppano in [Family]: una seduta del programma si abbina a un allenamento registrato della
 * stessa famiglia nello stesso giorno (una seduta di pesi può essere registrata dall'orologio come "Corpo libero"
 * e viceversa). Per aggiungere uno sport: una voce qui, la sua famiglia e, se serve, il tipo di Health Connect in
 * [HealthConnectSource].
 */
enum class Sport(val key: String, val family: Family) {
    RUN(tk("Corsa"), Family.RUN),
    WEIGHTS(tk("Pesi"), Family.STRENGTH),
    BODYWEIGHT(tk("Corpo libero"), Family.STRENGTH);

    /** RUN = corsa (km, ritmo, ripetute); STRENGTH = esercizi con serie e ripetizioni. */
    enum class Family { RUN, STRENGTH }

    val label: String get() = tr(key)
    val isStrength: Boolean get() = family == Family.STRENGTH

    companion object {
        /** Il tipo di allenamento che Health Connect dà alle sessioni di pesi (vedi [HealthConnectSource]). */
        const val GYM_WORKOUT = "Palestra"

        private val weightsWords = Regex(
            """\b(pesi|palestra|forza|gym|weights?|weightlifting|strength|bilanciere|manubri|panca|squat|stacchi|ipertrofia)\b""",
            RegexOption.IGNORE_CASE
        )
        /** Detto esplicitamente: vince anche se ci sono esercizi da palestra. */
        private val bodyweightWords = Regex(
            """\b(corpo libero|calisthenics?|bodyweight|a carico naturale)\b""",
            RegexOption.IGNORE_CASE
        )
        /** Esercizi tipici del corpo libero: contano solo se non ci sono parole dei pesi (le trazioni si fanno anche in palestra). */
        private val bodyweightExercises = Regex(
            """\b(circuito|piegamenti|trazioni|push.?ups?|pull.?ups?|burpees?|dips)\b""",
            RegexOption.IGNORE_CASE
        )

        private val runWords = Regex(
            """\b(corsa|corri\w*|run|running|jog\w*|km|ripetut\w*|salit\w*|fartlek|lungo|progressiv\w*)\b""",
            RegexOption.IGNORE_CASE
        )

        /** Lo sport scritto per esteso (proprietà "Sport" di Notion, valore salvato): null se non si riconosce. */
        fun of(name: String?): Sport? {
            val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            entries.firstOrNull { it.key.equals(n, ignoreCase = true) }?.let { return it }
            return when {
                Regex("""^(run|running|corri\w*|jog\w*)$""", RegexOption.IGNORE_CASE).matches(n) -> RUN
                bodyweightWords.containsMatchIn(n) -> BODYWEIGHT
                weightsWords.containsMatchIn(n) -> WEIGHTS
                bodyweightExercises.containsMatchIn(n) -> BODYWEIGHT
                else -> null
            }
        }

        /**
         * Lo sport di una seduta senza "Sport" scritto: dal tipo o dal nome ("Forza gambe", "Corpo libero").
         * Se non ci sono parole della forza è una corsa, come tutte le sedute delle versioni precedenti.
         */
        fun infer(vararg texts: String?): Sport {
            val all = texts.filterNotNull().joinToString(" ")
            return when {
                // "Salite per la forza", "Lungo + core": parole della corsa = resta una corsa
                runWords.containsMatchIn(all) -> RUN
                bodyweightWords.containsMatchIn(all) -> BODYWEIGHT
                weightsWords.containsMatchIn(all) -> WEIGHTS
                bodyweightExercises.containsMatchIn(all) -> BODYWEIGHT
                else -> RUN
            }
        }

        /** Lo sport di un allenamento registrato (tipo di [WorkoutDetail]): null per quelli che la sezione non segue. */
        fun ofWorkout(type: String): Sport? = when (type) {
            RUN.key -> RUN
            GYM_WORKOUT, WEIGHTS.key -> WEIGHTS
            BODYWEIGHT.key -> BODYWEIGHT
            else -> null
        }
    }
}
