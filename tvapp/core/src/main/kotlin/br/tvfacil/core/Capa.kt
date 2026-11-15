package br.tvfacil.core

/** Capa (tvg-logo) de filme ou serie: vem da lista, entao nao e confiavel. */
object Capa {
    // TMDB serve a mesma imagem em varios tamanhos pelo caminho: 600x900 (~106 KB) -> 185 px (~17 KB).
    private val tmdb = Regex("""^(https://image\.tmdb\.org/t/p/)[^/]+/""")

    /** Endereco que o app pode baixar (mesmas regras das listas: http/https da internet), na versao pequena; ou null. */
    fun endereco(logo: String): String? {
        if (!Fontes.urlAceita(logo)) return null
        return logo.replace(tmdb, "$1w185/")
    }
}

/** "Continuar assistindo": o que ficar 30 dias sem ser aberto sai sozinho. */
const val VALIDADE_MS = 30L * 24 * 60 * 60 * 1000

/** Serie vista (chave = categoria + nome) e quando foi aberta pela ultima vez. */
data class Recente(val chave: String, val quando: Long)

/** Ultimas series vistas: a mais recente na frente. */
object Recentes {
    fun usar(lista: List<Recente>, chave: String, agora: Long, max: Int = 30) =
        (listOf(Recente(chave, agora)) + lista.filter { it.chave != chave }).take(max)

    fun tirar(lista: List<Recente>, chave: String) = lista.filter { it.chave != chave }

    fun validas(lista: List<Recente>, agora: Long) = lista.filter { agora - it.quando <= VALIDADE_MS }

    /** Uma por linha: "chave\tquando". Linha antiga (antes da 0.33, sem hora) conta a partir de agora. */
    fun ler(texto: String, agora: Long) = texto.lines().filter { it.isNotBlank() }.map { linha ->
        Recente(linha.substringBefore('\t'), linha.substringAfter('\t', "").toLongOrNull() ?: agora)
    }

    fun escrever(lista: List<Recente>) = lista.joinToString("\n") { "${it.chave}\t${it.quando}" }
}

/** Onde o filme ou episodio parou ("pos:duracao:quando", em ms). */
data class Posicao(val pos: Long, val duracao: Long, val quando: Long) {
    fun texto() = "$pos:$duracao:$quando"
    fun vencida(agora: Long) = agora - quando > VALIDADE_MS

    companion object {
        /** Formato antigo "pos:duracao" (antes da 0.33) conta a partir de agora; texto estragado = null. */
        fun ler(texto: String, agora: Long): Posicao? {
            val p = texto.split(':').map { it.toLongOrNull() ?: return null }
            return when (p.size) {
                2 -> Posicao(p[0], p[1], agora)
                3 -> Posicao(p[0], p[1], p[2])
                else -> null
            }
        }
    }
}
