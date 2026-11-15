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

/** Ultimas series vistas ("Continuar assistindo"): a mais recente na frente. */
object Recentes {
    fun usar(lista: List<String>, chave: String, max: Int = 30) = (listOf(chave) + lista.filter { it != chave }).take(max)

    fun tirar(lista: List<String>, chave: String) = lista.filter { it != chave }
}
