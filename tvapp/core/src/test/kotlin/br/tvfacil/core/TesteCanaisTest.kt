package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TesteCanaisTest {
    private fun grupo(extinf: String) = TesteCanais.classificar(M3uParser.parse("$extinf\nhttps://exemplo.com/a.m3u8").single())

    private fun canal(nome: String, url: String = "https://exemplo.com/$nome.m3u8", grupo: String = "") = Canal(nome = nome, url = url, grupo = grupo)

    @Test
    fun `classifica igual ao testa_regras do pipeline`() {
        val casos = listOf(
            """#EXTINF:-1 tvg-id="ESPNBrazil.br" group-title="Sports",ESPN (Brazil) (1080p)""" to "Esportes",
            """#EXTINF:-1 tvg-id="CazeTV.br" group-title="Entertainment",CazeTV (1080p)""" to null,
            """#EXTINF:-1 group-title="Undefined",Cazé TV""" to null,
            """#EXTINF:-1 tvg-id="FIFAPlus.int@Portuguese" group-title="General",FIFA+ Portuguese (720p)""" to "Esportes",
            """#EXTINF:-1 tvg-id="CNNBrasil.br" group-title="News",CNN Brasil (1080p)""" to "Noticias Brasil",
            """#EXTINF:-1 group-title="Brazil",Record News""" to "Noticias Brasil",
            """#EXTINF:-1 group-title="Brazil",TV Cultura""" to "Emissoras abertas",
            """#EXTINF:-1 tvg-id="CanalFutura.br" group-title="Education",Canal Futura (720p)""" to "Emissoras abertas",
            """#EXTINF:-1 group-title="Movies",Pluto TV Cine Sucessos""" to "Filmes",
            """#EXTINF:-1 group-title="Religious",TV Aparecida""" to null,
            """#EXTINF:-1 tvg-id="RTP1.pt" group-title="General",RTP 1""" to null,
            """#EXTINF:-1 group-title="Undefined",TV Qualquer""" to null,
            """#EXTINF:-1 group-title="Kids",Box Kids TV (720p)""" to "Outros",
            """#EXTINF:-1 group-title="Entertainment",Adult Swim Latin America Brazil""" to "Outros",
        )
        for ((extinf, esperado) in casos) assertEquals(esperado, grupo(extinf), extinf)
    }

    @Test
    fun `Sergipe vem primeiro e Camara vai para emissoras mesmo sendo legislativo`() {
        assertEquals("Sergipe", grupo("""#EXTINF:-1 group-title="Religious",TV Atalaia"""))
        assertEquals("Sergipe", grupo("""#EXTINF:-1 tvg-id="TVAperipe.br" group-title="General",Aperipê TV"""))
        assertEquals("Emissoras abertas", grupo("""#EXTINF:-1 group-title="Legislative",TV Câmara"""))
        assertEquals(null, grupo("""#EXTINF:-1 group-title="Shop",Shoptime"""))
    }

    @Test
    fun `juntar mantem a ordem, tira URL repetida e respeita o limite`() {
        val a = canal("A"); val b = canal("B"); val c = canal("C")
        val bDeNovo = canal("B outro nome", url = b.url)
        assertEquals(listOf(a, b, c), TesteCanais.juntar(listOf(listOf(a, b), listOf(bDeNovo, c))))
        assertEquals(listOf(a, b), TesteCanais.juntar(listOf(listOf(a, b), listOf(c)), max = 2))
    }

    @Test
    fun `resposta ok - codigo 2xx, com conteudo e sem pagina html`() {
        assertTrue(TesteCanais.respostaOk(200, "#EXTM3U\n#EXT-X-VERSION:3".toByteArray()))
        assertTrue(TesteCanais.respostaOk(206, byteArrayOf(0x47, 0x40, 0x00)))
        assertFalse(TesteCanais.respostaOk(404, "#EXTM3U".toByteArray()))
        assertFalse(TesteCanais.respostaOk(302, "#EXTM3U".toByteArray()))
        assertFalse(TesteCanais.respostaOk(200, ByteArray(0)))
        assertFalse(TesteCanais.respostaOk(200, "<!DOCTYPE html><title>erro</title>".toByteArray()))
        assertFalse(TesteCanais.respostaOk(200, "\n  <HTML><body>bloqueado</body>".toByteArray()))
        // html depois dos primeiros 512 bytes nao conta (como o head -c 512 do pipeline)
        assertTrue(TesteCanais.respostaOk(200, ("x".repeat(600) + "<html>").toByteArray()))
    }

    @Test
    fun `organizar troca o grupo e ordena pelos grupos, mantendo a ordem dentro de cada um`() {
        val outros1 = canal("Box Kids TV", grupo = "Kids")
        val noticia = canal("CNN Brasil", grupo = "News")
        val portugal = canal("RTP 1", grupo = "General")
        val outros2 = canal("Adult Swim", grupo = "Entertainment")
        val lista = TesteCanais.organizar(listOf(outros1, noticia, portugal, outros2))
        assertEquals(listOf("CNN Brasil", "Box Kids TV", "Adult Swim"), lista.map { it.nome })
        assertEquals(listOf("Noticias Brasil", "Outros", "Outros"), lista.map { it.grupo })
    }

    @Test
    fun `m3u gerado volta igual pelo leitor`() {
        val canais = listOf(
            Canal(nome = "CNN Brasil, ao vivo", url = "https://exemplo.com/cnn.m3u8", grupo = "Noticias Brasil", logo = "https://exemplo.com/cnn.png", tvgId = "CNNBrasil.br"),
            Canal(nome = "Filme", url = "http://exemplo.com/f.ts", grupo = "Filmes", userAgent = "Mozilla/5.0", referrer = "https://exemplo.com/", tipo = Tipo.FILME),
        )
        val texto = TesteCanais.m3u(canais)
        assertTrue(texto.startsWith("#EXTM3U\n"))
        assertEquals(canais, M3uParser.parse(texto))
    }

    @Test
    fun `dominio guarda so o host, sem usuario, senha, caminho ou parametros`() {
        assertEquals("servidor.com", TesteCanais.dominio("http://user:senha@servidor.com:8080/u/s/1.ts?senha=x&chave=y"))
        assertEquals("exemplo.com", TesteCanais.dominio("https://exemplo.com/live/usuario/senha/123.m3u8"))
        assertEquals("?", TesteCanais.dominio("isso nao e url"))
    }

    @Test
    fun `resumo no mesmo formato do relatorio do Termux`() {
        val lista = listOf(canal("CNN", grupo = "Noticias Brasil"), canal("Box", grupo = "Outros"), canal("Kids", grupo = "Outros"))
        assertEquals(
            "2026-11-14 09:53:57 5 de 9 canais funcionando, 2 removidos pelos filtros, 3 na lista (5min 8s)\n" +
                "2026-11-14 09:53:57 grupos: Sergipe=0; Noticias Brasil=1; Emissoras abertas=0; Filmes=0; Esportes=0; Outros=2; ",
            TesteCanais.resumo("2026-11-14 09:53:57", lidos = 9, funcionando = 5, lista = lista, segundos = 308),
        )
    }

    @Test
    fun `as listas embutidas sao as do pipeline, todas https`() {
        assertEquals(3, TesteCanais.LISTAS.size)
        assertTrue(TesteCanais.LISTAS.all { it.startsWith("https://") })
    }

    @Test
    fun `final - listas proprias primeiro, com os grupos delas, sem repetir canal`() {
        val filme = canal("Nosferatu (1922)", url = "http://meu.com/movie/u/s/1.mp4", grupo = "Filmes | Drama")
        val semGrupo = canal("Canal Meu", url = "http://meu.com/live/u/s/2.ts")
        val cnn = canal("CNN Brasil", grupo = "News")
        val repetido = canal("Copia", url = semGrupo.url, grupo = "Kids")
        val lista = TesteCanais.final(proprias = listOf(filme, semGrupo), publicasTestadas = listOf(cnn, repetido))
        assertEquals(listOf("Nosferatu (1922)", "Canal Meu", "CNN Brasil"), lista.map { it.nome })
        assertEquals(listOf("Filmes | Drama", "Outros", "Noticias Brasil"), lista.map { it.grupo })
    }
}
