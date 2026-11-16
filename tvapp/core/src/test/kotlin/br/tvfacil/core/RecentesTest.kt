package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecentesTest {
    private val dia = 24 * 60 * 60 * 1000L
    private val agora = 100 * dia

    @Test
    fun `a ultima vista vai para a frente, sem repetir, com a hora`() {
        val lista = listOf(Recente("a", 1), Recente("b", 2), Recente("c", 3))
        assertEquals(listOf(Recente("b", agora), Recente("a", 1), Recente("c", 3)), Recentes.usar(lista, "b", agora))
    }

    @Test
    fun `guarda no maximo o limite`() {
        val lista = listOf("1", "2", "3").map { Recente(it, 1) }
        assertEquals(listOf("novo", "1", "2"), Recentes.usar(lista, "novo", agora, max = 3).map { it.chave })
    }

    @Test
    fun `tirar remove so aquela`() {
        assertEquals(listOf("a", "c"), Recentes.tirar(listOf("a", "b", "c").map { Recente(it, 1) }, "b").map { it.chave })
    }

    @Test
    fun `30 dias sem abrir sai sozinha`() {
        val lista = listOf(Recente("nova", agora - 29 * dia), Recente("velha", agora - 31 * dia))
        assertEquals(listOf("nova"), Recentes.validas(lista, agora).map { it.chave })
    }

    @Test
    fun `texto - le e escreve, e linha antiga sem hora conta a partir de agora`() {
        val lista = listOf(Recente("serie:X|Aurora", 5), Recente("serie:Y|Farol", 7))
        assertEquals(lista, Recentes.ler(Recentes.escrever(lista), agora))
        assertEquals(listOf(Recente("serie:X|Aurora", agora)), Recentes.ler("serie:X|Aurora\n\n", agora))
    }

    @Test
    fun `posicao do filme - com hora, formato antigo e vencida`() {
        assertEquals(Posicao(90_000, 5_400_000, 7), Posicao.ler(Posicao(90_000, 5_400_000, 7).texto(), agora))
        assertEquals(Posicao(90_000, 5_400_000, agora), Posicao.ler("90000:5400000", agora)) // antes da 0.33
        assertNull(Posicao.ler("lixo", agora))
        assertEquals(true, Posicao(1, 2, agora - 31 * dia).vencida(agora))
        assertEquals(false, Posicao(1, 2, agora - 29 * dia).vencida(agora))
    }
}
