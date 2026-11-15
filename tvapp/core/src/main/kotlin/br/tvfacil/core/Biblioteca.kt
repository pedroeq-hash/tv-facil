package br.tvfacil.core

sealed interface Item {
    data class AoVivo(val canal: Canal) : Item
    data class Filme(val canal: Canal) : Item
    data class Episodio(val serie: String, val temporada: Int, val numero: Int, val canal: Canal) : Item
}

/** id = nome do arquivo dos episodios (series/<id>.m3u): gerado pelo app, nunca vem da lista. */
data class Serie(val id: Int, val nome: String, val categoria: String, val episodios: Int)

/** Quem grava (no app: arquivos em filesDir). */
interface Destino {
    fun aoVivo(c: Canal)
    fun filme(c: Canal)
    /** Um bloco seguido de episodios da serie; o mesmo id de novo = acrescentar ao que ja foi gravado. */
    fun episodios(serie: Serie, eps: List<Canal>)
}

/** Filmes e series das listas proprias (as publicas continuam so canais). */
object Biblioteca {
    /** Le a lista aos pedacos e manda cada item para o destino; devolve o indice das series. */
    fun montar(canais: Sequence<Canal>, destino: Destino): List<Serie> {
        val ids = HashMap<Pair<String, String>, Int>() // (categoria, nome) -> id
        val series = mutableListOf<Serie>()
        var aoVivo = 0
        var filmes = 0
        var blocoId = -1
        val bloco = mutableListOf<Canal>()
        fun fechar() {
            if (bloco.isNotEmpty()) destino.episodios(series[blocoId], bloco.toList())
            bloco.clear()
        }
        for (c in canais) when (val item = classificar(c)) {
            is Item.AoVivo -> if (aoVivo < MAX_AO_VIVO) { aoVivo++; destino.aoVivo(item.canal) }
            is Item.Filme -> if (filmes < MAX_FILMES) { filmes++; destino.filme(item.canal) }
            is Item.Episodio -> {
                val chave = item.canal.grupo to item.serie
                val id = ids[chave] ?: if (series.size < MAX_SERIES) {
                    series.size.also { ids[chave] = it; series += Serie(it, item.serie, item.canal.grupo, 0) }
                } else continue
                if (series[id].episodios >= MAX_EPISODIOS) continue
                if (id != blocoId) { fechar(); blocoId = id }
                bloco += item.canal
                series[id] = series[id].copy(episodios = series[id].episodios + 1)
            }
        }
        fechar()
        return series
    }

    // Limites de disco e memoria (docs/plano-tvapp.md, "Filmes e series de lista propria").
    const val MAX_FILMES = 60_000
    const val MAX_SERIES = 20_000
    const val MAX_EPISODIOS = 5_000 // por serie; a maior da lista do colega tem 1.081
    const val MAX_AO_VIVO = 5_000

    private val extensaoVod = Regex("""\.(mp4|mkv|avi|mov|m4v|webm)$""", RegexOption.IGNORE_CASE)
    private val numeracao = Regex("""^(.*?)[\s._-]*\bS(\d{1,3})\s*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)
    private val prefixo = Regex("""^\s*(filmes?|s[eéEÉ]ries?|vod|movies?)\s*\|\s*""", RegexOption.IGNORE_CASE)

    /** "FILMES | Drama" -> "Drama"; "SÉRIES | NETFLIX" -> "NETFLIX". */
    fun categoria(grupo: String) = grupo.replace(prefixo, "").trim().ifEmpty { "Outros" }

    fun classificar(c: Canal): Item {
        val caminho = c.url.substringBefore('?').substringBefore('#')
        val emSeries = "/series/" in caminho
        val vod = emSeries || "/movie/" in caminho || c.tipo == Tipo.FILME || extensaoVod.containsMatchIn(caminho)
        if (!vod) return Item.AoVivo(c)
        val filme = c.copy(grupo = categoria(c.grupo), tipo = Tipo.FILME)
        val m = numeracao.find(c.nome)
        return when {
            m != null -> Item.Episodio(m.groupValues[1].trim().ifEmpty { c.nome }, m.groupValues[2].toInt(), m.groupValues[3].toInt(), filme)
            emSeries -> Item.Episodio(c.nome, 1, 0, filme)
            else -> Item.Filme(filme)
        }
    }

    fun numero(nome: String) = numeracao.find(nome)?.groupValues?.get(3)?.toInt() ?: 0
    private fun temporada(nome: String) = numeracao.find(nome)?.groupValues?.get(2)?.toInt() ?: 1

    fun temporadas(episodios: List<Canal>): List<Grupo> =
        episodios.groupBy { temporada(it.nome) }.toSortedMap().map { (t, eps) -> Grupo("Temporada $t", eps.sortedBy { numero(it.nome) }) }

    fun indice(series: List<Serie>) = series.joinToString("") { "${it.id}\t${it.categoria}\t${it.nome}\t${it.episodios}\n" }

    fun lerIndice(texto: String): List<Serie> = texto.lineSequence().mapNotNull { linha ->
        val p = linha.split('\t')
        val id = p.getOrNull(0)?.toIntOrNull()
        val episodios = p.getOrNull(3)?.toIntOrNull()
        if (p.size != 4 || id == null || id < 0 || episodios == null) null else Serie(id, p[2], p[1], episodios)
    }.toList()
}

/** "Continuar de onde parou": so depois de 1 min; a partir de 95% conta como visto e recomeca. */
object Retomar {
    fun inicio(posMs: Long, duracaoMs: Long): Long =
        if (posMs >= 60_000 && (duracaoMs <= 0 || posMs < duracaoMs * 95 / 100)) posMs else 0
}

/**
 * Segurar ◀▶ no filme: para onde pular, pelo tempo segurado. Cada vez mais rapido:
 * ate 2 s, 30 s de filme por segundo; de 2 a 5 s, 2 min por segundo; depois, 5 min por segundo.
 */
object Pulo {
    fun alvo(posMs: Long, duracaoMs: Long, frente: Boolean, seguradoMs: Long): Long {
        val s = seguradoMs.coerceAtLeast(0)
        val passo = when {
            s <= 2_000 -> s * 30
            s <= 5_000 -> 60_000 + (s - 2_000) * 120
            else -> 60_000 + 360_000 + (s - 5_000) * 300
        }
        val fim = if (duracaoMs > 0) (duracaoMs - 5_000).coerceAtLeast(0) else Long.MAX_VALUE
        return (if (frente) posMs + passo else posMs - passo).coerceIn(0, fim)
    }
}
