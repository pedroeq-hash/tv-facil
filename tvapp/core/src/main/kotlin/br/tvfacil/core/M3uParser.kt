package br.tvfacil.core

/** A lista vem de fora (sites publicos, no futuro o note): tudo que entra e filtrado aqui. */
object M3uParser {

    private val etiqueta = Regex("""([\w-]+)="([^"]*)"""")

    // Controle e formato: inclui os invisiveis e os que invertem a direcao do texto (U+202E etc.).
    private val invisiveis = Regex("""[\p{Cc}\p{Cf}]""")

    // Outros esquemas (file://, mms://, rtsp://...) dao acesso a arquivos do stick ou a modulos do VLC com falhas conhecidas.
    private val urlAceita = Regex("""^https?://[^\s\p{Cc}\p{Cf}]+$""", RegexOption.IGNORE_CASE)

    private const val MAX_CANAIS = 5000
    private const val MAX_URL = 2048
    private const val MAX_NOME = 80
    private const val MAX_GRUPO = 40
    private const val MAX_OPCAO = 256

    private fun limpa(texto: String, max: Int) = texto.replace(invisiveis, "").trim().take(max)

    // Nome limpo: "GloboNews (1080p)" -> "GloboNews". Tira etiquetas [..] e marcas de qualidade; o resto entre () fica (ex.: ano do filme).
    private const val MARCA = """(\d{3,4}[pi](\d{2})?|[fu]?hd|uhd|4k|sd|h\.?26[45]|hevc)"""
    private val colchetes = Regex("""\[[^\]]*]""")
    private val marcaEntreParenteses = Regex("""\(\s*$MARCA\s*\)""", RegexOption.IGNORE_CASE)
    private val marcasNoFim = Regex("""(\s+$MARCA)+\s*$""", RegexOption.IGNORE_CASE)
    private val espacos = Regex("""\s+""")

    private fun nomeLimpo(nome: String) = nome.replace(colchetes, " ").replace(marcaEntreParenteses, " ")
        .replace(espacos, " ").replace(marcasNoFim, "").trim().ifEmpty { nome }

    private fun aceita(url: String) = url.length <= MAX_URL && urlAceita.matches(url)

    fun parse(texto: String): List<Canal> {
        val canais = mutableListOf<Canal>()
        var atual: Canal? = null
        for (bruta in texto.lines()) {
            if (canais.size >= MAX_CANAIS) break
            val linha = bruta.trim()
            when {
                linha.isEmpty() -> Unit
                linha.startsWith("#EXTINF") -> atual = lerExtinf(linha)
                linha.startsWith("#EXTVLCOPT:http-user-agent=") ->
                    atual = atual?.copy(userAgent = limpa(linha.substringAfter("="), MAX_OPCAO))
                linha.startsWith("#EXTVLCOPT:http-referrer=") ->
                    atual = atual?.copy(referrer = limpa(linha.substringAfter("="), MAX_OPCAO))
                linha.startsWith("#") -> Unit
                atual != null -> {
                    if (aceita(linha)) canais += atual.copy(url = linha)
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
        val nome = if (corte >= 0) linha.substring(corte + 1) else ""
        val tags = etiqueta.findAll(cabecalho).associate { it.groupValues[1] to it.groupValues[2] }
        return Canal(
            nome = nomeLimpo(limpa(nome, 200)).take(MAX_NOME),
            url = "",
            grupo = limpa(tags["group-title"] ?: "", MAX_GRUPO),
            logo = tags["tvg-logo"] ?: "",
            tipo = if (tags["tipo"] == "filme") Tipo.FILME else Tipo.AO_VIVO,
            tvgId = limpa(tags["tvg-id"] ?: "", MAX_NOME),
        )
    }
}
