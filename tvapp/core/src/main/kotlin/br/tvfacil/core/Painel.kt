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
    private val aoMudar: (List<Fonte>) -> Unit,
) {
    var fontes = fontes
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
                "$i\t${TesteCanais.dominio(f.url)}\t${if (f.propria) "propria" else "publica"}\n"
            })
            p.metodo == "POST" && p.caminho == "/listas" -> {
                val url = p.corpo.trim()
                if (!Fontes.urlAceita(url)) return texto(400, "endereco nao aceito: use http:// ou https:// da internet")
                if (fontes.none { it.url == url }) mudar(fontes + Fonte(url, propria = true))
                texto(200, "ok")
            }
            p.metodo == "DELETE" && p.caminho.startsWith("/listas/") -> {
                val i = p.caminho.removePrefix("/listas/").toIntOrNull()
                if (i == null || i !in fontes.indices) return texto(404, "lista nao encontrada")
                mudar(fontes.filterIndexed { j, _ -> j != i })
                texto(200, "ok")
            }
            p.metodo == "GET" && p.caminho == "/registro" -> texto(200, registro())
            else -> texto(404, "nao existe")
        }
    }

    private fun mudar(novas: List<Fonte>) {
        fontes = novas
        aoMudar(novas)
    }

    private fun texto(codigo: Int, corpo: String) = Resposta(codigo, "text/plain; charset=utf-8", corpo)

    // Comparacao em tempo constante: nao da pistas do PIN/token pelo tempo de resposta.
    private fun igual(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
