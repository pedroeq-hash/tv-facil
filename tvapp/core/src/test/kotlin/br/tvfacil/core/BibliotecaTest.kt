package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BibliotecaTest {
    private fun c(nome: String, url: String, grupo: String = "", tipo: Tipo = Tipo.AO_VIVO) = Canal(nome, url, grupo, tipo = tipo)

    @Test
    fun `canal ao vivo continua canal, com o grupo da lista`() {
        val globo = c("Globo", "http://s.com/live/u/p/1.ts", "Abertos")
        assertEquals(Item.AoVivo(globo), Biblioteca.classificar(globo))
        val semExtensao = c("Record", "http://s.com/u/p/123")
        assertEquals(Item.AoVivo(semExtensao), Biblioteca.classificar(semExtensao))
    }

    @Test
    fun `filme pelo caminho, pela extensao ou pela etiqueta tipo`() {
        assertEquals(
            Item.Filme(c("Titanic (1997)", "http://s.com/movie/u/p/1.mp4", "Drama", Tipo.FILME)),
            Biblioteca.classificar(c("Titanic (1997)", "http://s.com/movie/u/p/1.mp4", "FILMES | Drama")),
        )
        for (url in listOf("https://a.org/x/Nosferatu.MKV", "https://a.org/x/f.mp4?v=1", "https://a.org/x/f.webm")) {
            assertEquals(Item.Filme(c("F", url, "Outros", Tipo.FILME)), Biblioteca.classificar(c("F", url)), url)
        }
        assertEquals(
            Item.Filme(c("F", "https://a.org/f.m3u8", "Outros", Tipo.FILME)),
            Biblioteca.classificar(c("F", "https://a.org/f.m3u8", tipo = Tipo.FILME)),
        )
        // "13" no nome nao e episodio
        assertEquals(Tipo.FILME, (Biblioteca.classificar(c("Apollo 13", "http://s.com/movie/u/p/2.mp4")) as Item.Filme).canal.tipo)
    }

    @Test
    fun `episodio - serie, temporada e numero tirados do nome`() {
        assertEquals(
            Item.Episodio("Gotham", 3, 13, c("Gotham S03E13", "http://s.com/series/u/p/9.mp4", "MAX", Tipo.FILME)),
            Biblioteca.classificar(c("Gotham S03E13", "http://s.com/series/u/p/9.mp4", "SÉRIES | MAX")),
        )
        assertEquals(Item.Episodio("Dark", 1, 2, c("Dark S1 E2", "https://a.org/dark.mp4", "Outros", Tipo.FILME)), Biblioteca.classificar(c("Dark S1 E2", "https://a.org/dark.mp4")))
        // em /series/ sem numero: temporada 1, numero 0 (fica na ordem da lista)
        val especial = Biblioteca.classificar(c("Especial de Natal", "http://s.com/series/u/p/10.mp4")) as Item.Episodio
        assertEquals(Triple("Especial de Natal", 1, 0), Triple(especial.serie, especial.temporada, especial.numero))
    }

    @Test
    fun `categoria sem o prefixo FILMES ou SERIES`() {
        assertEquals("Drama", Biblioteca.categoria("FILMES | Drama"))
        assertEquals("NETFLIX", Biblioteca.categoria("SÉRIES | NETFLIX"))
        assertEquals("Max", Biblioteca.categoria("Series|Max"))
        assertEquals("Ação", Biblioteca.categoria("VOD | Ação"))
        assertEquals("Cinema", Biblioteca.categoria("Cinema"))
        assertEquals("Outros", Biblioteca.categoria(" "))
        assertEquals("Esportes | Futebol", Biblioteca.categoria("Esportes | Futebol"))
    }

    @Test
    fun `indice das series - escreve, le de volta e ignora linha estragada`() {
        val series = listOf(Serie(0, "Dark", "NETFLIX", 26), Serie(1, "A Grande Família", "GLOBOPLAY", 485))
        assertEquals(series, Biblioteca.lerIndice(Biblioteca.indice(series)))
        val estragado = "lixo\n2\tX\tA\tB\t3\n-1\tX\tY\t2\n../1\tX\tY\t2\n3\tX\tZ\tabc\n4\tNETFLIX\tDark\t26\n"
        assertEquals(listOf(Serie(4, "Dark", "NETFLIX", 26)), Biblioteca.lerIndice(estragado))
    }

    @Test
    fun `nome malicioso nao quebra o indice - o M3uParser ja tirou tab e quebra de linha`() {
        val canal = M3uParser.parse("#EXTINF:-1 group-title=\"SÉRIES | X\",Má\tSérie\u0000\u0007 S01E01\nhttp://s.com/series/1.mp4\n").single()
        val ep = Biblioteca.classificar(canal) as Item.Episodio
        val serie = Serie(7, ep.serie, ep.canal.grupo, 1)
        assertEquals(listOf(serie), Biblioteca.lerIndice(Biblioteca.indice(listOf(serie))))
    }

    @Test
    fun `temporadas em ordem, episodios pelo numero`() {
        val eps = listOf("Dark S02E01", "Dark S01E02", "Dark S01E01", "Dark Especial").map { c(it, "https://a.org/$it.mp4") }
        val t = Biblioteca.temporadas(eps)
        assertEquals(listOf("Temporada 1", "Temporada 2"), t.map { it.nome })
        assertEquals(listOf("Dark Especial", "Dark S01E01", "Dark S01E02"), t[0].canais.map { it.nome })
        assertEquals(listOf("Dark S02E01"), t[1].canais.map { it.nome })
    }

    /** Guarda o que a Biblioteca mandaria gravar. */
    private class Gravado : Destino {
        val aoVivo = mutableListOf<Canal>()
        val filmes = mutableListOf<Canal>()
        val blocos = mutableListOf<Pair<Int, List<String>>>() // id da serie -> nomes dos episodios
        override fun aoVivo(c: Canal) { aoVivo += c }
        override fun filme(c: Canal) { filmes += c }
        override fun episodios(serie: Serie, eps: List<Canal>) { blocos += serie.id to eps.map { it.nome } }
    }

    private fun ep(nome: String, grupo: String = "SÉRIES | X") = c(nome, "http://s.com/series/u/p/$nome.mp4", grupo)

    @Test
    fun `montar - separa tudo e numera as series na ordem da lista`() {
        val g = Gravado()
        val itens = sequenceOf(
            c("Globo", "http://s.com/live/1.ts"), c("Titanic", "http://s.com/movie/1.mp4", "FILMES | Drama"),
            ep("Dark S01E01"), ep("Dark S01E02"), ep("Gotham S01E01"),
        )
        val indice = Biblioteca.montar(itens, g)
        assertEquals(listOf("Globo"), g.aoVivo.map { it.nome })
        assertEquals(listOf("Titanic"), g.filmes.map { it.nome })
        assertEquals(listOf(0 to listOf("Dark S01E01", "Dark S01E02"), 1 to listOf("Gotham S01E01")), g.blocos)
        assertEquals(listOf(Serie(0, "Dark", "X", 2), Serie(1, "Gotham", "X", 1)), indice)
    }

    @Test
    fun `montar - serie que volta depois usa o mesmo numero, mesmo nome em outra categoria e outra serie`() {
        val g = Gravado()
        val indice = Biblioteca.montar(sequenceOf(ep("Dark S01E01"), ep("Lost S01E01"), ep("Dark S01E02"), ep("Dark S01E01", "SÉRIES | Y")), g)
        assertEquals(listOf(0, 1, 0, 2), g.blocos.map { it.first })
        assertEquals(listOf(Serie(0, "Dark", "X", 2), Serie(1, "Lost", "X", 1), Serie(2, "Dark", "Y", 1)), indice)
    }

    @Test
    fun `montar - respeita os limites`() {
        val g = Gravado()
        val filmes = (1..Biblioteca.MAX_FILMES + 5).asSequence().map { c("F$it", "http://s.com/movie/$it.mp4") }
        val eps = (1..Biblioteca.MAX_EPISODIOS + 5).asSequence().map { ep("Novela S01E$it") }
        val series = (1..Biblioteca.MAX_SERIES + 5).asSequence().map { ep("S$it x S01E01") }
        val indice = Biblioteca.montar(filmes + eps + series, g)
        assertEquals(Biblioteca.MAX_FILMES, g.filmes.size)
        assertEquals(Biblioteca.MAX_SERIES, indice.size)
        assertEquals(Biblioteca.MAX_EPISODIOS, indice[0].episodios)
        assertEquals(Biblioteca.MAX_EPISODIOS, g.blocos.filter { it.first == 0 }.sumOf { it.second.size })
    }

    /** Lista real (fora do repositorio): LISTA_REAL=caminho gradlew :core:test --tests '*BibliotecaTest*'. */
    @Test
    fun `lista real - conta e mede (pulado sem LISTA_REAL)`() {
        val caminho = System.getenv("LISTA_REAL") ?: return
        val g = object : Destino {
            var filmes = 0; var aoVivo = 0
            override fun aoVivo(c: Canal) { aoVivo++ }
            override fun filme(c: Canal) { filmes++ }
            override fun episodios(serie: Serie, eps: List<Canal>) = Unit
        }
        val inicio = System.currentTimeMillis()
        val indice = java.io.File(caminho).useLines { Biblioteca.montar(M3uParser.ler(it), g) }
        val ms = System.currentTimeMillis() - inicio
        val memoria = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1_000_000 }
        println("LISTA_REAL: filmes=${g.filmes} series=${indice.size} episodios=${indice.sumOf { it.episodios }} aoVivo=${g.aoVivo} em ${ms}ms, memoria ~${memoria}MB")
    }

    @Test
    fun `capa da serie - a do 1o episodio que tiver, na versao pequena, e volta pelo indice`() {
        val eps = sequenceOf(
            ep("Dark S01E01"),
            c("Dark S01E02", "http://s.com/series/u/p/2.mp4", "SÉRIES | X").copy(logo = "https://image.tmdb.org/t/p/w600_and_h900_bestv2/d.jpg"),
            c("Dark S01E03", "http://s.com/series/u/p/3.mp4", "SÉRIES | X").copy(logo = "https://image.tmdb.org/t/p/w500/outra.jpg"),
            c("Lost S01E01", "http://s.com/series/u/p/4.mp4", "SÉRIES | X").copy(logo = "http://192.168.0.5/capa.jpg"),
        )
        val indice = Biblioteca.montar(eps, Gravado())
        assertEquals(listOf("https://image.tmdb.org/t/p/w185/d.jpg", ""), indice.map { it.logo })
        assertEquals(indice, Biblioteca.lerIndice(Biblioteca.indice(indice)))
    }

    @Test
    fun `indice antigo (4 colunas, sem capa) continua valendo`() {
        assertEquals(listOf(Serie(4, "Dark", "NETFLIX", 26)), Biblioteca.lerIndice("4\tNETFLIX\tDark\t26\n"))
    }

    @Test
    fun `numero do episodio para o cartao`() {
        assertEquals(13, Biblioteca.numero("Gotham S03E13"))
        assertEquals(0, Biblioteca.numero("Especial"))
    }
}
