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
        assertEquals("cnn.br", canal.tvgId)
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

    private fun um(url: String) = M3uParser.parse("#EXTINF:-1,Canal\n$url")

    @Test
    fun `so aceita enderecos http e https`() {
        for (url in listOf("file:///sdcard/x.ts", "mms://exemplo.com/a", "rtsp://exemplo.com/a", "javascript:alert(1)", "exemplo.com/a.m3u8")) {
            assertEquals(emptyList(), um(url), url)
        }
        assertEquals(1, um("HTTPS://exemplo.com/a.m3u8").size)
        assertEquals(1, um("http://exemplo.com/a.m3u8").size)
    }

    @Test
    fun `ignora endereco longo demais ou com espaco e controle`() {
        assertEquals(emptyList(), um("https://exemplo.com/" + "a".repeat(2048)))
        assertEquals(emptyList(), um("https://exemplo.com/a b.m3u8"))
        assertEquals(emptyList(), um("https://exemplo.com/a\u0000.m3u8"))
    }

    @Test
    fun `tira caracteres invisiveis e de inversao de texto do nome, grupo e opcoes`() {
        val canal = M3uParser.parse(
            "#EXTINF:-1 group-title=\"Not​ícias\u0007\",CNN‮ Brasil\u0000\n" +
                "#EXTVLCOPT:http-user-agent=Mozilla\u0001/5.0\n" +
                "#EXTVLCOPT:http-referrer=https://exemplo.com/⁦\n" +
                "https://exemplo.com/cnn.m3u8"
        ).single()
        assertEquals("CNN Brasil", canal.nome)
        assertEquals("Notícias", canal.grupo)
        assertEquals("Mozilla/5.0", canal.userAgent)
        assertEquals("https://exemplo.com/", canal.referrer)
    }

    @Test
    fun `corta nome, grupo e opcoes compridos demais`() {
        val canal = M3uParser.parse(
            "#EXTINF:-1 group-title=\"${"g".repeat(100)}\",${"n".repeat(200)}\n" +
                "#EXTVLCOPT:http-user-agent=${"u".repeat(1000)}\n" +
                "#EXTVLCOPT:http-referrer=${"r".repeat(1000)}\n" +
                "https://exemplo.com/a.m3u8"
        ).single()
        assertEquals(80, canal.nome.length)
        assertEquals(40, canal.grupo.length)
        assertEquals(256, canal.userAgent.length)
        assertEquals(256, canal.referrer.length)
    }

    @Test
    fun `no maximo 5000 canais por lista`() {
        val texto = (1..5001).joinToString("\n") { "#EXTINF:-1,C$it\nhttps://exemplo.com/$it.m3u8" }
        val canais = M3uParser.parse(texto)
        assertEquals(5000, canais.size)
        assertEquals("C5000", canais.last().nome)
    }

    private fun nome(n: String) = M3uParser.parse("#EXTINF:-1,$n\nhttps://exemplo.com/a.m3u8").single().nome

    @Test
    fun `ler aos pedacos - sem o limite de 5000 (lista grande de filmes)`() {
        val texto = (1..6000).joinToString("") { "#EXTINF:-1,Filme $it\nhttps://x.com/movie/$it.mp4\n" }
        assertEquals(6000, M3uParser.ler(texto.lineSequence()).count())
        assertEquals(5000, M3uParser.parse(texto).size)
    }

    @Test
    fun `nome limpo - tira qualidade e etiquetas, mantem o resto`() {
        assertEquals("GloboNews", nome("GloboNews (1080p)"))
        assertEquals("ESPN (Brazil)", nome("ESPN (Brazil) (1080p)"))
        assertEquals("Canal Brasil", nome("Canal Brasil [Geo-blocked]"))
        assertEquals("Rede Vida", nome("Rede Vida (720p) [Not 24/7]"))
        assertEquals("Discovery", nome("Discovery HD"))
        assertEquals("Cine Ação", nome("Cine Ação FHD H265"))
        assertEquals("Esporte", nome("Esporte (1080p60)"))
        assertEquals("Titanic (1997)", nome("Titanic (1997)"))
        assertEquals("HDTV Notícias", nome("HDTV Notícias")) // HD dentro da palavra fica
        assertEquals("4K", nome("4K")) // so marcas: fica como estava
        // [L] legendado, [DUB] dublado: separam versoes da mesma serie/filme, ficam
        assertEquals("Jogos do Destino [L]", nome("Jogos do Destino [L]"))
        assertEquals("Destan [DUB] S01E02", nome("Destan [DUB] S01E02"))
    }
}
