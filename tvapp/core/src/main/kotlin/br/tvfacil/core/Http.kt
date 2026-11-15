package br.tvfacil.core

import java.io.ByteArrayOutputStream
import java.io.InputStream

data class Pedido(val metodo: String, val caminho: String, val cabecalhos: Map<String, String>, val corpo: String)

/** Le um pedido HTTP vindo da rede (nao confiavel): pequeno, simples, e null se algo estiver errado. */
object Http {
    private const val MAX_CABECALHO = 8 * 1024
    private const val MAX_CORPO = 64 * 1024 // cabe um arquivo .m3u de canais avulsos (~300 canais)

    fun ler(entrada: InputStream): Pedido? {
        val cabecalho = lerAteLinhaVazia(entrada) ?: return null
        val linhas = cabecalho.split("\r\n")
        val partes = linhas.first().split(' ')
        if (partes.size != 3 || !partes[1].startsWith("/")) return null
        val cabecalhos = mutableMapOf<String, String>()
        for (linha in linhas.drop(1)) {
            val i = linha.indexOf(':')
            if (i <= 0) return null
            cabecalhos[linha.substring(0, i).trim().lowercase()] = linha.substring(i + 1).trim()
        }
        val tamanho = cabecalhos["content-length"]?.let { it.toIntOrNull() ?: return null } ?: 0
        if (tamanho !in 0..MAX_CORPO) return null
        val corpo = ByteArray(tamanho)
        var lidos = 0
        while (lidos < tamanho) {
            val n = entrada.read(corpo, lidos, tamanho - lidos)
            if (n < 0) return null
            lidos += n
        }
        return Pedido(partes[0], partes[1], cabecalhos, corpo.toString(Charsets.UTF_8))
    }

    // Byte a byte ate "\r\n\r\n": nunca le mais do que o cabecalho (o corpo vem depois, pelo Content-Length).
    private fun lerAteLinhaVazia(entrada: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var fim = 0
        while (fim < 4) {
            val b = entrada.read()
            if (b < 0 || buf.size() >= MAX_CABECALHO) return null
            buf.write(b)
            fim = if (b == "\r\n\r\n"[fim].code) fim + 1 else if (b == '\r'.code) 1 else 0
        }
        return buf.toString(Charsets.ISO_8859_1.name()).dropLast(4)
    }
}
