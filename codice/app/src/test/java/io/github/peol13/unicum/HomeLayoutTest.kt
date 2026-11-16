package io.github.peol13.unicum

import io.github.peol13.unicum.data.DashboardMode
import io.github.peol13.unicum.data.HomeCard
import io.github.peol13.unicum.data.HomeLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun `ogni modalita parte dal banner e finisce con il peso`() {
        DashboardMode.entries.forEach { mode ->
            val cards = HomeLayout.default(mode)
            assertEquals(HomeCard.BANNER, cards.first())
            assertEquals(HomeCard.PESO, cards.last())
            assertEquals(cards.size, cards.distinct().size)
        }
    }

    @Test
    fun `salvataggio e lettura`() {
        val cards = listOf(HomeCard.SONNO, HomeCard.CALORIE)
        assertEquals(cards, HomeLayout.decode(HomeLayout.encode(cards)))
    }

    @Test
    fun `nomi sconosciuti e doppioni si ignorano`() {
        assertEquals(listOf(HomeCard.PASTI, HomeCard.PESO), HomeLayout.decode("PASTI, VECCHIA_CARD,PESO,PASTI"))
        assertNull(HomeLayout.decode(""))
        assertNull(HomeLayout.decode(null))
    }

    @Test
    fun `spostare una card`() {
        val cards = listOf(HomeCard.BANNER, HomeCard.CALORIE, HomeCard.PASTI)
        assertEquals(listOf(HomeCard.CALORIE, HomeCard.PASTI, HomeCard.BANNER), HomeLayout.move(cards, 0, 2))
        assertEquals(listOf(HomeCard.PASTI, HomeCard.BANNER, HomeCard.CALORIE), HomeLayout.move(cards, 2, 0))
        assertEquals(cards, HomeLayout.move(cards, 0, 5))
    }

    @Test
    fun `card nascoste`() {
        val hidden = HomeLayout.hidden(HomeLayout.default(DashboardMode.NUTRIZIONE))
        assertTrue(HomeCard.SONNO in hidden)
        assertTrue(HomeCard.CALORIE !in hidden)
    }
}
