package br.tvfacil

import android.content.Context
import br.tvfacil.core.Biblioteca
import br.tvfacil.core.Busca
import br.tvfacil.core.Canal
import br.tvfacil.core.Capa
import br.tvfacil.core.Escondidas
import br.tvfacil.core.Item
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
        val daBiblioteca: Boolean, // leu a pasta biblioteca/ (e nao so os filmes avulsos)
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
    private var daBiblioteca = false

    /** Lanca excecao se nao der para ler agora: quem chama mantem a leitura anterior e tenta de novo depois. */
    fun ler(c: Context, marca: List<Long>): Lida {
        val esconde = Escondidas.filtro(Arquivos.lerEscondidas(c)) // ex.: Brasil Paralelo
        // Filmes avulsos (pagina: "um por vez" ou arquivo); os canais ao vivo avulsos vao para a lista de canais (TesteJob).
        val avulsos = TesteJob.lerAvulsos(c).mapNotNull { a ->
            when (val item = Biblioteca.classificar(a)) {
                is Item.Filme -> item.canal
                is Item.Episodio -> item.canal
                is Item.AoVivo -> null
            }?.let { it.copy(logo = Capa.endereco(it.logo) ?: "") }
        }.filterNot { esconde(it.grupo) }
        val existe = Arquivos.biblioteca(c).exists()
        // Sem pasta: ou nunca teve lista propria, ou o TesteJob esta trocando a pasta agora (ai fica a leitura anterior).
        check(existe || !daBiblioteca) { "biblioteca sendo trocada" }
        val arquivoFilmes = Arquivos.filmes(c)
        val f = if (!arquivoFilmes.exists()) emptyList() else
            arquivoFilmes.useLines { M3uParser.ler(it).take(Biblioteca.MAX_FILMES).filterNot { f -> esconde(f.grupo) }.toList() }
        val arquivoSeries = Arquivos.series(c)
        val s = if (!arquivoSeries.exists()) emptyList() else {
            require(arquivoSeries.length() <= 5_000_000) // o indice da lista do colega tem ~400 KB
            Biblioteca.lerIndice(arquivoSeries.readText()).take(Biblioteca.MAX_SERIES).filterNot { esconde(it.categoria) }
        }
        val todos = avulsos + f
        return Lida(marca, todos, s, Busca.indice(todos), Busca.indice(s.map { it.comoCanal() }), existe)
    }

    fun guardar(l: Lida) {
        marca = l.marca
        filmes = l.filmes
        series = l.series
        indiceFilmes = l.indiceFilmes
        indiceSeries = l.indiceSeries
        daBiblioteca = l.daBiblioteca
    }
}
