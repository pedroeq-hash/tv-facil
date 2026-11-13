package br.tvfacil.core

import java.text.Normalizer

/** Busca pelo nome do canal, sem acento, maiúsculas nem espaços ("globo news" acha "GloboNews"). */
object Busca {
    private val ACENTO = Regex("\\p{M}")

    private fun normalizar(texto: String) =
        Normalizer.normalize(texto, Normalizer.Form.NFD).replace(ACENTO, "").lowercase().filter(Char::isLetterOrDigit)

    /** Os nomes que começam com o termo vêm primeiro; o resto mantém a ordem da lista. */
    fun filtrar(canais: List<Canal>, termo: String): List<Canal> {
        val t = normalizar(termo)
        if (t.isEmpty()) return emptyList()
        return canais.map { it to normalizar(it.nome) }
            .filter { (_, nome) -> t in nome }
            .sortedBy { (_, nome) -> !nome.startsWith(t) }
            .map { it.first }
    }
}
