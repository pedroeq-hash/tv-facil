package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontesTest {
    @Test
    fun `sem arquivo ainda, usa as 3 listas publicas, todas testadas`() {
        assertEquals(TesteCanais.LISTAS.map { Fonte(it, propria = false) }, Fontes.ler(null))
    }

    @Test
    fun `escreve e le de volta, separando lista propria de publica`() {
        val fontes = listOf(Fonte("https://iptv-org.github.io/iptv/countries/br.m3u", false), Fonte("http://meu.servidor.com/lista.m3u?u=a&p=b", true))
        assertEquals(fontes, Fontes.ler(Fontes.escrever(fontes)))
    }

    @Test
    fun `arquivo vazio = nenhuma lista (o usuario removeu todas)`() {
        assertEquals(emptyList(), Fontes.ler(""))
    }

    @Test
    fun `linha estragada ou com endereco proibido e ignorada`() {
        val texto = "T https://ok.com/a.m3u\nlixo\nP file:///sdcard/x.m3u\nX https://ok.com/b.m3u\nP http://127.0.0.1/a.m3u\n"
        assertEquals(listOf(Fonte("https://ok.com/a.m3u", false)), Fontes.ler(texto))
    }

    @Test
    fun `aceita http e https da internet`() {
        assertTrue(Fontes.urlAceita("https://iptv-org.github.io/iptv/countries/br.m3u"))
        assertTrue(Fontes.urlAceita("http://servidor.com:8080/get.php?u=a&p=b&type=m3u_plus"))
        assertTrue(Fontes.urlAceita("http://8.8.8.8/lista.m3u"))
    }

    @Test
    fun `recusa outros esquemas, enderecos da rede local e truques de IP`() {
        val recusadas = listOf(
            "file:///sdcard/lista.m3u", "ftp://servidor.com/a.m3u", "javascript:alert(1)", "servidor.com/a.m3u",
            "http://localhost:8080/a.m3u", "http://app.localhost/a.m3u",
            "http://127.0.0.1/a.m3u", "http://10.0.0.5/a.m3u", "http://172.16.3.4/a.m3u", "http://172.31.255.255/a",
            "http://192.168.1.1/a.m3u", "http://169.254.169.254/latest", "http://0.0.0.0/a", "http://100.64.0.1/a",
            "http://[::1]/a.m3u", "http://2130706433/a.m3u", "http://0x7f000001/a.m3u", "http://127.1/a.m3u",
            "https://servidor.com/a b.m3u", "https://servidor.com/" + "a".repeat(2048),
        )
        for (url in recusadas) assertFalse(Fontes.urlAceita(url), url)
        assertTrue(Fontes.urlAceita("http://172.32.0.1/a.m3u")) // fora da faixa privada 172.16-31
    }

    @Test
    fun `nome da lista - escreve e le de volta, e arquivo antigo sem nome continua valendo`() {
        val fontes = listOf(Fonte("https://ok.com/a.m3u", true, "Lista do João — filmes"), Fonte("https://ok.com/b.m3u", false))
        assertEquals(fontes, Fontes.ler(Fontes.escrever(fontes)))
        assertEquals(listOf(Fonte("https://ok.com/a.m3u", true, "")), Fontes.ler("P https://ok.com/a.m3u"))
    }

    @Test
    fun `nome vem do usuario - sem caracteres invisiveis, sem tab, ate 40 letras`() {
        assertEquals("AB", Fontes.nome("  A\u0007\tB\u202E ")) // controle, tab e inversao de texto somem
        assertEquals(40, Fontes.nome("x".repeat(100)).length)
    }
}
