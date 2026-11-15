package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentesTest {
    @Test
    fun `a ultima vista vai para a frente, sem repetir`() {
        assertEquals(listOf("b", "a", "c"), Recentes.usar(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("d", "a"), Recentes.usar(listOf("a"), "d"))
    }

    @Test
    fun `guarda no maximo o limite`() {
        assertEquals(listOf("novo", "1", "2"), Recentes.usar(listOf("1", "2", "3"), "novo", max = 3))
    }

    @Test
    fun `tirar remove so aquela`() {
        assertEquals(listOf("a", "c"), Recentes.tirar(listOf("a", "b", "c"), "b"))
        assertEquals(listOf("a"), Recentes.tirar(listOf("a"), "x"))
    }
}
