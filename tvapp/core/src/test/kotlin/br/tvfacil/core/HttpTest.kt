package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpTest {
    private fun ler(texto: String) = Http.ler(texto.byteInputStream())

    @Test
    fun `le metodo, caminho e cabecalhos (nomes em minusculas)`() {
        val p = ler("GET /listas HTTP/1.1\r\nHost: 10.0.0.20:8765\r\nX-Token: abc\r\n\r\n")!!
        assertEquals("GET", p.metodo)
        assertEquals("/listas", p.caminho)
        assertEquals("10.0.0.20:8765", p.cabecalhos["host"])
        assertEquals("abc", p.cabecalhos["x-token"])
        assertEquals("", p.corpo)
    }

    @Test
    fun `le o corpo pelo Content-Length`() {
        val p = ler("POST /parear HTTP/1.1\r\nHost: x\r\nContent-Length: 6\r\n\r\n123456resto-ignorado")!!
        assertEquals("123456", p.corpo)
    }

    @Test
    fun `pedido malformado ou grande demais vira null`() {
        assertNull(ler(""))
        assertNull(ler("lixo sem quebra de linha"))
        assertNull(ler("GET\r\n\r\n"))
        assertNull(ler("GET /a HTTP/1.1\r\nCabecalho sem dois pontos\r\n\r\n"))
        assertNull(ler("GET /a HTTP/1.1\r\nX: " + "a".repeat(9000) + "\r\n\r\n"))
        assertNull(ler("POST /a HTTP/1.1\r\nContent-Length: 5000\r\n\r\n" + "a".repeat(5000)))
        assertNull(ler("POST /a HTTP/1.1\r\nContent-Length: abc\r\n\r\n"))
        assertNull(ler("POST /a HTTP/1.1\r\nContent-Length: 10\r\n\r\ncurto")) // corpo acabou antes
    }
}
