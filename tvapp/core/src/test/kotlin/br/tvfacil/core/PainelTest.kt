package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PainelTest {
    private val endereco = "10.0.0.20:8765"
    private var salvas: List<Fonte>? = null
    private var avulsosSalvos: List<Canal>? = null
    private val publica = Fonte("https://iptv-org.github.io/iptv/countries/br.m3u", false)
    private val painel = Painel(
        endereco, pin = "123456", token = "tok", fontes = listOf(publica), pagina = "<html>pagina</html>",
        registro = { "2026-11-15 06:51:00 inicio da rodada\n  falhou: x.com - HTTP 404" }, avulsos = 2, aoMudarAvulsos = { avulsosSalvos = it },
    ) { salvas = it }

    // Arquivo .m3u com n canais; por padrao com usuario e senha no link, como nas listas pagas.
    private fun m3u(n: Int, url: (Int) -> String = { "http://srv.com:8080/joao/segredo/$it" }) =
        "#EXTM3U\n" + (1..n).joinToString("") { "#EXTINF:-1 group-title=\"Globo\",Canal $it\n${url(it)}\n" }

    private fun pedido(metodo: String, caminho: String, corpo: String = "", vararg extras: Pair<String, String>) =
        Pedido(metodo, caminho, mapOf("host" to endereco) + extras, corpo)

    private fun comToken(metodo: String, caminho: String, corpo: String = "") = pedido(metodo, caminho, corpo, "x-token" to "tok")

    @Test
    fun `entrega a pagina e pareia com o PIN certo`() {
        assertEquals(Resposta(200, "text/html; charset=utf-8", "<html>pagina</html>"), painel.responder(pedido("GET", "/")))
        assertFalse(painel.pareado)
        assertEquals(200 to "tok", painel.responder(pedido("POST", "/parear", "123456\n")).let { it.codigo to it.corpo })
        assertTrue(painel.pareado)
    }

    @Test
    fun `Host diferente (DNS rebinding) ou Origin de outro site sao recusados`() {
        assertEquals(403, painel.responder(Pedido("GET", "/", mapOf("host" to "site-mau.com:8765"), "")).codigo)
        assertEquals(403, painel.responder(Pedido("GET", "/", emptyMap(), "")).codigo)
        assertEquals(403, painel.responder(pedido("POST", "/parear", "123456", "origin" to "https://site-mau.com")).codigo)
        assertEquals(200, painel.responder(pedido("POST", "/parear", "123456", "origin" to "http://$endereco")).codigo)
    }

    @Test
    fun `sem o token certo, nada alem da pagina e do pareamento`() {
        assertEquals(401, painel.responder(pedido("GET", "/listas")).codigo)
        assertEquals(401, painel.responder(pedido("GET", "/registro", "", "x-token" to "errado")).codigo)
        assertEquals(401, painel.responder(pedido("POST", "/listas", "https://ok.com/a.m3u")).codigo)
    }

    @Test
    fun `5 PINs errados bloqueiam tudo, ate o PIN certo`() {
        repeat(5) { assertEquals(401, painel.responder(pedido("POST", "/parear", "000000")).codigo) }
        assertTrue(painel.bloqueado)
        assertEquals(403, painel.responder(pedido("POST", "/parear", "123456")).codigo)
        assertEquals(403, painel.responder(pedido("GET", "/")).codigo)
    }

    @Test
    fun `lista as fontes mostrando so o dominio`() {
        painel.responder(comToken("POST", "/listas", "http://meu.servidor.com:8080/get.php?u=joao&p=segredo"))
        val corpo = painel.responder(comToken("GET", "/listas")).corpo
        assertEquals("0\tiptv-org.github.io\tpublica\t\n1\tmeu.servidor.com\tpropria\t\n", corpo)
        assertFalse("segredo" in corpo || "joao" in corpo)
    }

    @Test
    fun `adiciona lista propria, recusa endereco proibido e nao repete`() {
        assertEquals(200, painel.responder(comToken("POST", "/listas", " https://ok.com/a.m3u \n")).codigo)
        assertEquals(listOf(publica, Fonte("https://ok.com/a.m3u", true)), salvas)
        assertEquals(400, painel.responder(comToken("POST", "/listas", "http://192.168.0.5/a.m3u")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/listas", "file:///sdcard/a.m3u")).codigo)
        assertEquals(200, painel.responder(comToken("POST", "/listas", "https://ok.com/a.m3u")).codigo)
        assertEquals(2, painel.fontes.size)
    }

    @Test
    fun `remove pelo numero`() {
        assertEquals(200, painel.responder(comToken("DELETE", "/listas/0")).codigo)
        assertEquals(emptyList(), salvas)
        assertEquals(404, painel.responder(comToken("DELETE", "/listas/0")).codigo)
        assertEquals(404, painel.responder(comToken("DELETE", "/listas/abc")).codigo)
    }

    @Test
    fun `canais avulsos so com o token`() {
        assertEquals(401, painel.responder(pedido("GET", "/avulsos")).codigo)
        assertEquals(401, painel.responder(pedido("POST", "/avulsos", m3u(1))).codigo)
        assertEquals(401, painel.responder(pedido("DELETE", "/avulsos")).codigo)
        assertNull(avulsosSalvos)
    }

    @Test
    fun `recebe o arquivo m3u, conta os canais e nao devolve a senha`() {
        assertEquals("2", painel.responder(comToken("GET", "/avulsos")).corpo)
        val r = painel.responder(comToken("POST", "/avulsos", m3u(9)))
        assertEquals(200 to "9 canais salvos", r.codigo to r.corpo)
        assertEquals(9, avulsosSalvos!!.size)
        assertEquals("Canal 1", avulsosSalvos!![0].nome)
        assertEquals("9", painel.responder(comToken("GET", "/avulsos")).corpo)
        assertFalse(listOf("/avulsos", "/listas", "/registro").any { "segredo" in painel.responder(comToken("GET", it)).corpo })
    }

    @Test
    fun `arquivo sem canal valido ou com canais demais e recusado e nada muda`() {
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", "")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", m3u(3) { "file:///sdcard/$it.ts" })).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", m3u(301))).codigo)
        assertNull(avulsosSalvos)
        assertEquals("2", painel.responder(comToken("GET", "/avulsos")).corpo)
        assertEquals(200, painel.responder(comToken("POST", "/avulsos", m3u(300))).codigo)
    }

    @Test
    fun `apagar limpa os canais avulsos`() {
        assertEquals(200, painel.responder(comToken("DELETE", "/avulsos")).codigo)
        assertEquals(emptyList(), avulsosSalvos)
        assertEquals("0", painel.responder(comToken("GET", "/avulsos")).corpo)
    }

    @Test
    fun `mostra o registro e responde 404 para o resto`() {
        assertEquals("2026-11-15 06:51:00 inicio da rodada\n  falhou: x.com - HTTP 404", painel.responder(comToken("GET", "/registro")).corpo)
        assertEquals(404, painel.responder(comToken("GET", "/outra")).codigo)
    }

    @Test
    fun `resumo simples do registro so com o token`() {
        assertEquals(401, painel.responder(pedido("GET", "/resumo")).codigo)
        val r = painel.responder(comToken("GET", "/resumo"))
        assertEquals(200, r.codigo)
        assertEquals("Próximo teste dos canais: por volta de 15/11 12:51.\n15/11 06:51 — Testando os canais agora…", r.corpo)
    }

    @Test
    fun `nome da lista - na 2a linha ao adicionar, trocado depois com PUT`() {
        assertEquals(200, painel.responder(comToken("POST", "/listas", "https://ok.com/a.m3u\nLista do João")).codigo)
        assertEquals(Fonte("https://ok.com/a.m3u", true, "Lista do João"), salvas!!.last())
        assertEquals(200, painel.responder(comToken("PUT", "/listas/0", "Públicas BR")).codigo)
        assertEquals("Públicas BR", salvas!![0].nome)
        assertEquals(
            "0\tiptv-org.github.io\tpublica\tPúblicas BR\n1\tok.com\tpropria\tLista do João\n",
            painel.responder(comToken("GET", "/listas")).corpo,
        )
        assertEquals(404, painel.responder(comToken("PUT", "/listas/9", "x")).codigo)
        assertEquals(401, painel.responder(pedido("PUT", "/listas/0", "x")).codigo)
    }
}
