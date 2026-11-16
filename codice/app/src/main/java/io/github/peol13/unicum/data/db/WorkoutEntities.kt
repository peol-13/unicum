package io.github.peol13.unicum.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dettaglio di un allenamento importato (serie di battito, velocità, cadenza, percorso): il
 * [io.github.peol13.unicum.data.WorkoutDetail] in JSON. Una riga per sessione di Health Connect.
 */
@Entity(tableName = "workout_details", indices = [Index("date")])
data class WorkoutDetailEntity(
    @PrimaryKey val externalId: String,
    val source: String,
    val date: String,
    val type: String,
    val startMillis: Long,
    /** true per le sessioni riconosciute in automatico (camminate): restano in Attività, non in Allenamenti. */
    val automatic: Boolean,
    /** Lo stesso dettaglio senza le serie: basta per elenchi e grafici dello storico. */
    val summary: String,
    val json: String
)

/**
 * Sforzo percepito, tipo di seduta e nota scritti dall'utente su un allenamento. Tabella a parte perché
 * gli allenamenti importati vengono riscritti a ogni sincronizzazione. [key] = id esterno della sessione.
 */
@Entity(tableName = "workout_feedback")
data class WorkoutFeedbackEntity(
    @PrimaryKey val key: String,
    val rpe: Int?,
    val kind: String?,
    val note: String?,
    /** Correzione a mano (v7): distanza in metri e tempo in movimento in secondi. */
    val distanceM: Double? = null,
    val movingSeconds: Int? = null,
    /** FC del solo tratto corso, ricavata con la correzione (v8). */
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    /** Struttura delle ripetute scritta dall'utente, [io.github.peol13.unicum.data.IntervalPlan] in JSON (v9). */
    val intervals: String? = null
)
