package br.tvfacil.core

data class Grupo(val nome: String, val canais: List<Canal>)

object Catalogo {
    const val FAVORITOS = "★ Favoritos"

    fun grupoDe(canal: Canal) = canal.grupo.ifBlank { "Outros" }

    /** Favoritos primeiro (se houver); depois os grupos na ordem da lista (a ordem vem do testa_canais.sh). */
    fun grupos(canais: List<Canal>, favoritos: Set<String>): List<Grupo> {
        val resto = canais.groupBy(::grupoDe).map { (nome, cs) -> Grupo(nome, cs) }
        val fav = canais.filter { it.url in favoritos }
        return if (fav.isEmpty()) resto else listOf(Grupo(FAVORITOS, fav)) + resto
    }
}

/** CH ▲▼: canal vizinho dentro do grupo, voltando ao início nas pontas. */
object Zapper {
    fun <T> vizinho(lista: List<T>, atual: T, passo: Int): T? {
        if (lista.isEmpty()) return null
        val i = lista.indexOf(atual)
        return if (i < 0) lista.first() else lista[Math.floorMod(i + passo, lista.size)]
    }
}
