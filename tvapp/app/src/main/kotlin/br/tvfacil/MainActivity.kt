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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import br.tvfacil.core.Bloqueio
import br.tvfacil.core.Camada
import br.tvfacil.core.Canal
import br.tvfacil.core.Catalogo
import br.tvfacil.core.Diagnostico
import br.tvfacil.core.Efeito
import br.tvfacil.core.EstadoVideo
import br.tvfacil.core.Fase
import br.tvfacil.core.M3uParser
import br.tvfacil.core.Navegacao
import br.tvfacil.core.ResultadoTeste
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
        favoritos = prefs.getStringSet("favoritos", emptySet())!!.toSet()
        player = VlcPlayer(
            this,
            aoTocar = {
                video = video.imagem(it)
                jaTocou = true
            },
            aoFalhar = { video = video.falhou(it) },
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = ATKINSON)) {
                    Tela(canais, grupos, grupoAtual, favoritos, video, camada, anterior, faixa, esperaMaximaMs, termo, player, this)
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
        if (e.action == KeyEvent.ACTION_UP && e.keyCode == teclaEngolida) {
            teclaEngolida = -1
            return true
        }
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
            val tecla = Navegacao.tecla(e.keyCode)
            val reacao = tecla?.let {
                Navegacao.reagir(camada, it, video.fase == Fase.INDISPONIVEL, e.eventTime - avisoSaida)
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
                    null -> Unit
                }
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun mudarTermo(texto: String) {
        termo = texto
    }

    private fun abrir(canal: Canal) {
        video = video.escolher(canal)
        // Protecao contra queda: alguns streams derrubam a libVLC (erro nativo, nao da para capturar).
        // Se o app reabrir com esta marca ainda gravada, o canal e bloqueado (ver bloquearCanalQueDerrubou).
        // Gravada na fila do player (gravar no disco na thread da tela engasga o stick), antes do play.
        player.tocar(canal) { prefs.edit().putString("abrindo", Bloqueio.marca(canal.url, boot)).commit() }
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
        if (canal != video.canal) anterior = video.canal
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

    override fun mostrar(camada: Camada) {
        this.camada = camada
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

    // A mais nova entre a lista do teste do app (TesteJob) e a do Termux (reserva): se um parar, o outro segura.
    private val arquivoLista
        get() = listOf(TesteJob.lista(this), File(Environment.getExternalStorageDirectory(), "iptv/canais_ok.m3u"))
            .maxBy { it.lastModified() } // arquivo que nao existe = 0
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

    private fun carregar() {
        bloqueados = bloquearCanalQueDerrubou()
        lifecycleScope.launch {
            val lista = lerLista()
            canais = lista
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
        video.canal?.let(::abrir) // voltou ao app: retoma o ultimo canal
        // Lista nova do Termux: rele ao voltar ao app (Home, stick acordando).
        // shortcut: com o app sempre na frente, so rele na proxima volta; se precisar, reler tambem a cada 6 h.
        if (canais != null && arquivoLista.lastModified() != listaLidaEm) lifecycleScope.launch { canais = lerLista() }
    }

    override fun onStop() {
        super.onStop()
        parar()
        player.soltarTela()
    }

    override fun onDestroy() {
        player.liberar()
        super.onDestroy()
    }
}
