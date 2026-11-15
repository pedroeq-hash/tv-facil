package br.tvfacil

import android.content.Context
import br.tvfacil.core.Biblioteca
import br.tvfacil.core.Busca
import br.tvfacil.core.Canal
import br.tvfacil.core.Escondidas
import br.tvfacil.core.M3uParser
import br.tvfacil.core.Serie

/**
 * Filmes e series ja lidos (com o indice da busca), guardados no processo: sobrevivem ao fechar e abrir a tela.
 * So a MainActivity mexe, sempre na thread principal (guardar); ler roda em IO e nao altera nada.
 */
object BibliotecaLida {
    class Lida(
        val marca: List<Long>,
        val filmes: List<Canal>,
        val series: List<Serie>,
        val indiceFilmes: List<Pair<Canal, String>>,
        val indiceSeries: List<Pair<Canal, String>>,
    )

    var marca = emptyList<Long>()
        private set
    var filmes = emptyList<Canal>()
        private set
    var series = emptyList<Serie>()
        private set
    var indiceFilmes = emptyList<Pair<Canal, String>>()
        private set
    var indiceSeries = emptyList<Pair<Canal, String>>()
        private set

    /** Lanca excecao se nao der para ler agora: quem chama mantem a leitura anterior e tenta de novo depois. */
    fun ler(c: Context, marca: List<Long>): Lida {
        if (!Arquivos.biblioteca(c).exists()) {
            // Sem pasta: ou nunca teve lista propria (vazio mesmo), ou o TesteJob esta trocando a pasta agora.
            check(filmes.isEmpty() && series.isEmpty()) { "biblioteca sendo trocada" }
            return Lida(marca, emptyList(), emptyList(), emptyList(), emptyList())
        }
        val esconde = Escondidas.filtro(Arquivos.lerEscondidas(c)) // ex.: Brasil Paralelo
        val arquivoFilmes = Arquivos.filmes(c)
        val f = if (!arquivoFilmes.exists()) emptyList() else
            arquivoFilmes.useLines { M3uParser.ler(it).take(Biblioteca.MAX_FILMES).filterNot { f -> esconde(f.grupo) }.toList() }
        val arquivoSeries = Arquivos.series(c)
        val s = if (!arquivoSeries.exists()) emptyList() else {
            require(arquivoSeries.length() <= 5_000_000) // o indice da lista do colega tem ~400 KB
            Biblioteca.lerIndice(arquivoSeries.readText()).take(Biblioteca.MAX_SERIES).filterNot { esconde(it.categoria) }
        }
        return Lida(marca, f, s, Busca.indice(f), Busca.indice(s.map { it.comoCanal() }))
    }

    fun guardar(l: Lida) {
        marca = l.marca
        filmes = l.filmes
        series = l.series
        indiceFilmes = l.indiceFilmes
        indiceSeries = l.indiceSeries
    }
}
