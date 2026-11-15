package br.tvfacil

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.tvfacil.core.Http
import br.tvfacil.core.Painel
import br.tvfacil.core.Resposta
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Servidor da pagina de configuracao. So existe enquanto a tela "Configurar" esta aberta:
 * fechou a tela, fechou a porta. Regras: docs/plano-tvapp.md, Fase 7. A logica (e os testes) ficam no Painel (:core).
 */
class ServidorConfig(private val context: Context, private val categorias: List<String>) {
    val ip: String? = ipDaRede()
    private val sorte = SecureRandom()
    val pin = "%06d".format(sorte.nextInt(1_000_000))
    var status by mutableStateOf("Aguardando o aparelho…")
        private set
    private var porta: ServerSocket? = null

    fun iniciar() {
        val ip = ip ?: return
        val token = ByteArray(32).also(sorte::nextBytes).joinToString("") { "%02x".format(it) }
        val painel = Painel(
            endereco = "$ip:$PORTA",
            pin = pin,
            token = token,
            fontes = TesteJob.lerFontes(context),
            pagina = context.assets.open("config.html").bufferedReader().use { it.readText() },
            registro = { TesteJob.fimDoRegistro(context) },
            avulsos = TesteJob.contarAvulsos(context),
            aoMudarAvulsos = { canais ->
                TesteJob.salvarAvulsos(context, canais)
                TesteJob.rodarAgora(context)
                status = "Canais avulsos salvos. Aparecem em uns 5 minutos."
            },
            categorias = { categorias },
            escondidas = Arquivos.lerEscondidas(context),
            aoMudarEscondidas = { categorias ->
                Arquivos.salvarEscondidas(context, categorias)
                status = "Categorias escondidas salvas. A TV atualiza em até 1 minuto."
            },
        ) { novas ->
            val enderecosAntes = TesteJob.lerFontes(context).map { it.url }
            TesteJob.salvarFontes(context, novas)
            if (novas.map { it.url } == enderecosAntes) { // so mudou o nome: nada a baixar
                status = "Nome da lista salvo."
                return@Painel
            }
            TesteJob.atualizarPropriasAgora(context) // refaz filmes e series e, no fim, testa os canais
            status = "Listas salvas. Canais, filmes e séries novos aparecem em uns 10 minutos."
        }
        thread(name = "config", isDaemon = true) {
            runCatching {
                ServerSocket(PORTA).use { servidor ->
                    porta = servidor
                    while (!servidor.isClosed) {
                        val conexao = runCatching { servidor.accept() }.getOrNull() ?: break
                        conexao.use { atender(it, painel) } // uma conexao por vez
                        if (painel.bloqueado) {
                            status = "Bloqueado: PIN errado 5 vezes. Feche e abra esta tela."
                            break
                        }
                        if (painel.pareado && status.startsWith("Aguardando")) status = "Aparelho conectado."
                    }
                }
            }.onFailure { if (porta?.isClosed != true) status = "Não consegui abrir a configuração (${it.javaClass.simpleName})." }
        }
    }

    fun parar() {
        runCatching { porta?.close() }
    }

    private fun atender(conexao: Socket, painel: Painel) {
        conexao.soTimeout = 10_000 // pedido lento demais: desiste
        val pedido = runCatching { Http.ler(conexao.getInputStream()) }.getOrNull()
        val resposta = pedido?.let(painel::responder) ?: Resposta(400, "text/plain; charset=utf-8", "pedido invalido")
        val corpo = resposta.corpo.toByteArray()
        val cabecalho = "HTTP/1.1 ${resposta.codigo} ${if (resposta.codigo == 200) "OK" else "Erro"}\r\n" +
            "Content-Type: ${resposta.tipo}\r\n" +
            "Content-Length: ${corpo.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            // Nada externo, nada em iframe de outro site; o JS da pagina e embutido.
            "Content-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; " +
            "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Connection: close\r\n\r\n"
        runCatching {
            conexao.getOutputStream().apply {
                write(cabecalho.toByteArray(Charsets.ISO_8859_1))
                write(corpo)
                flush()
            }
        }
    }

    companion object {
        const val PORTA = 8765

        // IPv4 da rede de casa (Wi-Fi ou cabo): e o endereco que aparece na TV.
        private fun ipDaRede(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
        }.getOrNull()
    }
}
