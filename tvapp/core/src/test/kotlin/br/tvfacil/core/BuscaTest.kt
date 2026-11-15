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
    fun `indice pronto da o mesmo resultado e corta no maximo`() {
        val lista = canais("Rede Globo", "GloboNews", "Globoplay Novelas")
        val indice = Busca.indice(lista)
        assertEquals(nomes(Busca.filtrar(lista, "globo")), nomes(Busca.filtrar(indice, "globo")))
        assertEquals(listOf("GloboNews", "Globoplay Novelas"), nomes(Busca.filtrar(indice, "globo", max = 2)))
    }

    @Test
    fun `termo vazio nao mostra nada`() {
        assertEquals(emptyList(), Busca.filtrar(canais("Record"), " "))
    }

    @Test
    fun `busca global - canais, series e filmes, com cota de cada tipo`() {
        val canais = Busca.indice(canais("Rede Globo", "Record"))
        val series = Busca.indice(listOf(Canal("Globo Repórter", "1", tipo = Tipo.SERIE), Canal("Dark", "2", tipo = Tipo.SERIE)))
        val filmes = Busca.indice((1..50).map { Canal("Globo $it", "https://f.com/$it.mp4", tipo = Tipo.FILME) })
        val r = Busca.global(canais, series, filmes, "globo", cota = 3)
        assertEquals(listOf("Rede Globo", "Globo Repórter", "Globo 1", "Globo 2", "Globo 3"), nomes(r))
    }
}
