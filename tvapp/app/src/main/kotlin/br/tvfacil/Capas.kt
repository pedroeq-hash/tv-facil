package br.tvfacil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Capas de filmes e series. Carregador proprio (sem biblioteca nova) para controlar os limites num stick fraco:
 * 2 downloads por vez (nao atrapalha o video), ate 1 MB por imagem, reduzida a ~200x300 e em RGB_565,
 * guardada na memoria (~24 MB) e no disco (cacheDir/capas, ate 50 MB). O endereco ja vem validado (Capa.endereco).
 */
object Capas {
    private const val MAX_BYTES = 1_000_000
    private const val LARGURA = 200
    private const val ALTURA = 300
    private const val MAX_DISCO = 50L * 1024 * 1024

    private val memoria = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(chave: String, valor: ImageBitmap) = valor.width * valor.height * 2 // RGB_565
    }
    private val fila = Dispatchers.IO.limitedParallelism(2)

    fun naMemoria(url: String): ImageBitmap? = memoria.get(url)

    suspend fun carregar(c: Context, url: String): ImageBitmap? = withContext(fila) {
        memoria.get(url)?.let { return@withContext it }
        val arquivo = File(pasta(c), sha1(url))
        val bytes = if (arquivo.exists()) arquivo.readBytes() else baixar(url)?.also { runCatching { arquivo.writeBytes(it) } }
        bytes?.let(::decodificar)?.also { memoria.put(url, it) }
    }

    /** Na abertura do app: passou de 50 MB no disco, apaga as capas mais antigas ate 40 MB. */
    fun aparar(c: Context) {
        val arquivos = pasta(c).listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = arquivos.sumOf { it.length() }
        if (total <= MAX_DISCO) return
        for (a in arquivos) {
            if (total <= MAX_DISCO * 4 / 5) break
            total -= a.length()
            a.delete()
        }
    }

    private fun pasta(c: Context) = File(c.cacheDir, "capas").apply { mkdirs() }

    // shortcut: redirecionamento do mesmo protocolo e seguido sem conferir o destino; checar se a capa puder vir da rede local.
    private fun baixar(url: String): ByteArray? = runCatching {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        try {
            if (con.responseCode !in 200..299) return null
            con.inputStream.use { entrada ->
                val saida = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = entrada.read(buf)
                    if (n < 0) break
                    saida.write(buf, 0, n)
                    if (saida.size() > MAX_BYTES) return null // imagem grande demais: fica sem capa
                }
                saida.toByteArray()
            }
        } finally {
            con.disconnect()
        }
    }.getOrNull()

    // Reduz pelo tamanho real (largura e altura): uma imagem pequena no disco pode ter dimensoes enormes.
    private fun decodificar(bytes: ByteArray): ImageBitmap? {
        val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
        if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
        var amostra = 1
        while (medidas.outWidth / (amostra * 2) >= LARGURA || medidas.outHeight / (amostra * 2) >= ALTURA) amostra *= 2
        val opcoes = BitmapFactory.Options().apply {
            inSampleSize = amostra
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes) }.getOrNull()?.asImageBitmap()
    }

    private fun sha1(texto: String) = MessageDigest.getInstance("SHA-1").digest(texto.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** A capa, ou a alternativa (sigla) enquanto carrega ou se nao houver. Sai da tela: o LaunchedEffect cancela. */
@Composable
fun Capa(url: String, modifier: Modifier, alternativa: @Composable () -> Unit) {
    val context = LocalContext.current
    var imagem by remember(url) { mutableStateOf(if (url.isEmpty()) null else Capas.naMemoria(url)) }
    if (url.isNotEmpty() && imagem == null) LaunchedEffect(url) { imagem = Capas.carregar(context, url) }
    val i = imagem
    if (i != null) Image(i, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    else Box(modifier, contentAlignment = Alignment.Center) { alternativa() }
}
