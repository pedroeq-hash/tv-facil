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

    @Test
    fun `sigla - iniciais das duas primeiras palavras`() {
        assertEquals("RN", Catalogo.sigla("Record News"))
        assertEquals("JP", Catalogo.sigla("Jovem Pan News"))
        assertEquals("TC", Catalogo.sigla("TV Câmara"))
    }

    @Test
    fun `sigla - uma palavra vira as duas primeiras letras`() {
        assertEquals("SB", Catalogo.sigla("SBT"))
        assertEquals("GL", Catalogo.sigla("globonews"))
    }

    @Test
    fun `sigla - ignora qualidade entre parenteses ou colchetes e simbolos`() {
        assertEquals("XS", Catalogo.sigla("XITE Siempre Latino (1080p)"))
        assertEquals("CB", Catalogo.sigla("Canal Brasil [Geo-blocked]"))
        assertEquals("T3", Catalogo.sigla("TV - 3"))
    }

    @Test
    fun `sigla - nome vazio ou so simbolos vira TV`() {
        assertEquals("TV", Catalogo.sigla(""))
        assertEquals("TV", Catalogo.sigla("--- (720p)"))
    }
}
