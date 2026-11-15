package br.tvfacil

import android.content.Context
import br.tvfacil.core.Canal
import br.tvfacil.core.Escondidas
import br.tvfacil.core.Serie
import br.tvfacil.core.Tipo
import java.io.File

/** Filmes e series das listas proprias: montados pelo TesteJob 1 vez por dia, lidos pela MainActivity. Privado (links com senha). */
object Arquivos {
    fun biblioteca(c: Context) = File(c.filesDir, "biblioteca")
    fun filmes(c: Context) = File(biblioteca(c), "filmes.m3u")
    fun series(c: Context) = File(biblioteca(c), "series.tsv")
    fun serie(c: Context, id: Int) = File(biblioteca(c), "series/$id.m3u") // id gerado pelo app, nunca o nome da lista
    fun aoVivo(c: Context) = File(biblioteca(c), "proprias_aovivo.m3u")

    // Categorias escondidas: fora da pasta da biblioteca, que e refeita todo dia.
    fun escondidas(c: Context) = File(c.filesDir, "escondidas.txt")
    fun lerEscondidas(c: Context) = Escondidas.ler(escondidas(c).takeIf { it.exists() }?.readText())
    fun salvarEscondidas(c: Context, categorias: List<String>) {
        val novo = File(c.filesDir, "escondidas.txt.novo")
        novo.writeText(Escondidas.escrever(categorias))
        check(novo.renameTo(escondidas(c))) { "nao consegui salvar as categorias escondidas" }
    }
}

/** A serie como cartao da grade e resultado da busca (url = id da serie). */
fun Serie.comoCanal() = Canal(nome = nome, url = id.toString(), grupo = categoria, tipo = Tipo.SERIE)
