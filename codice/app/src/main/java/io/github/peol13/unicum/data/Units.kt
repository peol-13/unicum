package io.github.peol13.unicum.data

import io.github.peol13.unicum.i18n.I18n
import io.github.peol13.unicum.i18n.tk
import io.github.peol13.unicum.i18n.tr
import java.util.Locale
import kotlin.math.roundToInt

/** Sistema di misura per peso, altezza e carichi: metrico (kg, cm) o imperiale (lb, ft/in). */
enum class UnitSystem(private val labelIt: String) {
    METRIC(tk("Metrico (kg, cm)")),
    IMPERIAL(tk("Imperiale (lb, ft)"));

    val label: String get() = tr(labelIt)

    companion object {
        /** Di base segue il paese del telefono: imperiale negli Stati Uniti, in Liberia e in Myanmar. */
        fun fromLocale(locale: Locale = Locale.getDefault()): UnitSystem =
            if (locale.country.uppercase() in setOf("US", "LR", "MM")) IMPERIAL else METRIC
    }
}

/**
 * Conversioni per mostrare e scrivere peso corporeo, altezza e carichi nel sistema scelto. Nel database e nelle
 * preferenze i valori restano sempre in kg e cm: si converte solo ai bordi (testi e campi). Le distanze delle corse
 * restano in km. Logica pura, con test.
 */
object Units {
    const val LB_PER_KG = 2.2046226218
    const val CM_PER_IN = 2.54

    val system: UnitSystem get() = UserSettings.unitSystem
    val imperial: Boolean get() = system == UnitSystem.IMPERIAL

    /** "kg" o "lb". */
    fun weightUnit(imperial: Boolean = this.imperial): String = if (imperial) "lb" else "kg"

    /** Da kg al numero mostrato (kg o lb). */
    fun weightValue(kg: Double, imperial: Boolean = this.imperial): Double = if (imperial) kg * LB_PER_KG else kg

    /** Dal numero scritto (kg o lb) ai kg. */
    fun toKg(value: Double, imperial: Boolean = this.imperial): Double = if (imperial) value / LB_PER_KG else value

    /** "72,5 kg" / "159.8 lb" (una cifra decimale; [decimals] = 0 per i valori interi). */
    fun weight(kg: Double, decimals: Int = 1, imperial: Boolean = this.imperial): String =
        String.format(I18n.locale, "%.${decimals}f", weightValue(kg, imperial)) + " " + weightUnit(imperial)

    /** Carico di un esercizio: "60 kg", "62,5 kg" oppure "132 lb", "137.5 lb" (mezze libbre). */
    fun load(kg: Double, imperial: Boolean = this.imperial): String {
        val v = if (imperial) Math.round(kg * LB_PER_KG * 2) / 2.0 else kg
        val text = if (kotlin.math.abs(v - Math.round(v)) < 1e-6) "${Math.round(v)}" else String.format(I18n.locale, "%.1f", v)
        return "$text ${weightUnit(imperial)}"
    }

    /** Peso corporeo intero da mostrare in un campo (kg → kg o lb arrotondati). */
    fun weightField(kg: Int, imperial: Boolean = this.imperial): Int = weightValue(kg.toDouble(), imperial).roundToInt()

    /** Dal campo (kg o lb interi) ai kg interi salvati. */
    fun weightFromField(value: Int, imperial: Boolean = this.imperial): Int = toKg(value.toDouble(), imperial).roundToInt()

    /** Altezza in un campo: cm, oppure pollici totali. */
    fun heightField(cm: Int, imperial: Boolean = this.imperial): Int = if (imperial) (cm / CM_PER_IN).roundToInt() else cm

    fun heightFromField(value: Int, imperial: Boolean = this.imperial): Int = if (imperial) (value * CM_PER_IN).roundToInt() else value

    /** "cm" o "in". */
    fun heightUnit(imperial: Boolean = this.imperial): String = if (imperial) "in" else "cm"

    /** "175 cm" oppure "5′9″". */
    fun height(cm: Int, imperial: Boolean = this.imperial): String {
        if (!imperial) return "$cm cm"
        val inches = (cm / CM_PER_IN).roundToInt()
        return "${inches / 12}′${inches % 12}″"
    }
}
