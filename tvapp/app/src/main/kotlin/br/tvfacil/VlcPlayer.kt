package br.tvfacil

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import br.tvfacil.core.Canal
import br.tvfacil.core.Tipo
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.Executors

class VlcPlayer(
    context: Context,
    private val aoTocar: (Canal) -> Unit,
    private val aoFalhar: (Canal) -> Unit,
    private val aoTerminar: (Canal) -> Unit, // filme/episodio chegou ao fim
    private val aoTempo: (posMs: Long, duracaoMs: Long) -> Unit, // so filme/episodio, no maximo 1 vez por segundo
) {
    // android_display: entrega o video direto a tela, sem redesenhar cada quadro por OpenGL (stick fraco).
    private val libVlc = LibVLC(context, arrayListOf("--network-caching=3000", "--vout=android_display"))
    private val mp = MediaPlayer(libVlc)

    // Parar um canal fora do ar pode demorar minutos: nunca na thread da tela, senao o app congela (ANR).
    private val fila = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())

    // So lido/escrito na thread principal; trocado por post() depois do stop(), entao
    // avisos atrasados do canal anterior (ja na fila) ainda saem com o canal anterior.
    private var atual: Canal? = null

    init {
        mp.setEventListener { e ->
            val canal = atual ?: return@setEventListener
            when (e.type) {
                MediaPlayer.Event.Vout -> if (e.voutCount > 0) aoTocar(canal)
                MediaPlayer.Event.Buffering -> if (e.buffering >= 100f) aoTocar(canal) // cobre canais so de audio
                MediaPlayer.Event.EncounteredError -> aoFalhar(canal)
                // No ao vivo, "acabou" e queda do sinal; no filme e o fim mesmo.
                MediaPlayer.Event.EndReached -> if (canal.tipo == Tipo.FILME) aoTerminar(canal) else aoFalhar(canal)
                MediaPlayer.Event.TimeChanged -> if (canal.tipo == Tipo.FILME && e.timeChanged / 1000 != ultimoSegundo) {
                    ultimoSegundo = e.timeChanged / 1000
                    aoTempo(e.timeChanged, mp.length)
                }
            }
        }
    }

    private var ultimoSegundo = -1L
    private var tela: VLCVideoLayout? = null

    fun criarTela(context: Context): View = VLCVideoLayout(context).also {
        tela = it
        prenderTela()
    }

    // Como no exemplo da libVLC: prender a tela no onStart e soltar no onStop. Sem isso, depois que o app
    // sai de cena e volta, a libVLC nao recria o video ("failed to create video output") e so sai o som.
    fun prenderTela() {
        val t = tela ?: return
        if (!mp.vlcVout.areViewsAttached()) mp.attachViews(t, null, false, false)
    }

    fun soltarTela() = mp.detachViews()

    // antes/depois: rodam na fila do player, fora da thread da tela e na mesma ordem dos comandos.
    fun tocar(canal: Canal, inicioMs: Long = 0, antes: () -> Unit = {}) = fila.execute {
        mp.stop()
        principal.post { atual = canal; ultimoSegundo = -1 }
        antes()
        val media = Media(libVlc, Uri.parse(canal.url))
        media.setHWDecoderEnabled(true, false) // decodificador de hardware do stick (MediaCodec)
        // Alguns canais so respondem se o player se identificar como o navegador/site esperado.
        if (canal.userAgent.isNotEmpty()) media.addOption(":http-user-agent=${canal.userAgent}")
        if (canal.referrer.isNotEmpty()) media.addOption(":http-referrer=${canal.referrer}")
        if (inicioMs > 0) media.addOption(":start-time=${inicioMs / 1000}") // continuar de onde parou
        mp.media = media
        media.release()
        mp.play()
    }

    fun pausar(sim: Boolean) = fila.execute { if (!sim) mp.play() else if (mp.isPlaying) mp.pause() }

    fun pular(ms: Long) = fila.execute {
        val duracao = mp.length
        if (duracao > 0) mp.time = (mp.time + ms).coerceIn(0, duracao - 1_000)
    }

    fun irPara(ms: Long) = fila.execute {
        val duracao = mp.length
        if (duracao > 0) mp.time = ms.coerceIn(0, duracao - 1_000)
    }

    fun parar(depois: () -> Unit = {}) = fila.execute {
        mp.stop()
        principal.post { atual = null }
        depois()
    }

    fun liberar() {
        mp.detachViews()
        fila.execute {
            mp.stop()
            mp.release()
            libVlc.release()
        }
        fila.shutdown()
    }
}
