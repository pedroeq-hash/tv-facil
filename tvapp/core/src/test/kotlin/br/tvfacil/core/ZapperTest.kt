package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZapperTest {
    private val lista = listOf("a", "b", "c")

    @Test
    fun `proximo e anterior`() {
        assertEquals("b", Zapper.vizinho(lista, "a", +1))
        assertEquals("a", Zapper.vizinho(lista, "b", -1))
    }

    @Test
    fun `volta ao inicio nas pontas`() {
        assertEquals("a", Zapper.vizinho(lista, "c", +1))
        assertEquals("c", Zapper.vizinho(lista, "a", -1))
    }

    @Test
    fun `canal fora da lista vai para o primeiro, lista vazia nao tem vizinho`() {
        assertEquals("a", Zapper.vizinho(lista, "z", +1))
        assertNull(Zapper.vizinho(emptyList(), "a", +1))
    }
}
