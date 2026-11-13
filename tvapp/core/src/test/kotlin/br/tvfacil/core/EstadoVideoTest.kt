package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class EstadoVideoTest {
    private val cnn = Canal(nome = "CNN Brasil", url = "https://exemplo.com/cnn.m3u8")
    private val band = Canal(nome = "BandNews TV", url = "https://exemplo.com/band.m3u8")

    @Test
    fun `comeca parado e escolher um canal fica abrindo`() {
        assertEquals(Fase.PARADO, EstadoVideo().fase)
        assertEquals(EstadoVideo(Fase.ABRINDO, cnn), EstadoVideo().escolher(cnn))
    }

    @Test
    fun `quando chega imagem passa a tocando`() {
        assertEquals(Fase.TOCANDO, EstadoVideo().escolher(cnn).imagem(cnn).fase)
    }

    @Test
    fun `erro abrindo ou tocando vira indisponivel`() {
        assertEquals(Fase.INDISPONIVEL, EstadoVideo().escolher(cnn).falhou(cnn).fase)
        assertEquals(Fase.INDISPONIVEL, EstadoVideo().escolher(cnn).imagem(cnn).falhou(cnn).fase)
    }

    @Test
    fun `tempo esgotado so vale enquanto abre`() {
        assertEquals(Fase.INDISPONIVEL, EstadoVideo().escolher(cnn).tempoEsgotado(cnn).fase)
        assertEquals(Fase.TOCANDO, EstadoVideo().escolher(cnn).imagem(cnn).tempoEsgotado(cnn).fase)
    }

    @Test
    fun `imagem que chega depois do tempo esgotado ainda mostra o canal`() {
        assertEquals(Fase.TOCANDO, EstadoVideo().escolher(cnn).tempoEsgotado(cnn).imagem(cnn).fase)
    }

    @Test
    fun `avisos atrasados do canal anterior sao ignorados`() {
        val abrindoBand = EstadoVideo().escolher(cnn).escolher(band)
        assertEquals(abrindoBand, abrindoBand.falhou(cnn))
        assertEquals(abrindoBand, abrindoBand.imagem(cnn))
        assertEquals(abrindoBand, abrindoBand.tempoEsgotado(cnn))
    }
}
