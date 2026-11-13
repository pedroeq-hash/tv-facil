package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BuscaTest {
    private fun canais(vararg nomes: String) = nomes.map { Canal(nome = it, url = "https://x.com/$it") }
    private fun nomes(lista: List<Canal>) = lista.map { it.nome }

    @Test
    fun `ignora acento e maiusculas`() {
        assertEquals(listOf("TV Câmara"), nomes(Busca.filtrar(canais("TV Câmara", "Record"), "CAMARA")))
    }

    @Test
    fun `ignora espacos - a voz fala separado o que o nome escreve junto`() {
        val lista = canais("GloboNews", "Record News")
        assertEquals(listOf("GloboNews"), nomes(Busca.filtrar(lista, "globo news")))
        assertEquals(listOf("GloboNews"), nomes(Busca.filtrar(lista, "glo")))
    }

    @Test
    fun `nomes que comecam com o termo vem primeiro`() {
        assertEquals(listOf("GloboNews", "Rede Globo"), nomes(Busca.filtrar(canais("Rede Globo", "GloboNews"), "globo")))
    }

    @Test
    fun `termo vazio nao mostra nada`() {
        assertEquals(emptyList(), Busca.filtrar(canais("Record"), " "))
    }
}
