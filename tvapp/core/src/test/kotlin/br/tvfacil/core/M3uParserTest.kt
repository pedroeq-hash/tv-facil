package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class M3uParserTest {

    @Test
    fun `le um canal com nome e url`() {
        val canais = M3uParser.parse(
            """
            #EXTM3U
            #EXTINF:-1,CNN Brasil
            https://exemplo.com/cnn.m3u8
            """.trimIndent()
        )
        assertEquals(listOf(Canal(nome = "CNN Brasil", url = "https://exemplo.com/cnn.m3u8")), canais)
    }

    @Test
    fun `le grupo e logo das etiquetas`() {
        val canal = M3uParser.parse(
            """
            #EXTINF:-1 tvg-id="cnn.br" tvg-logo="https://exemplo.com/cnn.png" group-title="Notícias Brasil",CNN Brasil
            https://exemplo.com/cnn.m3u8
            """.trimIndent()
        ).single()
        assertEquals("Notícias Brasil", canal.grupo)
        assertEquals("https://exemplo.com/cnn.png", canal.logo)
        assertEquals("CNN Brasil", canal.nome)
    }

    @Test
    fun `tipo filme vira FILME e o resto e ao vivo`() {
        val canais = M3uParser.parse(
            """
            #EXTINF:-1 tipo="filme" group-title="Meus filmes",Central do Brasil (1998)
            https://exemplo.com/central.mkv
            #EXTINF:-1 tipo="qualquer",Canal A
            https://exemplo.com/a.m3u8
            #EXTINF:-1,Canal B
            https://exemplo.com/b.m3u8
            """.trimIndent()
        )
        assertEquals(listOf(Tipo.FILME, Tipo.AO_VIVO, Tipo.AO_VIVO), canais.map { it.tipo })
    }

    @Test
    fun `virgula dentro de etiqueta nao corta o nome`() {
        val canal = M3uParser.parse(
            """
            #EXTINF:-1 group-title="Filmes, Séries",Cine Clássico, 24h
            https://exemplo.com/cine.m3u8
            """.trimIndent()
        ).single()
        assertEquals("Filmes, Séries", canal.grupo)
        assertEquals("Cine Clássico, 24h", canal.nome)
    }

    @Test
    fun `le user-agent e referrer do EXTVLCOPT`() {
        val canal = M3uParser.parse(
            """
            #EXTINF:-1,Canal Protegido
            #EXTVLCOPT:http-user-agent=Mozilla/5.0 (Android)
            #EXTVLCOPT:http-referrer=https://site.com/
            https://exemplo.com/p.m3u8
            """.trimIndent()
        ).single()
        assertEquals("Mozilla/5.0 (Android)", canal.userAgent)
        assertEquals("https://site.com/", canal.referrer)
    }

    @Test
    fun `tolera quebra de linha do Windows, linhas vazias e espacos`() {
        val texto = "#EXTM3U\r\n\r\n#EXTINF:-1,Canal A  \r\n\r\n  https://exemplo.com/a.m3u8  \r\n"
        assertEquals(listOf(Canal(nome = "Canal A", url = "https://exemplo.com/a.m3u8")), M3uParser.parse(texto))
    }

    @Test
    fun `blocos quebrados sao ignorados sem perder o resto`() {
        val canais = M3uParser.parse(
            """
            https://exemplo.com/sem-extinf.m3u8
            #EXTINF:-1,Sem URL
            #EXTVLCOPT:http-user-agent=Vazado
            #EXTINF:-1,Canal Bom
            https://exemplo.com/bom.m3u8
            """.trimIndent()
        )
        assertEquals(listOf(Canal(nome = "Canal Bom", url = "https://exemplo.com/bom.m3u8")), canais)
    }
}
