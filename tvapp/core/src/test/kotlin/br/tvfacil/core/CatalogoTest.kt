package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogoTest {
    private fun canal(nome: String, grupo: String) = Canal(nome = nome, url = "https://exemplo.com/$nome", grupo = grupo)

    private val cnn = canal("CNN", "Notícias")
    private val cine = canal("Cine", "Filmes")
    private val band = canal("Band", "Notícias")
    private val canais = listOf(cnn, cine, band)

    @Test
    fun `grupos na ordem em que aparecem, sem favoritos`() {
        val grupos = Catalogo.grupos(canais, favoritos = emptySet())
        assertEquals(listOf("Notícias", "Filmes"), grupos.map { it.nome })
        assertEquals(listOf(cnn, band), grupos[0].canais)
    }

    @Test
    fun `favoritos vem primeiro`() {
        val grupos = Catalogo.grupos(canais, favoritos = setOf(band.url))
        assertEquals(listOf(Catalogo.FAVORITOS, "Notícias", "Filmes"), grupos.map { it.nome })
        assertEquals(listOf(band), grupos[0].canais)
    }

    @Test
    fun `canal sem grupo vai para Outros`() {
        assertEquals("Outros", Catalogo.grupos(listOf(canal("X", "")), emptySet()).single().nome)
    }
}
