package io.github.peol13.unicum.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlin.math.roundToInt

/**
 * Client minimale di Open Food Facts (database alimentare aperto, licenza ODbL): prodotti confezionati
 * con marca e valori per 100 g: ricerca per nome e per codice a barre.
 *
 * Regole d'uso del servizio: identificarsi con uno User-Agent, non fare troppe richieste (qui una ricerca
 * per volta e solo quando l'utente la chiede) e citare la fonte nell'interfaccia.
 */
object OpenFoodFacts {
    private const val SEARCH_URL = "https://search.openfoodfacts.org/search"
    private const val PRODUCT_URL = "https://world.openfoodfacts.org/api/v2/product"
    private val USER_AGENT = AppInfo.USER_AGENT
    const val SOURCE = "openfoodfacts"
    const val ID_PREFIX = "off:"
    const val CATEGORY = "Marche"
    private const val FIELDS = "code,product_name,brands,nutriments"

    /** Cerca prodotti per nome o marca. Non solleva eccezioni: l'errore è dentro il [Result]. */
    suspend fun search(query: String, limit: Int = 25): Result<List<Food>> = request(
        "$SEARCH_URL?q=${URLEncoder.encode(query.trim(), "UTF-8")}&page_size=$limit&fields=$FIELDS&langs=it"
    ) { body -> body?.let { parseSearch(it) } ?: emptyList() }

    /** Cerca un prodotto dal codice a barre. Successo con `null` = codice sconosciuto o senza valori nutrizionali. */
    suspend fun byBarcode(code: String): Result<Food?> = request(
        "$PRODUCT_URL/${URLEncoder.encode(code.trim(), "UTF-8")}.json?fields=$FIELDS"
    ) { body -> body?.let { parseProduct(it, code) } }

    /**
     * GET con User-Agent e timeout; [parse] riceve il corpo della risposta oppure null se il server
     * risponde 404 (prodotto inesistente). Errori di rete e risposte diverse da 200 finiscono nel [Result].
     */
    private suspend fun <T> request(url: String, parse: (String?) -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                when (val status = conn.responseCode) {
                    200 -> Result.success(parse(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }))
                    404 -> Result.success(parse(null))
                    else -> throw IOException("HTTP $status")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Risposta di `/api/v2/product/<codice>`: il prodotto sta in "product". */
    fun parseProduct(json: String, code: String): Food? {
        val root = JSONObject(json)
        if (root.optInt("status", 1) == 0) return null
        val product = root.optJSONObject("product") ?: return null
        if (!product.has("code")) product.put("code", code)
        return toFood(product)
    }

    /** Messaggio comprensibile per l'utente. */
    fun friendlyError(e: Throwable): String = when (e) {
        is UnknownHostException -> "Nessuna connessione a internet."
        is SocketTimeoutException -> "Open Food Facts non risponde: riprova tra poco."
        else -> "Ricerca online non riuscita (${e.message ?: e.javaClass.simpleName})."
    }

    /**
     * Legge la risposta della ricerca. I prodotti senza nome o senza valori nutrizionali credibili
     * vengono scartati (nel database ce ne sono molti incompleti) e i doppioni uniti.
     */
    fun parseSearch(json: String): List<Food> {
        val hits = JSONObject(json).optJSONArray("hits") ?: return emptyList()
        val out = mutableListOf<Food>()
        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i) ?: continue
            toFood(hit)?.let { out += it }
        }
        return out.distinctBy { it.name.lowercase() }
    }

    private fun toFood(hit: JSONObject): Food? {
        val code = hit.optString("code").takeIf { it.isNotBlank() } ?: return null
        val product = hit.optString("product_name").trim().takeIf { it.isNotEmpty() } ?: return null
        val n = hit.optJSONObject("nutriments") ?: return null

        fun value(key: String): Double? = n.optDouble(key, Double.NaN).takeIf { !it.isNaN() }

        val protein = value("proteins_100g")
        val carbs = value("carbohydrates_100g")
        val fats = value("fat_100g")
        if (protein == null && carbs == null && fats == null) return null

        // Calorie: dal campo kcal, altrimenti convertendo i kJ
        val kcal = value("energy-kcal_100g") ?: value("energy_100g")?.let { it / 4.184 } ?: return null
        val p = protein ?: 0.0
        val c = carbs ?: 0.0
        val f = fats ?: 0.0
        if (kcal < 0 || kcal > 950 || p < 0 || c < 0 || f < 0 || p + c + f > 105) return null

        // Valori facoltativi: se mancano o sono impossibili restano vuoti
        fun optional(key: String) = value(key)?.takeIf { it in 0.0..100.0 }?.toFloat()
        val salt = optional("salt_100g") ?: value("sodium_100g")?.let { it * 2.5 }?.takeIf { it in 0.0..100.0 }?.toFloat()

        val brand = brandOf(hit)
        return Food(
            name = displayName(product, brand),
            caloriesPer100g = kcal.roundToInt(),
            proteinPer100g = p.toFloat(),
            carbsPer100g = c.toFloat(),
            fatsPer100g = f.toFloat(),
            category = CATEGORY,
            brand = brand,
            barcode = code,
            fiberPer100g = optional("fiber_100g"),
            sugarsPer100g = optional("sugars_100g"),
            saltPer100g = salt
        )
    }

    /** La prima marca: nella ricerca arriva come lista, nel dettaglio prodotto come testo separato da virgole. */
    private fun brandOf(hit: JSONObject): String? {
        val arr = hit.optJSONArray("brands")
        val first = if (arr != null && arr.length() > 0) arr.optString(0)
        else hit.optString("brands").split(",").firstOrNull()
        return first?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** "Nutella (Ferrero)": il nome contiene la marca così resta unico e riconoscibile nell'elenco. */
    fun displayName(product: String, brand: String?): String {
        val name = product.replaceFirstChar { it.uppercase() }
        return if (brand.isNullOrBlank() || name.contains(brand, ignoreCase = true)) name else "$name ($brand)"
    }
}
