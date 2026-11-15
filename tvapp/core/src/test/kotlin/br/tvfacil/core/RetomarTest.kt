package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RetomarTest {
    private val hora = 60 * 60_000L

    @Test
    fun `continua de onde parou so depois de 1 minuto`() {
        assertEquals(0, Retomar.inicio(59_999, hora))
        assertEquals(60_000, Retomar.inicio(60_000, hora))
    }

    @Test
    fun `perto do fim conta como visto e recomeca`() {
        assertEquals(hora * 94 / 100, Retomar.inicio(hora * 94 / 100, hora))
        assertEquals(0, Retomar.inicio(hora * 95 / 100, hora))
    }

    @Test
    fun `sem duracao conhecida, vale so o minuto`() {
        assertEquals(120_000, Retomar.inicio(120_000, 0))
    }
}
