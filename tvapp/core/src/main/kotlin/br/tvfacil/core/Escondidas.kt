package br.tvfacil.core

import java.text.Normalizer

/**
 * Categorias de filmes e series que nao aparecem na TV (escolhidas na pagina de configuracao).
 * Arquivo privado, uma por linha. Sem arquivo = PADRAO; arquivo vazio = nada escondido.
 */
object Escondidas {
    val PADRAO = listOf("BRASIL PARALELO")
    private const val MAX = 200
    private val ACENTO = Regex("\\p{M}")

    /** Texto vem da pagina (nao confiavel): cada nome passa pelo Fontes.nome (sem invisiveis, ate 40). */
    fun ler(texto: String?): List<String> =
        texto?.lines()?.map(Fontes::nome)?.filter { it.isNotEmpty() }?.distinctBy(::chave)?.take(MAX) ?: PADRAO

    fun escrever(categorias: List<String>) = categorias.joinToString("") { "$it\n" }

    /** Monta uma vez e usa para os 30 mil filmes: a categoria inteira, sem maiusculas nem acento. */
    fun filtro(escondidas: List<String>): (String) -> Boolean {
        val chaves = escondidas.mapTo(HashSet(), ::chave)
        return { categoria -> chave(categoria) in chaves }
    }

    private fun chave(texto: String) = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replace(ACENTO, "").lowercase()
}
