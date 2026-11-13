package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DiagnosticoTest {
    private fun canal(nome: String, grupo: String) = Canal(nome = nome, url = "https://exemplo.com/$nome", grupo = grupo)

    private val resultados = listOf(
        ResultadoTeste(canal("CNN", "Notícias Brasil"), tocou = true, msParaAbrir = 2_000),
        ResultadoTeste(canal("Band", "Notícias Brasil"), tocou = false, msParaAbrir = null),
        ResultadoTeste(canal("Cine", "Filmes"), tocou = true, msParaAbrir = 3_000),
    )

    @Test
    fun `resume total, porcentagem e tempos`() {
        val r = Diagnostico.relatorio(resultados)
        assertContains(r, "Canais testados: 3")
        assertContains(r, "Tocaram: 2 (67%)")
        assertContains(r, "média 2,5 s · mais lento 3,0 s")
    }

    @Test
    fun `conta por grupo na ordem da lista e lista os que falharam`() {
        val r = Diagnostico.relatorio(resultados)
        assertContains(r, "Notícias Brasil: 1 de 2\nFilmes: 1 de 1")
        assertContains(r, "Não tocaram:\n- Band (Notícias Brasil)")
    }

    @Test
    fun `lista vazia nao quebra`() {
        val r = Diagnostico.relatorio(emptyList())
        assertEquals("Canais testados: 0", r.lines().first())
        assertContains(r, "Tocaram: 0 (0%)")
    }
}
