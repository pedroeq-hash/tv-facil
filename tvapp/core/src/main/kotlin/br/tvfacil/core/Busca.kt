package br.tvfacil.core

import java.text.Normalizer

/** Busca pelo nome do canal, sem acento, maiúsculas nem espaços ("globo news" acha "GloboNews"). */
object Busca {
    private val ACENTO = Regex("\\p{M}")

    private fun normalizar(texto: String) =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(ACENTO, "").lowercase().filter(Char::isLetterOrDigit)

    /** Os nomes que começam com o termo vêm primeiro; o resto mantém a ordem da lista. */
    fun filtrar(canais: List<Canal>, termo: String): List<Canal> = filtrar(indice(canais), termo, Int.MAX_VALUE)

    /** Nomes normalizados uma vez so: com 40 mil filmes e series, normalizar a cada letra trava o stick. */
    fun indice(canais: List<Canal>): List<Pair<Canal, String>> = canais.map { it to normalizar(it.nome) }

    fun filtrar(indice: List<Pair<Canal, String>>, termo: String, max: Int = 100): List<Canal> {
        val t = normalizar(termo)
        if (t.isEmpty()) return emptyList()
        return indice.filter { (_, nome) -> t in nome }
            .sortedBy { (_, nome) -> !nome.startsWith(t) }
            .take(max)
            .map { it.first }
    }

    /** Busca do menu: canais, series e filmes, ate `cota` de cada (com 30 mil filmes, as series sumiriam). */
    fun global(
        canais: List<Pair<Canal, String>>,
        series: List<Pair<Canal, String>>,
        filmes: List<Pair<Canal, String>>,
        termo: String,
        cota: Int = 30,
    ): List<Canal> = filtrar(canais, termo, cota) + filtrar(series, termo, cota) + filtrar(filmes, termo, cota)
}
