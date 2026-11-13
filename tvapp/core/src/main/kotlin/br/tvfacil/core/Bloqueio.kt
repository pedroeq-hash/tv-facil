package br.tvfacil.core

/**
 * Canal que derrubou o app fica fora da lista por 6 h (o mesmo ciclo do teste do Termux).
 * A queda da libVLC e intermitente: depois desse tempo o canal volta a ser tentado.
 * Cada registro e "hora_em_ms url".
 */
object Bloqueio {
    const val DURACAO_MS = 6 * 60 * 60 * 1000L

    fun registro(url: String, agora: Long) = "$agora $url"

    /** Marca gravada ao abrir um canal: "numero_da_ligacao url". */
    fun marca(url: String, boot: Int) = "$boot $url"

    /** URL do canal que derrubou o app, ou null se a marca e de outra ligacao do stick (reinicio, falta de luz). */
    fun queda(marca: String?, bootAtual: Int): String? =
        marca?.takeIf { it.substringBefore(' ').toIntOrNull() == bootAtual }?.substringAfter(' ')

    /** So os registros ainda dentro das 6 h. Sem hora (formato antigo) ou relogio atrasado: libera. */
    fun validos(registros: Set<String>, agora: Long): MutableSet<String> = registros.filterTo(mutableSetOf()) {
        val desde = it.substringBefore(' ').toLongOrNull() ?: return@filterTo false
        agora - desde in 0 until DURACAO_MS
    }

    fun urls(registros: Set<String>) = registros.mapTo(mutableSetOf()) { it.substringAfter(' ') }
}
