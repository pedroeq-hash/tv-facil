package br.tvfacil.core

object M3uParser {

    private val etiqueta = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(texto: String): List<Canal> {
        val canais = mutableListOf<Canal>()
        var atual: Canal? = null
        for (bruta in texto.lines()) {
            val linha = bruta.trim()
            when {
                linha.isEmpty() -> Unit
                linha.startsWith("#EXTINF") -> atual = lerExtinf(linha)
                linha.startsWith("#EXTVLCOPT:http-user-agent=") ->
                    atual = atual?.copy(userAgent = linha.substringAfter("="))
                linha.startsWith("#EXTVLCOPT:http-referrer=") ->
                    atual = atual?.copy(referrer = linha.substringAfter("="))
                linha.startsWith("#") -> Unit
                atual != null -> {
                    canais += atual.copy(url = linha)
                    atual = null
                }
            }
        }
        return canais
    }

    private fun lerExtinf(linha: String): Canal {
        // A primeira virgula FORA de aspas separa as etiquetas do nome.
        var dentroDeAspas = false
        var corte = -1
        for ((i, c) in linha.withIndex()) {
            if (c == '"') dentroDeAspas = !dentroDeAspas
            else if (c == ',' && !dentroDeAspas) { corte = i; break }
        }
        val cabecalho = if (corte >= 0) linha.substring(0, corte) else linha
        val nome = if (corte >= 0) linha.substring(corte + 1).trim() else ""
        val tags = etiqueta.findAll(cabecalho).associate { it.groupValues[1] to it.groupValues[2] }
        return Canal(
            nome = nome,
            url = "",
            grupo = tags["group-title"] ?: "",
            logo = tags["tvg-logo"] ?: "",
            tipo = if (tags["tipo"] == "filme") Tipo.FILME else Tipo.AO_VIVO,
        )
    }
}
