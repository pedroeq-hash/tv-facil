package br.tvfacil

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import br.tvfacil.core.Biblioteca
import br.tvfacil.core.Bloqueio
import br.tvfacil.core.Busca
import br.tvfacil.core.Camada
import br.tvfacil.core.Canal
import br.tvfacil.core.Catalogo
import br.tvfacil.core.Diagnostico
import br.tvfacil.core.Efeito
import br.tvfacil.core.Escondidas
import br.tvfacil.core.EstadoVideo
import br.tvfacil.core.Fase
import br.tvfacil.core.Grupo
import br.tvfacil.core.M3uParser
import br.tvfacil.core.Navegacao
import br.tvfacil.core.Pulo
import br.tvfacil.core.ResultadoTeste
import br.tvfacil.core.Retomar
import br.tvfacil.core.Serie
import br.tvfacil.core.Tipo
import br.tvfacil.core.Zapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val TECLA_VELHA_MS = 700L

class MainActivity : ComponentActivity(), Acoes {
    private lateinit var player: VlcPlayer
    private var canais by mutableStateOf<List<Canal>?>(null) // null = ainda carregando
    private var video by mutableStateOf(EstadoVideo())
    private var faixa by mutableStateOf<String?>(null) // aviso no topo (progresso do diagnostico)
    private var camada by mutableStateOf(Camada.ASSISTINDO)
    private var grupoAtual by mutableStateOf("") // CH ▲▼ anda dentro deste grupo
    private var anterior by mutableStateOf<Canal?>(null)
    private var favoritos by mutableStateOf(emptySet<String>()) // URLs
    private val grupos by derivedStateOf { Catalogo.grupos(canais.orEmpty(), favoritos) }
    private var teclaEngolida = -1
    private var avisoSaida = Long.MIN_VALUE / 2 // hora (uptime) do "aperte de novo para sair"; metade evita estouro na subtracao
    private var termo by mutableStateOf("") // texto da busca
    // Nomes ja normalizados para a busca (Busca.indice), um por tipo: a do menu mostra ate 30 de cada.
    private var indiceCanais by mutableStateOf(emptyList<Pair<Canal, String>>())
    private var indiceFilmes by mutableStateOf(emptyList<Pair<Canal, String>>())
    private var indiceSeries by mutableStateOf(emptyList<Pair<Canal, String>>())
    private var termoGrade by mutableStateOf("") // busca no topo de Filmes / Series

    // Filmes e series (listas proprias; ver Arquivos e TesteJob).
    private var filmes by mutableStateOf(emptyList<Canal>())
    private var series by mutableStateOf(emptyList<Serie>())
    private var emAndamento by mutableStateOf(emptySet<String>()) // URLs com "continuar de onde parou"
    private val gruposFilmes by derivedStateOf {
        val andamento = filmes.filter { it.url in emAndamento }
        (if (andamento.isEmpty()) emptyList() else listOf(Grupo("Continuar assistindo", andamento))) + Catalogo.grupos(filmes, emptySet())
    }
    private val gruposSeries by derivedStateOf { series.groupBy { it.categoria }.map { (cat, ss) -> Grupo(cat, ss.map { it.comoCanal() }) } }
    private var serieAberta by mutableStateOf<Serie?>(null)
    private var temporadas by mutableStateOf(emptyList<Grupo>())
    private var grupoFilmes by mutableStateOf("")
    private var grupoSeries by mutableStateOf("")
    private var temporadaAtual by mutableStateOf("")
    private var ultimoVod by mutableStateOf<Canal?>(null) // foco na grade ao voltar do filme
    private var voltarPara = Camada.FILMES // de onde o filme foi aberto
    private var tempo by mutableStateOf(0L to 0L) // posicao e duracao do filme, em ms
    private var pausado by mutableStateOf(false)
    private var toques by mutableIntStateOf(0) // tecla no filme: mostra a barra
    private var salvoEm = 0L
    // Segurar ◀▶: a barra mostra para onde vai (previa); o pulo e um so, ao soltar (cada pulo recarrega o video).
    private var previa by mutableStateOf<Long?>(null)
    private var baseDoPulo = 0L // posicao depois do toque (−10 s / +30 s) que abriu o segurar
    private val posicoes by lazy { getSharedPreferences("posicoes", MODE_PRIVATE) } // URL -> "pos:duracao"

    // O 1º canal depois de abrir o app demora mais (libVLC e rede "frios"): espera o dobro antes do aviso.
    private var jaTocou by mutableStateOf(false)
    private val esperaMaximaMs get() = if (jaTocou) 15_000L else 30_000L

