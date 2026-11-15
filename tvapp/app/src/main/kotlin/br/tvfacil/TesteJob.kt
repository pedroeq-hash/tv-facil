package br.tvfacil

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import br.tvfacil.core.Biblioteca
import br.tvfacil.core.Canal
import br.tvfacil.core.Destino
import br.tvfacil.core.Fonte
import br.tvfacil.core.Fontes
import br.tvfacil.core.M3uParser
import br.tvfacil.core.Serie
import br.tvfacil.core.TesteCanais
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
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
            runCatching {
                when (params.jobId) {
                    ID_PROPRIAS -> atualizarProprias(this@TesteJob, sempreTestar = false)
                    ID_PROPRIAS_AGORA -> atualizarProprias(this@TesteJob, sempreTestar = true)
                    else -> rodar(this@TesteJob)
                }
            }
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
        private const val ID_PROPRIAS = 3 // listas proprias (filmes e series): 1 vez por dia
        private const val ID_PROPRIAS_AGORA = 4 // listas mudaram pela pagina
        private const val SEIS_HORAS = 6 * 60 * 60 * 1000L
        private const val UM_DIA = 24 * 60 * 60 * 1000L
        private const val MAX_LISTA_PROPRIA = 200_000_000 // a do colega tem 85 MB
        private const val PARALELO = 4 // como o PARALELO=4 do Termux: nao atrapalha quem assiste

        fun agendar(context: Context) {
            val agenda = context.getSystemService(JobScheduler::class.java)
            for ((id, intervalo) in listOf(ID to SEIS_HORAS, ID_PROPRIAS to UM_DIA)) {
                if (agenda.getPendingJob(id) != null) continue
                agenda.schedule(
                    JobInfo.Builder(id, ComponentName(context, TesteJob::class.java))
                        .setPeriodic(intervalo)
                        .setPersisted(true) // volta sozinho depois de religar
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build(),
                )
            }
            // 1a abertura com lista propria e ainda sem biblioteca (ex.: veio da 0.23): monta ja, sem esperar o dia.
            if (!Arquivos.biblioteca(context).exists() && lerFontes(context).any { it.propria } && agenda.getPendingJob(ID_PROPRIAS_AGORA) == null) {
                atualizarPropriasAgora(context)
            }
        }

        /** Listas mudaram pela pagina: refaz a biblioteca e, no fim, testa os canais (ID 2). */
        fun atualizarPropriasAgora(context: Context) {
            context.getSystemService(JobScheduler::class.java).schedule(
                JobInfo.Builder(ID_PROPRIAS_AGORA, ComponentName(context, TesteJob::class.java))
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

        // Canais avulsos: arquivo .m3u enviado pela pagina, ja filtrado pelo M3uParser (privado: links podem ter senha).
        private fun arquivoAvulsos(context: Context) = File(context.filesDir, "extras.m3u")

        // Relido pelo M3uParser tambem aqui: o arquivo e reescrito por nos, mas o filtro e barato.
        private fun lerAvulsos(context: Context): List<Canal> =
            arquivoAvulsos(context).takeIf { it.exists() }?.let { M3uParser.parse(it.readText()) } ?: emptyList()

        fun contarAvulsos(context: Context) = lerAvulsos(context).size

        /** Lista vazia apaga o arquivo. */
        fun salvarAvulsos(context: Context, canais: List<Canal>) {
            if (canais.isEmpty()) {
                arquivoAvulsos(context).delete()
                return
            }
            val novo = File(context.filesDir, "extras.m3u.novo")
            novo.writeText(TesteCanais.m3u(canais))
            check(novo.renameTo(arquivoAvulsos(context))) { "nao consegui salvar os canais avulsos" }
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
            // Listas proprias: baixadas 1 vez por dia por atualizarProprias; aqui so os canais ao vivo ja separados.
            val fontes = lerFontes(context).filterNot { it.propria }
            val baixadas = fontes.map { fonte ->
                fonte to runCatching {
                    val (codigo, corpo) = abrir(fonte.url, "", "", maxBytes = 5_000_000)
                    check(codigo in 200..299) { "HTTP $codigo" }
                    M3uParser.parse(corpo.toString(Charsets.UTF_8))
                }.getOrElse {
                    // So o tipo do erro: a mensagem pode trazer a URL com senha.
                    log.escrever("${agora()} Falhou ao baixar ${TesteCanais.dominio(fonte.url)} (${it.javaClass.simpleName})")
                    emptyList()
                }
            }
            // Listas proprias entram sem teste: lista paga limita conexoes, e testar centenas de canais pode bloquear a conta.
            // Avulsos primeiro (como o extras.m3u do Termux), depois as listas proprias baixadas.
            val avulsos = lerAvulsos(context)
            val aoVivo = runCatching { M3uParser.parse(Arquivos.aoVivo(context).readText()) }.getOrDefault(emptyList())
            val proprias = avulsos + aoVivo
            val candidatos = TesteCanais.juntar(baixadas.map { it.second })
            val fila = Dispatchers.IO.limitedParallelism(PARALELO)
            val resultados = coroutineScope { candidatos.map { c -> async(fila) { c to testar(c) } }.awaitAll() }
            val funcionando = resultados.filter { it.second == null }.map { it.first } // mantem a ordem das listas
            resultados.forEach { (c, motivo) -> if (motivo != null) log.escrever("  falhou: ${TesteCanais.dominio(c.url)} - $motivo") }

            val final = TesteCanais.final(proprias, funcionando)
            // Nada baixou (ex.: sem internet): nao troca a lista boa por uma so com os avulsos.
            val nadaBaixou = fontes.isNotEmpty() && baixadas.all { it.second.isEmpty() }
            if (final.isEmpty() || nadaBaixou) {
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
            if (avulsos.isNotEmpty()) log.escrever("${agora()}   (entre eles, ${avulsos.size} canais avulsos)")
        }

        /**
         * Filmes, series e canais ao vivo das listas proprias (docs/plano-tvapp.md, "Filmes e series de lista propria").
         * Baixa direto para arquivo e le aos pedacos: a lista do colega tem 85 MB e 316 mil itens, nao cabe na memoria.
         * Monta tudo em biblioteca.novo/ e troca no fim; se uma lista falhar, fica a biblioteca anterior inteira.
         */
        private suspend fun atualizarProprias(context: Context, sempreTestar: Boolean) = trava.withLock {
            val log = registro(context)
            val inicio = System.currentTimeMillis()
            val proprias = lerFontes(context).filter { it.propria }
            log.escrever("${agora()} inicio: listas proprias (${proprias.size})")
            val baixados = proprias.indices.map { File(context.filesDir, "baixando_$it.m3u") }
            try {
                for ((fonte, arquivo) in proprias.zip(baixados)) {
                    val erro = runCatching {
                        val (codigo, _) = abrir(fonte.url, "", "", MAX_LISTA_PROPRIA, arquivo)
                        check(codigo in 200..299) { "HTTP $codigo" }
                    }.exceptionOrNull() ?: continue
                    // So o tipo do erro: a mensagem pode trazer a URL com senha.
                    log.escrever("${agora()} Falhou ao baixar ${TesteCanais.dominio(fonte.url)} (${erro.javaClass.simpleName}) - filmes e series anteriores mantidos")
                    return@withLock
                }
                val novo = File(context.filesDir, "biblioteca.novo")
                novo.deleteRecursively()
                check(File(novo, "series").mkdirs()) { "nao consegui criar a pasta" }
                val aoVivo = mutableListOf<Canal>()
                var filmes = 0
                val contexto = currentCoroutineContext()
                val linhas = sequence { for (a in baixados) a.bufferedReader().use { yieldAll(it.lineSequence()) } }
                    .onEachIndexed { i, _ -> if (i % 1000 == 0) contexto.ensureActive() } // o Android cortou o job: para
                val indice: List<Serie> = File(novo, "filmes.m3u").bufferedWriter().use { saida ->
                    saida.write("#EXTM3U\n")
                    // Sem logo: ainda nao ha capas, e poupa memoria ao ler os 30 mil filmes.
                    fun semCabecalho(cs: List<Canal>) = TesteCanais.m3u(cs.map { it.copy(logo = "") }).removePrefix("#EXTM3U\n")
                    Biblioteca.montar(M3uParser.ler(linhas), object : Destino {
                        override fun aoVivo(c: Canal) { aoVivo += c }
                        override fun filme(c: Canal) { saida.write(semCabecalho(listOf(c))); filmes++ }
                        override fun episodios(serie: Serie, eps: List<Canal>) = File(novo, "series/${serie.id}.m3u").appendText(semCabecalho(eps))
                    })
                }
                File(novo, "series.tsv").writeText(Biblioteca.indice(indice))
                val textoAoVivo = TesteCanais.m3u(aoVivo)
                File(novo, "proprias_aovivo.m3u").writeText(textoAoVivo)

                // Troca: quem le nunca pega a biblioteca pela metade.
                val antes = runCatching { Arquivos.aoVivo(context).readText() }.getOrDefault("")
                val atual = Arquivos.biblioteca(context)
                val velha = File(context.filesDir, "biblioteca.velha")
                velha.deleteRecursively()
                if (atual.exists()) check(atual.renameTo(velha)) { "nao consegui trocar a biblioteca" }
                check(novo.renameTo(atual)) { "nao consegui trocar a biblioteca" }
                velha.deleteRecursively()

                val segundos = (System.currentTimeMillis() - inicio) / 1000
                log.escrever(
                    "${agora()} listas proprias: $filmes filmes, ${indice.size} series (${indice.sumOf { it.episodios }} episodios), " +
                        "${aoVivo.size} canais ao vivo (${segundos / 60}min ${segundos % 60}s)",
                )
                if (sempreTestar || textoAoVivo != antes) rodarAgora(context) // os canais ao vivo entram no teste de canais
            } finally {
                baixados.forEach { it.delete() }
            }
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

        /**
         * Codigo HTTP + comeco da resposta. Segue ate 5 redirecionamentos, so para http/https.
         * Com arquivo: grava a resposta inteira (2xx) nele, ate maxBytes; passou disso, IOException.
         */
        private fun abrir(endereco: String, userAgent: String, referrer: String, maxBytes: Int, arquivo: File? = null): Pair<Int, ByteArray> {
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
                    if (arquivo != null) {
                        if (codigo in 200..299) con.inputStream.use { copiar(it, arquivo, maxBytes) }
                        return codigo to ByteArray(0)
                    }
                    val corpo = (if (codigo < 400) con.inputStream else con.errorStream)?.use { ler(it, maxBytes) }
                    return codigo to (corpo ?: ByteArray(0))
                } finally {
                    con.disconnect()
                }
            }
            return 310 to ByteArray(0) // redirecionamentos demais
        }

        // Aos pedacos, direto para o disco. A contagem e do que chega ja descompactado (gzip incluso).
        private fun copiar(entrada: InputStream, arquivo: File, max: Int) {
            arquivo.outputStream().buffered().use { saida ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val lidos = entrada.read(buf)
                    if (lidos < 0) break
                    total += lidos
                    if (total > max) throw IOException("lista grande demais")
                    saida.write(buf, 0, lidos)
                }
            }
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
