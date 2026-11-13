package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BloqueioTest {
    private val hora = 60 * 60 * 1000L
    private val url = "https://exemplo.com/canal.m3u8"

    @Test
    fun `canal fica bloqueado por menos de 6 horas`() {
        val registros = setOf(Bloqueio.registro(url, agora = 0))
        assertEquals(setOf(url), Bloqueio.urls(Bloqueio.validos(registros, agora = 6 * hora - 1)))
    }

    @Test
    fun `depois de 6 horas o canal volta`() {
        val registros = setOf(Bloqueio.registro(url, agora = 0))
        assertEquals(emptySet(), Bloqueio.validos(registros, agora = 6 * hora))
    }

    @Test
    fun `marca da mesma ligacao do stick e queda do app`() {
        assertEquals(url, Bloqueio.queda(Bloqueio.marca(url, boot = 7), bootAtual = 7))
    }

    @Test
    fun `stick reiniciado ou sem energia nao e queda`() {
        assertEquals(null, Bloqueio.queda(Bloqueio.marca(url, boot = 7), bootAtual = 8))
        assertEquals(null, Bloqueio.queda(url, bootAtual = 7)) // marca antiga, sem o numero da ligacao
        assertEquals(null, Bloqueio.queda(null, bootAtual = 7))
    }

    @Test
    fun `formato antigo sem hora e relogio atrasado liberam o canal`() {
        val registros = setOf(url, Bloqueio.registro("https://outro.com/x", agora = 10 * hora))
        assertEquals(emptySet(), Bloqueio.validos(registros, agora = 1 * hora))
    }
}
