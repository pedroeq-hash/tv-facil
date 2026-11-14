package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PainelTest {
    private val endereco = "10.0.0.20:8765"
    private var salvas: List<Fonte>? = null
    private val publica = Fonte("https://iptv-org.github.io/iptv/countries/br.m3u", false)
    private val painel = Painel(endereco, pin = "123456", token = "tok", fontes = listOf(publica), pagina = "<html>pagina</html>", registro = { "linha do log" }) { salvas = it }

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
        assertEquals("0\tiptv-org.github.io\tpublica\n1\tmeu.servidor.com\tpropria\n", corpo)
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
    fun `mostra o registro e responde 404 para o resto`() {
        assertEquals("linha do log", painel.responder(comToken("GET", "/registro")).corpo)
        assertEquals(404, painel.responder(comToken("GET", "/outra")).codigo)
    }
}
