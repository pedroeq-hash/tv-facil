package br.tvfacil.core

import java.net.URI

/**
 * Uma lista M3U de onde vem os canais. propria = do usuario (pode ter senha): entra sem teste e com os grupos dela.
 * nome: dado pelo usuario na pagina de configuracao (vazio = a pagina mostra so o site).
 */
data class Fonte(val url: String, val propria: Boolean, val nome: String = "", val aoVivo: Boolean = true) // aoVivo=false: so filmes e series

/**
 * As listas guardadas no app (arquivo privado), uma por linha: "T url" = publica testada, "P url" = propria, "V url" = propria so com filmes e series,
 * seguidas de tab + nome, se houver (a URL nunca tem tab: o formato recusa espacos).
 */
object Fontes {
    val PADRAO = TesteCanais.LISTAS.map { Fonte(it, propria = false) }
    private const val MAX_NOME = 40
    private val invisiveis = Regex("""[\p{Cc}\p{Cf}]""")

    fun ler(texto: String?): List<Fonte> = texto?.lines()?.mapNotNull { linha ->
        val url = linha.drop(2).substringBefore('\t').trim()
        val nome = nome(linha.substringAfter('\t', ""))
        when {
            !urlAceita(url) -> null
            linha.startsWith("T ") -> Fonte(url, propria = false, nome)
            linha.startsWith("P ") -> Fonte(url, propria = true, nome)
            linha.startsWith("V ") -> Fonte(url, propria = true, nome, aoVivo = false) // propria, sem os canais ao vivo
            else -> null
        }
    } ?: PADRAO

    fun escrever(fontes: List<Fonte>) = fontes.joinToString("") {
        "${if (!it.propria) "T" else if (it.aoVivo) "P" else "V"} ${it.url}${if (it.nome.isEmpty()) "" else "\t" + nome(it.nome)}\n"
    }

    /** Nome vindo da pagina (nao confiavel): sem caracteres de controle/invisiveis (inclui tab e quebra de linha), ate 40. */
    fun nome(texto: String) = texto.replace(invisiveis, "").trim().take(MAX_NOME).trim()

    private val formato = Regex("""^https?://[^\s\p{Cc}\p{Cf}]+$""", RegexOption.IGNORE_CASE)

    /** Endereco de lista que a TV pode baixar: so http/https da internet (nada da rede local nem do proprio stick). */
    fun urlAceita(url: String): Boolean {
        if (url.length > 2048 || !formato.matches(url)) return false
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
        if (host.startsWith("[")) return false // IPv6 escrito direto: recusado (shortcut: liberar se alguem precisar)
        if (host == "localhost" || host.endsWith(".localhost")) return false
        // So numeros/hex (ex.: 2130706433, 0x7f000001, 127.1): o Java aceitaria como IP; so vale o formato a.b.c.d
        if (host.all { it.isDigit() || it == '.' } || host.startsWith("0x")) return ipPublico(host)
        return true // shortcut: nome que o DNS resolve para IP privado passa; conferir no DNS se a pagina ganhar mais poder
    }

    private fun ipPublico(host: String): Boolean {
        val p = host.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it !in 0..255 }) return false
        val (a, b) = p
        return !(a == 0 || a == 10 || a == 127 || (a == 100 && b in 64..127) || (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) || (a == 192 && b == 168))
    }
}
