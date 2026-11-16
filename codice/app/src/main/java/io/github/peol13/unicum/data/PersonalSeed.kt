package io.github.peol13.unicum.data

import android.content.Context
import io.github.peol13.unicum.BuildConfig

/**
 * Valori personali di chi compila l'app, letti da `codice/personal.properties` (file che **non** va nel
 * repository; vedi `personal.properties.example`). Al primo avvio di una build che li contiene vengono scritti
 * nelle preferenze come se l'utente li avesse scelti a mano, poi restano lì anche installando build senza.
 * Una preferenza già presente non viene mai sovrascritta. Senza il file tutto parte in automatico.
 */
object PersonalSeed {
    private const val FLAG = "personal_seed_v1"

    fun apply(context: Context) {
        val app = context.applicationContext
        val settings = app.getSharedPreferences("unicum_settings", Context.MODE_PRIVATE)
        if (settings.getBoolean(FLAG, false)) return
        // Build senza valori personali: niente da scrivere e il segno resta libero per una build che li ha
        if (listOf(BuildConfig.PERSONAL_HRV_FROM, BuildConfig.PERSONAL_NOTION_PLAN, BuildConfig.PERSONAL_NOTION_LOG,
                BuildConfig.PERSONAL_RUN_GOAL_SHORT).all { it.isBlank() }) return

        BuildConfig.PERSONAL_HRV_FROM.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }?.let { d ->
            if (!settings.contains("hrv_from_manual")) settings.edit().putString("hrv_from_manual", d).commit()
        }

        val notion = app.getSharedPreferences("notion", Context.MODE_PRIVATE)
        NotionPlan.parseDatabaseId(BuildConfig.PERSONAL_NOTION_PLAN)?.let { id ->
            if (!notion.contains("database")) notion.edit().putString("database", id).commit()
        }
        NotionPlan.parseDatabaseId(BuildConfig.PERSONAL_NOTION_LOG)?.let { id ->
            if (!notion.contains("log_database")) notion.edit().putString("log_database", id).commit()
        }

        // Obiettivo a breve termine: "km|ritmo|scadenza", per esempio "10|5:45|2027-06-30"
        val goal = BuildConfig.PERSONAL_RUN_GOAL_SHORT.split('|').takeIf { it.size == 3 }?.let { (km, pace, deadline) ->
            val k = RunGoal.parseKm(km)
            val p = RunGoal.parsePace(pace)
            if (k != null && p != null && runCatching { java.time.LocalDate.parse(deadline.trim()) }.isSuccess) RunGoal(k, p, deadline.trim()) else null
        }
        if (goal != null) {
            val goals = app.getSharedPreferences("run_goals", Context.MODE_PRIVATE)
            if (!goals.contains("data")) {
                goals.edit().putString("data", kotlinx.serialization.json.Json.encodeToString(RunGoals.serializer(), RunGoals(short = goal))).commit()
            }
        }

        settings.edit().putBoolean(FLAG, true).commit()
    }
}
