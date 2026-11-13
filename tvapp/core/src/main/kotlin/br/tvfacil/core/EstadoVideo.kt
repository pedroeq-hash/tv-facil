package br.tvfacil.core

enum class Fase { PARADO, ABRINDO, TOCANDO, INDISPONIVEL }

/** O que a area do video mostra. Avisos de outro canal (atrasados) sao ignorados. */
data class EstadoVideo(val fase: Fase = Fase.PARADO, val canal: Canal? = null) {

    fun escolher(novo: Canal) = EstadoVideo(Fase.ABRINDO, novo)

    // Tambem sai de INDISPONIVEL: canal lento que abre depois do tempo esgotado aparece sozinho.
    fun imagem(de: Canal) = if (de == canal && (fase == Fase.ABRINDO || fase == Fase.INDISPONIVEL)) copy(fase = Fase.TOCANDO) else this

    fun falhou(de: Canal) =
        if (de == canal && (fase == Fase.ABRINDO || fase == Fase.TOCANDO)) copy(fase = Fase.INDISPONIVEL) else this

    fun tempoEsgotado(de: Canal) = if (de == canal && fase == Fase.ABRINDO) copy(fase = Fase.INDISPONIVEL) else this
}