    private val prefs by lazy { getSharedPreferences("tvfacil", MODE_PRIVATE) }

    // Quantas vezes o stick ja ligou: muda no reinicio/falta de luz, que nao sao queda do app.
    private val boot by lazy { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // protetor de tela nao interrompe o video
        TesteJob.agendar(this) // testa os canais a cada 6 h (ver arquivoLista)
        // Lista nova (teste de 6 h ou lista adicionada pelo celular) aparece sem precisar sair do app.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(60_000)
                    relerSeMudou()
                }
            }
        }
        favoritos = prefs.getStringSet("favoritos", emptySet())!!.toSet()
        player = VlcPlayer(
            this,
            aoTocar = {
                video = video.imagem(it)
                jaTocou = true
            },
            aoFalhar = { video = video.falhou(it) },
            aoTerminar = ::terminou,
            aoTempo = { pos, duracao ->
                tempo = pos to duracao
                if (pos - salvoEm > 30_000 || pos < salvoEm) salvarPosicao()
            },
        )
        emAndamento = posicoes.all.keys.toSet()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = ATKINSON)) {
                    val vod = Vod(
                        gruposFilmes, grupoFilmes, gruposSeries, grupoSeries, serieAberta, temporadas, temporadaAtual,
                        ultimoVod, pausado, toques, { tempo }, previa, termoGrade, indiceFilmes, indiceSeries,
                    )
                    Tela(canais, grupos, grupoAtual, favoritos, video, camada, anterior, faixa, esperaMaximaMs, termo, indiceCanais, vod, player, this)
                }
            }
        }
        val permissao = Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permissao) == PackageManager.PERMISSION_GRANTED) carregar()
        else requestPermissions(arrayOf(permissao), 1)
    }

    // Jeito classico de pedir permissao: a libVLC traz um androidx.fragment antigo, incompativel com registerForActivityResult.
    @Deprecated("API classica do Android; suficiente aqui")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        carregar()
    }

    // Botoes do controle: a regra fica no :core (Navegacao); o que ela nao trata segue para a tela/sistema.
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        // Clique que ficou na fila enquanto o stick engasgava: descarta, senao o app "sai clicando sozinho" depois.
        if (SystemClock.uptimeMillis() - e.eventTime > TECLA_VELHA_MS) return true
        if (camada == Camada.FILME && (e.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) {
                previa = Pulo.alvo(baseDoPulo, tempo.second, e.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT, e.eventTime - e.downTime)
                toques++
                return true
            }
            val alvo = previa
            if (e.action == KeyEvent.ACTION_UP && alvo != null) {
                player.irPara(alvo)
                tempo = alvo to tempo.second
                previa = null
                teclaEngolida = -1
                toques++
                return true
            }
        }
        if (e.action == KeyEvent.ACTION_UP && e.keyCode == teclaEngolida) {
            teclaEngolida = -1
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val tecla = Navegacao.tecla(e.keyCode)
            val reacao = tecla?.let {
                Navegacao.reagir(camada, it, video.fase == Fase.INDISPONIVEL, e.eventTime - avisoSaida, voltarPara)
            }
            if (reacao != null) {
                teclaEngolida = e.keyCode // o "soltar" desta tecla nao pode clicar no item que acabou de ganhar foco
                camada = reacao.camada
                when (reacao.efeito) {
                    Efeito.CANAL_MAIS -> zapear(+1)
                    Efeito.CANAL_MENOS -> zapear(-1)
                    Efeito.AVISAR_SAIDA -> {
                        avisoSaida = e.eventTime
                        Toast.makeText(this, "Aperte Voltar de novo para sair", Toast.LENGTH_LONG).show()
                    }
                    Efeito.PAUSAR -> {
                        pausado = !pausado
                        player.pausar(pausado)
                        salvarPosicao()
                        toques++
                    }
                    Efeito.VOLTAR_10S -> { player.pular(-10_000); baseDoPulo = (tempo.first - 10_000).coerceAtLeast(0); toques++ }
                    Efeito.AVANCAR_30S -> { player.pular(30_000); baseDoPulo = tempo.first + 30_000; toques++ }
                    Efeito.MOSTRAR_BARRA -> toques++
                    Efeito.SAIR_FILME -> sairDoFilme()
                    null -> Unit
                }
                if (camada == Camada.ASSISTINDO) retomarAoVivo()
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun mudarTermo(texto: String) {
        termo = texto
    }

    private fun abrir(canal: Canal, inicioMs: Long = 0) {
        video = video.escolher(canal)
        // Protecao contra queda: alguns streams derrubam a libVLC (erro nativo, nao da para capturar).
        // Se o app reabrir com esta marca ainda gravada, o canal e bloqueado (ver bloquearCanalQueDerrubou).
        // Gravada na fila do player (gravar no disco na thread da tela engasga o stick), antes do play.
        player.tocar(canal, inicioMs) { prefs.edit().putString("abrindo", Bloqueio.marca(canal.url, boot)).commit() }
    }

    private fun parar() = player.parar { prefs.edit().remove("abrindo").commit() } // parou normalmente: nao foi queda

    // shortcut: so vale ao abrir o app; com o app aberto direto por mais de 6 h, o canal volta so na proxima abertura.
    private fun bloquearCanalQueDerrubou(): Set<String> {
        val agora = System.currentTimeMillis()
        val registros = Bloqueio.validos(prefs.getStringSet("bloqueados", emptySet())!!, agora)
        Bloqueio.queda(prefs.getString("abrindo", null), boot)?.let { registros += Bloqueio.registro(it, agora) }
        prefs.edit().remove("abrindo").putStringSet("bloqueados", registros).commit()
        return Bloqueio.urls(registros)
    }

    override fun escolher(canal: Canal, grupo: String, fecharLista: Boolean) {
        if (canal != video.canal && video.canal?.tipo != Tipo.FILME) anterior = video.canal
        grupoAtual = grupo
        if (fecharLista) camada = Camada.ASSISTINDO
        abrir(canal)
        prefs.edit().putString("ultimo_canal", canal.url).apply()
    }

    override fun tempoEsgotado(canal: Canal) {
        video = video.tempoEsgotado(canal)
    }

    override fun zapear(passo: Int) {
        val atual = video.canal ?: return
        val lista = grupos.firstOrNull { it.nome == grupoAtual }?.canais ?: canais.orEmpty()
        Zapper.vizinho(lista, atual, passo)?.let { escolher(it, grupoAtual) }
    }

    override fun tentarDeNovo() {
        video.canal?.let(::abrir)
    }

    override fun mudarTermoGrade(texto: String) {
        termoGrade = texto
    }

    override fun mostrar(camada: Camada) {
        if (camada == Camada.FILMES || camada == Camada.SERIES) termoGrade = "" // aberto pelo menu: busca limpa
        this.camada = camada
        if (camada == Camada.ASSISTINDO) retomarAoVivo()
    }

    // Saiu dos filmes/series para assistir: volta o ultimo canal ao vivo (o filme ja parou).
    private fun retomarAoVivo() {
        if (video.canal?.tipo == Tipo.AO_VIVO) return
        val url = prefs.getString("ultimo_canal", null)
        canais.orEmpty().firstOrNull { it.url == url }?.let { escolher(it, Catalogo.grupoDe(it)) }
    }

    override fun assistirFilme(canal: Canal, voltarPara: Camada, grupo: String) {
        this.voltarPara = voltarPara
        when (voltarPara) {
            Camada.FILMES -> grupoFilmes = grupo
            Camada.SERIE -> {
                temporadaAtual = grupo
                serieAberta?.let { prefs.edit().putString(chaveSerie(it), canal.url).apply() }
            }
            else -> Unit
        }
        ultimoVod = canal
        pausado = false
        previa = null
        tempo = 0L to 0L
        salvoEm = 0
        camada = Camada.FILME
        abrir(canal, inicioSalvo(canal))
    }

    // Nome + categoria: o id da serie muda quando a lista e refeita, o nome nao.
    private fun chaveSerie(s: Serie) = "serie:${s.categoria}|${s.nome}"

    private fun inicioSalvo(c: Canal): Long {
        val partes = posicoes.getString(c.url, null)?.split(':')?.mapNotNull { it.toLongOrNull() }
        return if (partes?.size == 2) Retomar.inicio(partes[0], partes[1]) else 0
    }

    private fun salvarPosicao() {
        val c = video.canal?.takeIf { it.tipo == Tipo.FILME } ?: return
        val (pos, duracao) = tempo
        if (pos <= 0) return
        salvoEm = pos
        if (Retomar.inicio(pos, duracao) > 0) {
            posicoes.edit().putString(c.url, "$pos:$duracao").apply()
            emAndamento = emAndamento + c.url
        } else {
            posicoes.edit().remove(c.url).apply()
            emAndamento = emAndamento - c.url
        }
    }

    private fun sairDoFilme() {
        salvarPosicao()
        parar()
        video = EstadoVideo()
        pausado = false
    }

    // Fim do episodio: passa ao proximo da serie. Fim do filme: volta de onde veio.
    private fun terminou(canal: Canal) {
        if (camada != Camada.FILME || video.canal != canal) return
        tempo = 0L to 0L
        posicoes.edit().remove(canal.url).apply()
        emAndamento = emAndamento - canal.url
        val eps = temporadas.flatMap { it.canais }
        val proximo = if (voltarPara == Camada.SERIE) eps.getOrNull(eps.indexOf(canal) + 1) else null
        if (proximo != null) {
            assistirFilme(proximo, Camada.SERIE, temporadas.first { proximo in it.canais }.nome)
        } else {
            sairDoFilme()
            camada = voltarPara
        }
    }

    override fun abrirSerie(item: Canal, grupo: String) {
        val serie = series.firstOrNull { it.id.toString() == item.url } ?: return
        grupoSeries = grupo
        lifecycleScope.launch {
            val eps = withContext(Dispatchers.IO) {
                runCatching { Arquivos.serie(this@MainActivity, serie.id).useLines { M3uParser.ler(it).take(Biblioteca.MAX_EPISODIOS).toList() } }
                    .getOrDefault(emptyList())
            }.filterNot { it.url in bloqueados }
            temporadas = Biblioteca.temporadas(eps)
            serieAberta = serie
            val ultimo = prefs.getString(chaveSerie(serie), null)
            ultimoVod = eps.firstOrNull { it.url == ultimo }
            temporadaAtual = temporadas.firstOrNull { t -> t.canais.any { it.url == ultimo } }?.nome ?: ""
            camada = Camada.SERIE
        }
    }

    override fun alternarFavorito() {
        val url = video.canal?.url ?: return
        favoritos = if (url in favoritos) favoritos - url else favoritos + url
        prefs.edit().putStringSet("favoritos", favoritos).apply()
        camada = Camada.ASSISTINDO
    }

    override fun voltarAoAnterior() {
        anterior?.let { escolher(it, Catalogo.grupoDe(it)) }
    }

    // Modo diagnostico (so por ADB: am start ... --ez diagnostico true): testa cada canal no player.
    // inicio: continua de onde parou se um canal derrubar o app (o que derrubou ja sai da lista).
    private suspend fun diagnosticar(lista: List<Canal>, inicio: Int) {
        val resultados = mutableListOf<ResultadoTeste>()
        val arquivo = File(getExternalFilesDir(null), "diagnostico_$inicio.txt")
        for ((i, canal) in lista.withIndex()) {
            if (i < inicio) continue
            faixa = "Diagnóstico ${i + 1}/${lista.size} · tocaram ${resultados.count { it.tocou }}"
            val comeco = SystemClock.elapsedRealtime()
            abrir(canal)
            // A Tela marca "indisponivel" em 15 s; os 20 s aqui sao so uma rede de seguranca.
            withTimeoutOrNull(20_000) { snapshotFlow { video }.first { it.canal == canal && it.fase != Fase.ABRINDO } }
            val ms = SystemClock.elapsedRealtime() - comeco
            var tocou = video.canal == canal && video.fase == Fase.TOCANDO
            if (tocou) {
                delay(4_000) // tem que continuar tocando: pega canal que abre e cai logo
                tocou = video.canal == canal && video.fase == Fase.TOCANDO
            }
            resultados += ResultadoTeste(canal, tocou, if (tocou) ms else null)
            withContext(Dispatchers.IO) { arquivo.writeText(Diagnostico.relatorio(resultados)) }
        }
        parar()
        video = EstadoVideo()
        faixa = "Diagnóstico concluído: ${resultados.count { it.tocou }} de ${lista.size} tocaram"
    }

    // A lista do app (tem as listas proprias do usuario). A do Termux (reserva) so entra se a do app
    // nao existir ou ficar velha (teste do app parado): ai vale a mais nova das duas.
    private val arquivoLista: File
        get() {
            val doApp = TesteJob.lista(this)
            if (System.currentTimeMillis() - doApp.lastModified() < 12 * 60 * 60 * 1000L) return doApp
            return listOf(doApp, File(Environment.getExternalStorageDirectory(), "iptv/canais_ok.m3u")).maxBy { it.lastModified() }
        }
    private var listaLidaEm = 0L // data do arquivo lido: o Termux regrava a lista a cada 6 h
    private var bloqueados = emptySet<String>()

    private suspend fun lerLista(): List<Canal> = withContext(Dispatchers.IO) {
        val arquivo = arquivoLista
        listaLidaEm = arquivo.lastModified()
        // shortcut: lista acima de 5 MB (a normal tem ~50 KB) nao e lida e aparece "nao encontrada"; avisar melhor se acontecer.
        runCatching {
            require(arquivo.length() <= 5_000_000)
            M3uParser.parse(arquivo.readText())
        }.getOrDefault(emptyList())
    }.filterNot { it.url in bloqueados }

    private var bibliotecaLidaEm = emptyList<Long>()
    // Inclui as categorias escondidas: mudou na pagina, rele (em ate 1 min).
    private fun marcaBiblioteca() =
        listOf(Arquivos.filmes(this).lastModified(), Arquivos.series(this).lastModified(), Arquivos.escondidas(this).lastModified())

    // shortcut: le os 30 mil filmes de uma vez (~5 MB); se a memoria apertar, ler so os nomes e buscar o resto ao abrir.
    private suspend fun lerBiblioteca() {
        val (f, s) = withContext(Dispatchers.IO) {
            bibliotecaLidaEm = marcaBiblioteca()
            val esconde = Escondidas.filtro(Arquivos.lerEscondidas(this@MainActivity)) // ex.: Brasil Paralelo
            val f = runCatching { Arquivos.filmes(this@MainActivity).useLines { M3uParser.ler(it).take(Biblioteca.MAX_FILMES).toList() } }
                .getOrDefault(emptyList()).filterNot { it.url in bloqueados || esconde(it.grupo) }
            val s = runCatching {
                val arquivo = Arquivos.series(this@MainActivity)
                require(arquivo.length() <= 5_000_000) // o indice da lista do colega tem ~400 KB
                Biblioteca.lerIndice(arquivo.readText()).take(Biblioteca.MAX_SERIES).filterNot { esconde(it.categoria) }
            }.getOrDefault(emptyList())
            f to s
        }
        filmes = f
        series = s
        atualizarBusca()
    }

    private fun atualizarBusca() {
        val (c, f, s) = Triple(canais.orEmpty(), filmes, series.map { it.comoCanal() })
        lifecycleScope.launch {
            withContext(Dispatchers.Default) { Triple(Busca.indice(c), Busca.indice(f), Busca.indice(s)) }.let { (ic, ifi, ise) ->
                indiceCanais = ic
                indiceFilmes = ifi
                indiceSeries = ise
            }
        }
    }

    private fun carregar() {
        bloqueados = bloquearCanalQueDerrubou()
        lifecycleScope.launch {
            val lista = lerLista()
            canais = lista
            launch { lerBiblioteca() } // depois do canal: nao atrasa a abertura
            if (intent.getBooleanExtra("diagnostico", false)) {
                diagnosticar(lista, intent.getIntExtra("inicio", 0))
                return@launch
            }
            if (video.canal != null) return@launch
            // Abre tocando o ultimo canal (fica gravado: vale depois de Voltar e de reiniciar o stick).
            // Primeira vez (nenhum canal ainda): ja abre a lista.
            val ultimo = lista.firstOrNull { it.url == prefs.getString("ultimo_canal", null) }
            if (ultimo != null) escolher(ultimo, Catalogo.grupoDe(ultimo)) else if (lista.isNotEmpty()) camada = Camada.LISTA
        }
    }

    override fun onStart() {
        super.onStart()
        player.prenderTela()
        // Voltou ao app: retoma o ultimo canal (ou o filme, de onde parou).
        video.canal?.let { abrir(it, if (it.tipo == Tipo.FILME) inicioSalvo(it) else 0) }
        pausado = false
        relerSeMudou() // voltou ao app (Home, stick acordando)
    }

    private fun relerSeMudou() {
        if (canais != null && arquivoLista.lastModified() != listaLidaEm) lifecycleScope.launch {
            canais = lerLista()
            atualizarBusca()
        }
        if (canais != null && marcaBiblioteca() != bibliotecaLidaEm) lifecycleScope.launch { lerBiblioteca() }
    }

    override fun onStop() {
        super.onStop()
        salvarPosicao()
        parar()
        player.soltarTela()
    }

    override fun onDestroy() {
        player.liberar()
        super.onDestroy()
    }
}
