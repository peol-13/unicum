package io.github.peol13.unicum.data

import io.github.peol13.unicum.BuildConfig

/** Come l'app si presenta ai servizi esterni (Open Food Facts, mappe di OpenStreetMap). */
object AppInfo {
    /** Pagina pubblica del progetto: i servizi la usano per sapere chi fa le richieste. */
    const val PROJECT_URL = "https://github.com/peol-13/unicum"

    /** User-Agent di tutte le richieste di rete: nome, versione e pagina del progetto, come chiedono le loro regole. */
    val USER_AGENT = "UNICUM/${BuildConfig.VERSION_NAME} (Android; +$PROJECT_URL)"
}
