package br.tvfacil.core

import java.net.URI

/** As regras do pipeline/testa_canais.sh (Termux), para o app testar os canais sozinho. */
object TesteCanais {
    // Listas publicas (as mesmas de pipeline/listas.txt). Trocar = versao nova, ate o app par existir.
    val LISTAS = listOf(
        "https://iptv-org.github.io/iptv/countries/br.m3u",
        "https://iptv-org.github.io/iptv/languages/por.m3u",
        "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlists/playlist_brazil.m3u8",
    )

    /** Ordem dos grupos na lista final. */
    val GRUPOS = listOf("Sergipe", "Noticias Brasil", "Emissoras abertas", "Filmes", "Esportes", "Outros")

    private fun r(padrao: String) = Regex(padrao, RegexOption.IGNORE_CASE)
    private val sergipe = r("sergipe|aracaju|atalaia|aperip")
    private val portugalId = r("""\.pt($|@)""")
    private val portugalNome = r("portugal|^rtp")
    private val cazeTv = r("caz[eé] ?tv") // 1080p60: trava no stick (12/11/2026)
    private val camara = r("c[aâ]mara")
    private val esporteGrupo = r("sports")
    private val esporteNome = r("""espn|sportv|esporte|fifa\+|(^|[^a-z])sports?([^a-z]|$)""")
    private val fora = r("religious|shop|legislative")
    private val noticiaGrupo = r("news")
    private val noticiaNome = r("(^|[^a-z])news")
    private val emissoras = r("^(sbt|record( tv)?|band|redetv|tv cultura|(canal )?futura|rede brasil|tv brasil|tv senado|tv gazeta|tve |istv|com brasil|globo)")
    private val filmes = r("movies")
    private val html = r("<html|<!doctype")

    /** Grupo final do canal, ou null = fica fora da lista. A ordem das regras importa (igual ao classifica do pipeline). */
    fun classificar(canal: Canal): String? {
        val nome = canal.nome
        val grupo = canal.grupo
        return when {
            sergipe.containsMatchIn("$nome ${canal.tvgId}") -> "Sergipe"
            portugalId.containsMatchIn(canal.tvgId) || portugalNome.containsMatchIn(nome) -> null
            cazeTv.containsMatchIn(nome) -> null
            camara.containsMatchIn(nome) -> "Emissoras abertas"
            esporteGrupo.containsMatchIn(grupo) || esporteNome.containsMatchIn(nome) -> "Esportes"
            fora.containsMatchIn(grupo) -> null
            grupo.isBlank() || grupo.equals("undefined", ignoreCase = true) -> null
            noticiaGrupo.containsMatchIn(grupo) || noticiaNome.containsMatchIn(nome) -> "Noticias Brasil"
            emissoras.containsMatchIn(nome) -> "Emissoras abertas"
            filmes.containsMatchIn(grupo) -> "Filmes"
            else -> "Outros"
        }
    }

    /** Junta as listas na ordem, sem URL repetida, ate o limite (o stick e fraco). */
    fun juntar(listas: List<List<Canal>>, max: Int = 1000): List<Canal> = listas.flatten().distinctBy { it.url }.take(max)

    /** O canal respondeu de verdade? 2xx, com conteudo, e o comeco nao e uma pagina de erro. */
    fun respostaOk(codigo: Int, inicio: ByteArray): Boolean =
        codigo in 200..299 && inicio.isNotEmpty() &&
            !html.containsMatchIn(String(inicio, 0, minOf(inicio.size, 512), Charsets.ISO_8859_1))

    /** Dos canais que responderam: troca o grupo pelo final, tira os de fora e ordena pelos GRUPOS. */
    fun organizar(funcionando: List<Canal>): List<Canal> =
        funcionando.mapNotNull { c -> classificar(c)?.let { c.copy(grupo = it) } }.sortedBy { GRUPOS.indexOf(it.grupo) }

    /** Lista final: as proprias primeiro, como vieram (sem teste, grupos delas), depois as publicas testadas e organizadas. */
    fun final(proprias: List<Canal>, publicasTestadas: List<Canal>): List<Canal> {
        val minhas = proprias.distinctBy { it.url }.map { it.copy(grupo = Catalogo.grupoDe(it)) }
        val urls = minhas.mapTo(HashSet()) { it.url }
        return minhas + organizar(publicasTestadas.filterNot { it.url in urls })
    }

    fun m3u(canais: List<Canal>): String = buildString {
        appendLine("#EXTM3U")
        for (c in canais) {
            append("#EXTINF:-1")
            if (c.tvgId.isNotEmpty()) append(" tvg-id=\"${c.tvgId}\"")
            if (c.logo.isNotEmpty()) append(" tvg-logo=\"${c.logo}\"")
            if (c.tipo == Tipo.FILME) append(" tipo=\"filme\"")
            appendLine(" group-title=\"${c.grupo}\",${c.nome}")
            if (c.userAgent.isNotEmpty()) appendLine("#EXTVLCOPT:http-user-agent=${c.userAgent}")
            if (c.referrer.isNotEmpty()) appendLine("#EXTVLCOPT:http-referrer=${c.referrer}")
            appendLine(c.url)
        }
    }

    /** So o host: e o que vai para o log (usuario, senha e token ficam de fora). */
    fun dominio(url: String): String = runCatching { URI(url).host }.getOrNull() ?: "?"

    /** As 2 linhas do relatorio, no mesmo formato do relatorio.txt do Termux (para comparar lado a lado). */
    fun resumo(agora: String, lidos: Int, funcionando: Int, lista: List<Canal>, segundos: Long): String {
        val porGrupo = GRUPOS.joinToString("") { g -> "$g=${lista.count { it.grupo == g }}; " }
        return "$agora $funcionando de $lidos canais funcionando, ${funcionando - lista.size} removidos pelos filtros, " +
            "${lista.size} na lista (${segundos / 60}min ${segundos % 60}s)\n" +
            "$agora grupos: $porGrupo"
    }
}
