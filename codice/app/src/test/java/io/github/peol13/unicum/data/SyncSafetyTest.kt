package io.github.peol13.unicum.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Una lettura riuscita a metà non rovina i dati già salvati; modificare i grammi non sposta le kcal. */
class SyncSafetyTest {

    private fun detail(
        hr: List<WorkoutSample> = emptyList(),
        route: List<RoutePoint> = emptyList(),
        routeStatus: RouteStatus = RouteStatus.NONE,
        distanceM: Double? = null,
        kcal: Double? = null,
        modifiedMillis: Long? = null
    ) = WorkoutDetail(
        externalId = "a", source = "com.sec.android.app.shealth", date = "2026-11-05", type = "Corsa", exerciseType = 56,
        startMillis = 0, endMillis = 1_800_000, movingSeconds = 1800,
        hr = hr, route = route, routeStatus = routeStatus, distanceM = distanceM, kcal = kcal, modifiedMillis = modifiedMillis
    )

    @Test
    fun `una lettura interrotta non cancella battito, distanza e percorso gia salvati`() {
        val old = detail(
            hr = listOf(WorkoutSample(0, 120f), WorkoutSample(1, 121f)),
            route = listOf(RoutePoint(0, 45.0, 9.0), RoutePoint(5, 45.001, 9.0)), routeStatus = RouteStatus.DATA,
            distanceM = 4000.0, kcal = 300.0, modifiedMillis = 10
        )
        val broken = detail(routeStatus = RouteStatus.CONSENT)
        val kept = UnicumRepository.keepKnown(broken, old)
        assertEquals(old.hr, kept.hr)
        assertEquals(old.route, kept.route)
        assertEquals(RouteStatus.DATA, kept.routeStatus)
        assertEquals(4000.0, kept.distanceM!!, 0.0)
        assertEquals(300.0, kept.kcal!!, 0.0)
        // Resta da rileggere: la versione letta per intero non viene ricordata
        assertNull(kept.modifiedMillis)
    }

    @Test
    fun `i dati nuovi vincono su quelli vecchi`() {
        val old = detail(hr = listOf(WorkoutSample(0, 120f)), distanceM = 4000.0)
        val new = detail(hr = listOf(WorkoutSample(0, 130f), WorkoutSample(1, 131f)), distanceM = 4100.0, modifiedMillis = 20)
        val kept = UnicumRepository.keepKnown(new, old)
        assertEquals(new.hr, kept.hr)
        assertEquals(4100.0, kept.distanceM!!, 0.0)
        assertEquals(20L, kept.modifiedMillis)
        assertEquals(new, UnicumRepository.keepKnown(new, null))
    }

    @Test
    fun `cambiare i grammi e tornare indietro riporta le stesse kcal`() {
        val food = Food("Prova", caloriesPer100g = 52, proteinPer100g = 0.3f, carbsPer100g = 14f, fatsPer100g = 0.2f)
        val meal = food.portion(100, "Pranzo", 780).copy(id = 7)
        val back = meal.withGrams(30, food).withGrams(100, food)
        assertEquals(52, back.calories)
        assertEquals(7L, back.id)
        assertEquals("Pranzo", back.mealType)
        assertEquals(780, back.minuteOfDay)
        // Senza l'alimento (o se la voce non viene da lì) resta la proporzione sui valori registrati
        assertEquals(16, meal.withGrams(30).calories)
        val edited = meal.copy(calories = 80)
        assertEquals(24, edited.withGrams(30, food).calories)
    }
}
