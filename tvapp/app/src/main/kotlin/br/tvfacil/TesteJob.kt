package br.tvfacil

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import br.tvfacil.core.Canal
import br.tvfacil.core.Fonte
import br.tvfacil.core.Fontes
import br.tvfacil.core.M3uParser
import br.tvfacil.core.TesteCanais
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Testa os canais a cada 6 h (o mesmo trabalho do testa_canais.sh do Termux) e grava a lista no armazenamento
 * privado do app. Agendado pelo proprio Android: continua depois de religar o stick, sem Termux:Boot.
 * Rodar na hora (teste): adb shell cmd jobscheduler run -f br.tvfacil 1
 */
class TesteJob : JobService() {
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        escopo.launch {
            runCatching { rodar(this@TesteJob) }
                .onFailure { registro(this@TesteJob).escrever("${agora()} ERRO na rodada: ${it.javaClass.simpleName}") }
            jobFinished(params, false)
        }
        return true // continua em segundo plano
    }

    override fun onStopJob(params: JobParameters): Boolean {
        escopo.coroutineContext.cancelChildren() // o Android cortou (tempo ou rede): tenta de novo depois
        return true
    }

    companion object {
        private const val ID = 1
        private const val ID_AGORA = 2 // rodada extra quando as listas mudam pela pagina do celular
        private const val SEIS_HORAS = 6 * 60 * 60 * 1000L
        private const val PARALELO = 4 // como o PARALELO=4 do Termux: nao atrapalha quem assiste

        fun agendar(context: Context) {
            val agenda = context.getSystemService(JobScheduler::class.java)
            if (agenda.getPendingJob(ID) != null) return
            agenda.schedule(
                JobInfo.Builder(ID, ComponentName(context, TesteJob::class.java))
                    .setPeriodic(SEIS_HORAS)
                    .setPersisted(true) // volta sozinho depois de religar
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build(),
            )
        }

        /** Roda uma vez assim que houver rede (listas mudaram pela pagina de configuracao). */
        fun rodarAgora(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(ID_AGORA, ComponentName(context, TesteJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .build(),
            )
        }

        fun lista(context: Context) = File(context.filesDir, "canais_ok.m3u")

        // As listas de onde vem os canais (privado: pode ter senha na URL).
        private fun arquivoFontes(context: Context) = File(context.filesDir, "listas.txt")

        fun lerFontes(context: Context): List<Fonte> = Fontes.ler(arquivoFontes(context).takeIf { it.exists() }?.readText())

        fun salvarFontes(context: Context, fontes: List<Fonte>) {
            val novo = File(context.filesDir, "listas.txt.novo")
            novo.writeText(Fontes.escrever(fontes))
            check(novo.renameTo(arquivoFontes(context))) { "nao consegui salvar as listas" }
        }

        /** Fim do registro, para a pagina de configuracao (so tem dominios). */
        fun fimDoRegistro(context: Context): String = runCatching { arquivoRegistro(context).readText().takeLast(6000) }.getOrDefault("")

        private val trava = Mutex() // duas rodadas (a de 6 h e a "agora") nunca gravam juntas

        private fun agora() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        // Fica em /sdcard/Android/data/br.tvfacil/files/ para dar para ler por adb; so tem dominios, nunca a URL inteira.
        private fun arquivoRegistro(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "registro.txt")
        private fun registro(context: Context) = Registro(arquivoRegistro(context))

        private suspend fun rodar(context: Context) = trava.withLock {
            val log = registro(context)
            val inicio = System.currentTimeMillis()
            log.escrever("${agora()} inicio da rodada")
            val fontes = lerFontes(context)
            val baixadas = fontes.map { fonte ->
                fonte to runCatching {
                    // Lista propria pode ser grande (pacotes com filmes): ate 15 MB; o M3uParser corta em 5000 canais.
                    val (codigo, corpo) = abrir(fonte.url, "", "", maxBytes = if (fonte.propria) 15_000_000 else 5_000_000)
                    check(codigo in 200..299) { "HTTP $codigo" }
                    M3uParser.parse(corpo.toString(Charsets.UTF_8))
                }.getOrElse {
                    // So o tipo do erro: a mensagem pode trazer a URL com senha.
                    log.escrever("${agora()} Falhou ao baixar ${TesteCanais.dominio(fonte.url)} (${it.javaClass.simpleName})")
                    emptyList()
                }
            }
            // Listas proprias entram sem teste: lista paga limita conexoes, e testar centenas de canais pode bloquear a conta.
            val proprias = baixadas.filter { it.first.propria }.flatMap { it.second }
            val candidatos = TesteCanais.juntar(baixadas.filterNot { it.first.propria }.map { it.second })
            val fila = Dispatchers.IO.limitedParallelism(PARALELO)
            val resultados = coroutineScope { candidatos.map { c -> async(fila) { c to testar(c) } }.awaitAll() }
            val funcionando = resultados.filter { it.second == null }.map { it.first } // mantem a ordem das listas
            resultados.forEach { (c, motivo) -> if (motivo != null) log.escrever("  falhou: ${TesteCanais.dominio(c.url)} - $motivo") }

            val final = TesteCanais.final(proprias, funcionando)
            if (final.isEmpty()) { // ex.: sem internet: mantem a lista antiga
                log.escrever("${agora()} ERRO: ${candidatos.size} canais lidos, ${funcionando.size} funcionando, ${final.size} na lista - lista antiga mantida")
                return@withLock
            }
            // Nome provisorio + troca no fim: quem le nunca pega a lista pela metade.
            val novo = File(context.filesDir, "canais_ok.m3u.novo")
            novo.writeText(TesteCanais.m3u(final))
            check(novo.renameTo(lista(context))) { "nao consegui trocar a lista" }
            val segundos = (System.currentTimeMillis() - inicio) / 1000
            val minhas = proprias.distinctBy { it.url }.size
            log.escrever(TesteCanais.resumo(agora(), candidatos.size, funcionando.size, final.drop(minhas), segundos))
            if (minhas > 0) log.escrever("${agora()} + $minhas canais de listas proprias (sem teste)")
        }

        /** null = o canal respondeu; senao, o motivo (vai para o log). */
        private fun testar(canal: Canal): String? = try {
            val (codigo, inicio) = abrir(canal.url, canal.userAgent, canal.referrer, maxBytes = 4096)
            when {
                TesteCanais.respostaOk(codigo, inicio) -> null
                codigo !in 200..299 -> "HTTP $codigo"
                inicio.isEmpty() -> "resposta vazia"
                else -> "pagina HTML"
            }
        } catch (e: SocketTimeoutException) {
            "tempo esgotado"
        } catch (e: Exception) {
            e.javaClass.simpleName // so o tipo: a mensagem pode trazer a URL inteira
        }

        /** Codigo HTTP + comeco da resposta. Segue ate 5 redirecionamentos, so para http/https. */
        private fun abrir(endereco: String, userAgent: String, referrer: String, maxBytes: Int): Pair<Int, ByteArray> {
            var url = URL(endereco)
            repeat(6) {
                val con = url.openConnection() as HttpURLConnection
                try {
                    con.instanceFollowRedirects = false
                    con.connectTimeout = 10_000
                    con.readTimeout = 10_000
                    if (userAgent.isNotEmpty()) con.setRequestProperty("User-Agent", userAgent)
                    if (referrer.isNotEmpty()) con.setRequestProperty("Referer", referrer)
                    val codigo = con.responseCode
                    if (codigo in 300..399) {
                        val destino = con.getHeaderField("Location") ?: return codigo to ByteArray(0)
                        url = URL(url, destino)
                        if (url.protocol != "http" && url.protocol != "https") return codigo to ByteArray(0)
                        return@repeat
                    }
                    val corpo = (if (codigo < 400) con.inputStream else con.errorStream)?.use { ler(it, maxBytes) }
                    return codigo to (corpo ?: ByteArray(0))
                } finally {
                    con.disconnect()
                }
            }
            return 310 to ByteArray(0) // redirecionamentos demais
        }

        // Le so o comeco: uma transmissao ao vivo nao acaba nunca.
        private fun ler(entrada: InputStream, max: Int): ByteArray {
            val buf = ByteArray(max)
            var n = 0
            while (n < max) {
                val lidos = entrada.read(buf, n, max - n)
                if (lidos < 0) break
                n += lidos
            }
            return buf.copyOf(n)
        }
    }
}

/** Log com rodizio: passando de 256 KB, fica so a metade mais nova. */
private class Registro(private val arquivo: File) {
    @Synchronized
    fun escrever(texto: String) {
        if (arquivo.length() > 256_000) arquivo.writeText(arquivo.readText().takeLast(128_000).substringAfter('\n'))
        arquivo.appendText(texto + "\n")
    }
}
