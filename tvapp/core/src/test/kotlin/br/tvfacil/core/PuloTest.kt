package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class PuloTest {
    private val min = 60_000L
    private val filme = 120 * min // 2 h

    @Test
    fun `segurando mais tempo, anda mais rapido`() {
        assertEquals(10 * min + 30_000, Pulo.alvo(10 * min, filme, frente = true, seguradoMs = 1_000)) // 30 s por segundo
        assertEquals(10 * min + 3 * min, Pulo.alvo(10 * min, filme, frente = true, seguradoMs = 3_000)) // 1 min + 2 min
        assertEquals(10 * min + 32 * min, Pulo.alvo(10 * min, filme, frente = true, seguradoMs = 10_000)) // 1 + 6 + 25 min
    }

    @Test
    fun `para tras tambem`() {
        assertEquals(10 * min - 30_000, Pulo.alvo(10 * min, filme, frente = false, seguradoMs = 1_000))
    }

    @Test
    fun `nao passa do comeco nem do fim`() {
        assertEquals(0, Pulo.alvo(min, filme, frente = false, seguradoMs = 10_000))
        assertEquals(filme - 5_000, Pulo.alvo(110 * min, filme, frente = true, seguradoMs = 10_000))
    }

    @Test
    fun `duracao desconhecida nao limita para a frente`() {
        assertEquals(min + 30_000, Pulo.alvo(min, 0, frente = true, seguradoMs = 1_000))
    }
}
