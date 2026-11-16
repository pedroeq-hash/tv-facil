package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EscondidasTest {
    @Test
    fun `sem arquivo, nada escondido`() {
        assertEquals(emptyList(), Escondidas.ler(null))
    }

    @Test
    fun `compara sem maiusculas nem acento`() {
        val esconde = Escondidas.filtro(listOf("Séries Antigas", "Adultos"))
        assertTrue(esconde("SERIES ANTIGAS"))
        assertTrue(esconde("ADULTOS"))
        assertFalse(esconde("Séries")) // so a categoria inteira
        assertFalse(esconde("Comédia"))
    }

    @Test
    fun `arquivo vazio = nada escondido (o usuario mostrou tudo)`() {
        assertEquals(emptyList(), Escondidas.ler(""))
    }

    @Test
    fun `nome vem da pagina - limpo, sem repetir, sem linha vazia, e volta igual`() {
        val lidas = Escondidas.ler("  Drama \n\nDRAMA\nTerror\u0007\n")
        assertEquals(listOf("Drama", "Terror"), lidas)
        assertEquals(lidas, Escondidas.ler(Escondidas.escrever(lidas)))
    }

    @Test
    fun `no maximo 200`() {
        assertEquals(200, Escondidas.ler((1..300).joinToString("\n") { "C$it" }).size)
    }
}
