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
    private var escondidasSalvas: List<String>? = null
    private val publica = Fonte("https://iptv-org.github.io/iptv/countries/br.m3u", false)
    // Dois avulsos que a TV ja tem, com usuario e senha no link (como nas listas pagas).
    private val avulsosIniciais = listOf(
        Canal("Canal A", "http://srv.com:8080/joao/segredo/a", "Globo"),
        Canal("Filme B", "http://srv.com/movie/joao/segredo/b.mp4", "Meus filmes", tipo = Tipo.FILME),
    )
    private val painel = Painel(
        endereco, pin = "123456", token = "tok", fontes = listOf(publica), pagina = "<html>pagina</html>",
        registro = { "2026-11-15 06:51:00 inicio da rodada\n  falhou: x.com - HTTP 404" }, avulsos = avulsosIniciais, aoMudarAvulsos = { avulsosSalvos = it },
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
        assertEquals("0\tiptv-org.github.io\tpublica\t\t1\n1\tmeu.servidor.com\tpropria\t\t1\n", corpo)
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
    fun `lista os avulsos sem devolver a senha`() {
        assertEquals(
            "0\tCanal A\tsrv.com\tcanal\tGlobo\n1\tFilme B\tsrv.com\tfilme\tMeus filmes\n",
            painel.responder(comToken("GET", "/avulsos")).corpo,
        )
        assertFalse(listOf("/avulsos", "/listas", "/registro").any { "segredo" in painel.responder(comToken("GET", it)).corpo })
    }

    @Test
    fun `arquivo m3u acrescenta aos avulsos, sem repetir link`() {
        val r = painel.responder(comToken("POST", "/avulsos", m3u(9)))
        assertEquals(200 to "9 acrescentados", r.codigo to r.corpo)
        assertEquals(11, avulsosSalvos!!.size)
        assertEquals("Canal A", avulsosSalvos!![0].nome)
        assertEquals("Canal 1", avulsosSalvos!![2].nome)
        assertEquals(200 to "0 acrescentados", painel.responder(comToken("POST", "/avulsos", m3u(9))).let { it.codigo to it.corpo })
        assertEquals(11, avulsosSalvos!!.size)
    }

    @Test
    fun `arquivo sem canal valido ou que passa de 300 e recusado e nada muda`() {
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", "")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", m3u(3) { "file:///sdcard/$it.ts" })).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos", m3u(299))).codigo) // 2 + 299 = 301
        assertNull(avulsosSalvos)
        assertEquals(200, painel.responder(comToken("POST", "/avulsos", m3u(298))).codigo)
    }

    @Test
    fun `um canal ou filme por vez, com categoria padrao`() {
        assertEquals(200, painel.responder(comToken("POST", "/avulsos/um", "canal\nGlobo SE\nhttps://x.com/globo.m3u8\n")).codigo)
        assertEquals(Canal("Globo SE", "https://x.com/globo.m3u8", "Meus canais"), avulsosSalvos!!.last())
        assertEquals(200, painel.responder(comToken("POST", "/avulsos/um", "filme\nTitanic (1997)\nhttps://x.com/t.mp4\nDra\"ma")).codigo)
        assertEquals(Canal("Titanic (1997)", "https://x.com/t.mp4", "Drama", tipo = Tipo.FILME), avulsosSalvos!!.last())
        assertEquals(200, painel.responder(comToken("POST", "/avulsos/um", "filme\nOutro\nhttps://x.com/o.mp4\n")).codigo)
        assertEquals("Meus filmes", avulsosSalvos!!.last().grupo)
    }

    @Test
    fun `um por vez - link ruim, sem nome, repetido ou sem o token e recusado`() {
        assertEquals(400, painel.responder(comToken("POST", "/avulsos/um", "canal\nX\nfile:///sdcard/a.ts\n")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos/um", "canal\n \nhttps://x.com/a.m3u8\n")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos/um", "canal\nDe novo\nhttp://srv.com:8080/joao/segredo/a\n")).codigo)
        assertEquals(400, painel.responder(comToken("POST", "/avulsos/um", "outro\nX\nhttps://x.com/a.m3u8\n")).codigo)
        assertEquals(401, painel.responder(pedido("POST", "/avulsos/um", "canal\nX\nhttps://x.com/a.m3u8\n")).codigo)
        assertNull(avulsosSalvos)
    }

    @Test
    fun `remove um avulso pelo numero ou apaga todos`() {
        assertEquals(200, painel.responder(comToken("DELETE", "/avulsos/0")).codigo)
        assertEquals(listOf("Filme B"), avulsosSalvos!!.map { it.nome })
        assertEquals(404, painel.responder(comToken("DELETE", "/avulsos/5")).codigo)
        assertEquals(200, painel.responder(comToken("DELETE", "/avulsos")).codigo)
        assertEquals(emptyList(), avulsosSalvos)
        assertEquals("", painel.responder(comToken("GET", "/avulsos")).corpo)
    }

    @Test
    fun `lista propria pode ficar so com filmes e series (sem canais ao vivo)`() {
        painel.responder(comToken("POST", "/listas", "https://ok.com/a.m3u\nVOD"))
        assertEquals(401, painel.responder(pedido("PUT", "/listas/1/aovivo", "0")).codigo)
        assertEquals(200, painel.responder(comToken("PUT", "/listas/1/aovivo", "0")).codigo)
        assertEquals(Fonte("https://ok.com/a.m3u", true, "VOD", aoVivo = false), salvas!![1])
        assertTrue(painel.responder(comToken("GET", "/listas")).corpo.endsWith("1\tok.com\tpropria\tVOD\t0\n"))
        assertEquals(200, painel.responder(comToken("PUT", "/listas/1/aovivo", "1")).codigo)
        assertEquals(true, salvas!![1].aoVivo)
        assertEquals(400, painel.responder(comToken("PUT", "/listas/0/aovivo", "0")).codigo) // publica: so tem canais
        assertEquals(404, painel.responder(comToken("PUT", "/listas/9/aovivo", "0")).codigo)
    }

    @Test
    fun `endereco completo da lista so com o token`() {
        assertEquals(401, painel.responder(pedido("GET", "/listas/0/endereco")).codigo)
        assertEquals("https://iptv-org.github.io/iptv/countries/br.m3u", painel.responder(comToken("GET", "/listas/0/endereco")).corpo)
        assertEquals(404, painel.responder(comToken("GET", "/listas/9/endereco")).codigo)
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
    fun `categorias escondidas - mostra todas com a marca e salva a escolha, so com o token`() {
        val p = Painel(
            endereco, pin = "123456", token = "tok", fontes = listOf(publica), pagina = "", registro = { "" },
            avulsos = emptyList(), aoMudarAvulsos = {}, categorias = { listOf("NETFLIX", "BRASIL PARALELO") },
            escondidas = Escondidas.PADRAO, aoMudarEscondidas = { escondidasSalvas = it },
        ) {}
        assertEquals(401, p.responder(pedido("GET", "/categorias")).codigo)
        assertEquals(401, p.responder(pedido("PUT", "/escondidas", "NETFLIX")).codigo)
        assertEquals("BRASIL PARALELO\t1\nNETFLIX\t0\n", p.responder(comToken("GET", "/categorias")).corpo)
        assertEquals(200, p.responder(comToken("PUT", "/escondidas", "NETFLIX\n\u0007\n")).codigo)
        assertEquals(listOf("NETFLIX"), escondidasSalvas)
        assertEquals("BRASIL PARALELO\t0\nNETFLIX\t1\n", p.responder(comToken("GET", "/categorias")).corpo)
    }

    @Test
    fun `nome da lista - na 2a linha ao adicionar, trocado depois com PUT`() {
        assertEquals(200, painel.responder(comToken("POST", "/listas", "https://ok.com/a.m3u\nLista do João")).codigo)
        assertEquals(Fonte("https://ok.com/a.m3u", true, "Lista do João"), salvas!!.last())
        assertEquals(200, painel.responder(comToken("PUT", "/listas/0", "Públicas BR")).codigo)
        assertEquals("Públicas BR", salvas!![0].nome)
        assertEquals(
            "0\tiptv-org.github.io\tpublica\tPúblicas BR\t1\n1\tok.com\tpropria\tLista do João\t1\n",
            painel.responder(comToken("GET", "/listas")).corpo,
        )
        assertEquals(404, painel.responder(comToken("PUT", "/listas/9", "x")).codigo)
        assertEquals(401, painel.responder(pedido("PUT", "/listas/0", "x")).codigo)
    }
}
