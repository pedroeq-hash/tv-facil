package br.tvfacil.core

import java.security.MessageDigest

data class Resposta(val codigo: Int, val tipo: String, val corpo: String)

/**
 * A pagina de configuracao servida pela TV (so enquanto a tela "Configurar pelo celular" esta aberta).
 * Regras de seguranca: docs/plano-tvapp.md, Fase 7, "App par como pagina servida pela propria TV".
 */
class Painel(
    private val endereco: String, // "IP:porta" da TV: o unico Host aceito (contra DNS rebinding)
    private val pin: String,
    private val token: String,
    fontes: List<Fonte>,
    private val pagina: String,
    private val registro: () -> String,
    avulsos: List<Canal>, // canais e filmes avulsos que a TV ja tem
    private val aoMudarAvulsos: (List<Canal>) -> Unit,
    private val categorias: () -> List<String> = { emptyList() }, // de filmes e series (lidas so quando a pagina pede)
    escondidas: List<String> = Escondidas.PADRAO,
    private val aoMudarEscondidas: (List<String>) -> Unit = {},
    private val aoMudar: (List<Fonte>) -> Unit,
) {
    var fontes = fontes
        private set
    private var escondidas = escondidas
    var avulsos = avulsos
        private set
    var pareado = false
        private set
    private var erros = 0
    val bloqueado get() = erros >= 5

    fun responder(p: Pedido): Resposta {
        if (bloqueado) return texto(403, "bloqueado: feche e abra a tela na TV")
        if (p.cabecalhos["host"] != endereco) return texto(403, "endereco errado")
        val origem = p.cabecalhos["origin"]
        if (origem != null && origem != "http://$endereco") return texto(403, "outro site")
        if (p.metodo == "GET" && p.caminho == "/") return Resposta(200, "text/html; charset=utf-8", pagina)
        if (p.metodo == "POST" && p.caminho == "/parear") {
            if (!igual(p.corpo.trim(), pin)) {
                erros++
                return texto(401, "PIN errado")
            }
            pareado = true
            return texto(200, token)
        }
        if (!igual(p.cabecalhos["x-token"] ?: "", token)) return texto(401, "digite o PIN de novo")
        return when {
            p.metodo == "GET" && p.caminho == "/listas" -> texto(200, fontes.withIndex().joinToString("") { (i, f) ->
                "$i\t${TesteCanais.dominio(f.url)}\t${if (f.propria) "propria" else "publica"}\t${f.nome}\n"
            })
            // Corpo: URL na 1a linha, nome (opcional) na 2a.
            p.metodo == "POST" && p.caminho == "/listas" -> {
                val url = p.corpo.lineSequence().first().trim()
                if (!Fontes.urlAceita(url)) return texto(400, "endereco nao aceito: use http:// ou https:// da internet")
                val nome = Fontes.nome(p.corpo.lines().getOrElse(1) { "" })
                if (fontes.none { it.url == url }) mudar(fontes + Fonte(url, propria = true, nome))
                texto(200, "ok")
            }
            p.metodo == "PUT" && p.caminho.startsWith("/listas/") -> {
                val i = p.caminho.removePrefix("/listas/").toIntOrNull()
                if (i == null || i !in fontes.indices) return texto(404, "lista nao encontrada")
                mudarNome(i, Fontes.nome(p.corpo))
                texto(200, "ok")
            }
            p.metodo == "DELETE" && p.caminho.startsWith("/listas/") -> {
                val i = p.caminho.removePrefix("/listas/").toIntOrNull()
                if (i == null || i !in fontes.indices) return texto(404, "lista nao encontrada")
                mudar(fontes.filterIndexed { j, _ -> j != i })
                texto(200, "ok")
            }
            // "categoria\t1" = escondida; inclui as escondidas que nao estao mais na lista (para poder mostrar de novo).
            p.metodo == "GET" && p.caminho == "/categorias" -> {
                val esconde = Escondidas.filtro(escondidas)
                val todas = (categorias() + escondidas).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
                texto(200, todas.joinToString("") { "$it\t${if (esconde(it)) 1 else 0}\n" })
            }
            p.metodo == "PUT" && p.caminho == "/escondidas" -> {
                escondidas = Escondidas.ler(p.corpo)
                aoMudarEscondidas(escondidas)
                texto(200, "ok")
            }
            p.metodo == "GET" && p.caminho == "/registro" -> texto(200, registro())
            p.metodo == "GET" && p.caminho == "/resumo" -> texto(200, Resumo.simples(registro()).joinToString("\n"))
            // Endereco completo de uma lista (pode ter senha): so pela pagina, depois do PIN, para quem ja pode troca-la.
            p.metodo == "GET" && p.caminho.startsWith("/listas/") && p.caminho.endsWith("/endereco") -> {
                val i = p.caminho.removePrefix("/listas/").removeSuffix("/endereco").toIntOrNull()
                if (i == null || i !in fontes.indices) return texto(404, "lista nao encontrada")
                texto(200, fontes[i].url)
            }
            // Avulsos: canais e filmes que entram sem lista. Volta so nome, dominio, tipo e categoria (o link pode ter senha).
            p.metodo == "GET" && p.caminho == "/avulsos" -> texto(200, avulsos.withIndex().joinToString("") { (i, c) ->
                "$i\t${c.nome}\t${TesteCanais.dominio(c.url)}\t${if (c.tipo == Tipo.FILME) "filme" else "canal"}\t${c.grupo}\n"
            })
            p.metodo == "POST" && p.caminho == "/avulsos" -> {
                val canais = M3uParser.parse(p.corpo)
                if (canais.isEmpty()) return texto(400, "nenhum canal valido no arquivo (so links http:// ou https://)")
                val novos = canais.distinctBy { it.url }.filter { c -> avulsos.none { it.url == c.url } }
                if (avulsos.size + novos.size > MAX_AVULSOS) return texto(400, "passaria de $MAX_AVULSOS avulsos: apague alguns antes")
                mudarAvulsos(avulsos + novos)
                texto(200, "${novos.size} acrescentados")
            }
            // Um so: "tipo\nnome\nlink\ncategoria". Vira uma entrada M3U e passa pelo M3uParser (mesma validacao do arquivo).
            p.metodo == "POST" && p.caminho == "/avulsos/um" -> {
                val (tipo, nome, link, categoria) = (p.corpo.lines() + List(4) { "" }).map { it.trim() }
                if (tipo != "canal" && tipo != "filme") return texto(400, "tipo deve ser canal ou filme")
                val padrao = if (tipo == "filme") "Meus filmes" else "Meus canais"
                val grupo = categoria.replace("\"", "").ifBlank { padrao }
                val etiqueta = if (tipo == "filme") " tipo=\"filme\"" else ""
                val canal = M3uParser.parse("#EXTINF:-1$etiqueta group-title=\"$grupo\",$nome\n$link\n").singleOrNull()
                    ?: return texto(400, "link nao aceito: use http:// ou https://")
                if (canal.nome.isBlank()) return texto(400, "falta o nome")
                if (avulsos.any { it.url == canal.url }) return texto(400, "esse link ja esta nos avulsos")
                if (avulsos.size >= MAX_AVULSOS) return texto(400, "ja tem $MAX_AVULSOS avulsos: apague alguns antes")
                mudarAvulsos(avulsos + canal)
                texto(200, "ok")
            }
            p.metodo == "DELETE" && p.caminho.startsWith("/avulsos/") -> {
                val i = p.caminho.removePrefix("/avulsos/").toIntOrNull()
                if (i == null || i !in avulsos.indices) return texto(404, "avulso nao encontrado")
                mudarAvulsos(avulsos.filterIndexed { j, _ -> j != i })
                texto(200, "ok")
            }
            p.metodo == "DELETE" && p.caminho == "/avulsos" -> {
                mudarAvulsos(emptyList())
                texto(200, "ok")
            }
            else -> texto(404, "nao existe")
        }
    }

    private fun mudar(novas: List<Fonte>) {
        fontes = novas
        aoMudar(novas)
    }

    private fun mudarNome(i: Int, nome: String) = mudar(fontes.mapIndexed { j, f -> if (j == i) f.copy(nome = nome) else f })

    private fun mudarAvulsos(canais: List<Canal>) {
        avulsos = canais
        aoMudarAvulsos(canais)
    }

    private fun texto(codigo: Int, corpo: String) = Resposta(codigo, "text/plain; charset=utf-8", corpo)

    // Comparacao em tempo constante: nao da pistas do PIN/token pelo tempo de resposta.
    private fun igual(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val MAX_AVULSOS = 300
    }
}
