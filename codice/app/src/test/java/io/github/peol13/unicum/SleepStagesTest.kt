package io.github.peol13.unicum

import io.github.peol13.unicum.data.SleepStage
import io.github.peol13.unicum.data.SleepStageType
import io.github.peol13.unicum.data.SleepStages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStagesTest {

    private val min = 60_000L

    private fun st(type: SleepStageType, fromMin: Int, toMin: Int) =
        SleepStage(type, fromMin * min, toMin * min)

    @Test
    fun codifica_e_decodifica_sono_inverse() {
        val stages = listOf(
            st(SleepStageType.LEGGERO, 0, 30),
            st(SleepStageType.PROFONDO, 30, 90),
            st(SleepStageType.REM, 90, 120),
            st(SleepStageType.SVEGLIO, 120, 125)
        )
        assertEquals(stages, SleepStages.decode(SleepStages.encode(stages)))
    }

    @Test
    fun testo_vuoto_o_rovinato_non_da_errori() {
        assertTrue(SleepStages.decode(null).isEmpty())
        assertTrue(SleepStages.decode("").isEmpty())
        assertTrue(SleepStages.decode("pasticcio;X:1:2;L:abc:5;L:10:5").isEmpty())
    }

    @Test
    fun i_pezzi_validi_sopravvivono_a_quelli_rovinati() {
        val decoded = SleepStages.decode("L:0:600000;rotto;D:600000:1200000")
        assertEquals(2, decoded.size)
        assertEquals(SleepStageType.PROFONDO, decoded[1].type)
    }

    @Test
    fun fasi_adiacenti_dello_stesso_tipo_si_uniscono() {
        val merged = SleepStages.normalize(
            listOf(st(SleepStageType.LEGGERO, 0, 10), st(SleepStageType.LEGGERO, 10, 25), st(SleepStageType.REM, 25, 40)),
            0, 40 * min
        )
        assertEquals(2, merged.size)
        assertEquals(25, merged[0].minutes)
    }

    @Test
    fun le_fasi_si_ritagliano_dentro_la_sessione() {
        val out = SleepStages.normalize(listOf(st(SleepStageType.LEGGERO, -10, 20), st(SleepStageType.REM, 50, 90)), 0, 60 * min)
        assertEquals(0L, out[0].startMillis)
        assertEquals(60 * min, out[1].endMillis)
        assertEquals(20, out[0].minutes)
    }

    @Test
    fun le_fasi_fuori_dalla_sessione_spariscono() {
        assertTrue(SleepStages.normalize(listOf(st(SleepStageType.REM, 100, 120)), 0, 60 * min).isEmpty())
    }

    @Test
    fun fase_in_un_istante_e_fase_piu_vicina_nei_buchi() {
        val stages = listOf(st(SleepStageType.LEGGERO, 0, 30), st(SleepStageType.PROFONDO, 40, 80))
        assertEquals(SleepStageType.LEGGERO, SleepStages.stageAt(stages, 10 * min)?.type)
        assertEquals(SleepStageType.PROFONDO, SleepStages.stageAt(stages, 50 * min)?.type)
        // buco tra 30 e 40: 33 è più vicino alla prima, 38 alla seconda
        assertEquals(SleepStageType.LEGGERO, SleepStages.stageAt(stages, 33 * min)?.type)
        assertEquals(SleepStageType.PROFONDO, SleepStages.stageAt(stages, 38 * min)?.type)
        assertNull(SleepStages.stageAt(emptyList(), 0))
    }
}
