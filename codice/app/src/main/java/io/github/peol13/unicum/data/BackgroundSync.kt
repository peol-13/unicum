package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n

import io.github.peol13.unicum.i18n.tr

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import io.github.peol13.unicum.widget.UnicumWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Aggiornamenti ad app chiusa: ogni circa 3 ore Android avvia [BackgroundJobService], che sincronizza Health Connect
 * (se il telefono lo permette), rilegge Notion, fa il backup automatico se è il momento, manda l'avviso sul recupero
 * e ridisegna il widget. Usa JobScheduler di Android (lo stesso meccanismo di WorkManager, senza altre librerie):
 * i lavori sopravvivono al riavvio del telefono e Android sceglie il momento giusto per la batteria.
 */
object BackgroundSync {
    private const val JOB_ID = 4711
    const val PERIOD_MS = 3 * 3_600_000L
    private const val PREFS = "background_sync"
    private const val TAG = "UnicumBackground"

    /** Programma il lavoro periodico (una sola volta: se c'è già con lo stesso intervallo non lo azzera). */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID)?.intervalMillis == PERIOD_MS) return
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, BackgroundJobService::class.java))
            .setPeriodic(PERIOD_MS)
            .setRequiresBatteryNotLow(true)
            .setPersisted(true)
            .build()
        scheduler.schedule(info)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** "oggi 14:05 · sincronizzato" oppure null se non è mai partito. */
    fun lastRunText(context: Context): String? {
        val p = prefs(context)
        val at = p.getLong("last_run", 0L).takeIf { it > 0 } ?: return null
        val day = if (DateUtils.isToday(java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())) tr("oggi")
        else SimpleDateFormat(tr("d MMM"), I18n.locale).format(Date(at))
        return "$day ${SimpleDateFormat("HH:mm", I18n.locale).format(Date(at))} · ${p.getString("last_note", "")}"
    }

    /** Il lavoro vero: ogni passo è indipendente, uno che fallisce non ferma gli altri. */
    suspend fun run(context: Context) {
        val app = context.applicationContext
        UserSettings.init(app)
        NotionPlan.init(app)
        Shoes.init(app)
        AutoBackup.init(app)
        val notes = mutableListOf<String>()

        step("Health Connect") {
            val status = HealthConnectSource.status(app)
            if (status is HealthConnectSource.Status.Available && status.anyGranted) {
                when {
                    !status.backgroundSupported -> notes += tr("Health Connect non legge ad app chiusa su questo telefono")
                    !status.backgroundGranted -> notes += tr("manca il permesso per leggere ad app chiusa")
                    else -> {
                        val result = HealthConnectSource.sync(app, days = 7)
                        if (result.error == null) UserSettings.markSynced()
                        UnicumRepository.resolvePendingBackup(app)
                        notes += if (result.error == null) tr("sincronizzato") else tr("sincronizzazione non riuscita")
                    }
                }
            }
        }
        step("Notion") { NotionPlan.sync(app, force = false) }
        step("Backup automatico") {
            when (val r = AutoBackup.run(app)) {
                is AutoBackup.Outcome.Saved -> notes += tr("backup fatto")
                is AutoBackup.Outcome.Failed -> notes += tr("backup non riuscito")
                is AutoBackup.Outcome.Held -> notes += tr("backup fermo, serve conferma")
                else -> Unit
            }
        }
        step("Avviso recupero") {
            val today = DateUtils.today()
            val health = UnicumRepository.loadHealth(app, 14, today.toString())
            RecoveryAlerts.fromHealth(health, today)?.let { RecoveryAlertNotifier.notifyOnce(app, it) }
        }
        step("Riepilogo settimana") { WeeklySummary.notifyIfDue(app) }
        step("Widget") { UnicumWidget.refresh(app) }

        prefs(app).edit().putLong("last_run", System.currentTimeMillis())
            .putString("last_note", notes.joinToString(", ").ifEmpty { "nessun aggiornamento da fare" }).apply()
    }

    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$name non riuscito", e)
        }
    }
}

/** Android avvia questo servizio all'orario del lavoro periodico; il lavoro gira fuori dal thread principale. */
class BackgroundJobService : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                BackgroundSync.run(applicationContext)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("UnicumBackground", "Lavoro in background fallito", e)
                false
            }
            // false = riuscito; true = Android lo ripianifica con una pausa
            jobFinished(params, !ok)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }
}
