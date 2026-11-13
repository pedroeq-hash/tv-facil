package br.tvfacil.core

import java.util.Locale
import kotlin.math.roundToInt

data class ResultadoTeste(val canal: Canal, val tocou: Boolean, val msParaAbrir: Long?)

/** Relatorio do modo diagnostico: quantos canais tocam de verdade no player. */
object Diagnostico {
    private val ptBR = Locale.forLanguageTag("pt-BR")

    private fun segundos(ms: Double) = String.format(ptBR, "%.1f s", ms / 1000)

    fun relatorio(resultados: List<ResultadoTeste>): String = buildString {
        val total = resultados.size
        val ok = resultados.filter { it.tocou }
        val pct = if (total == 0) 0 else (ok.size * 100.0 / total).roundToInt()
        appendLine("Canais testados: $total")
        appendLine("Tocaram: ${ok.size} ($pct%)")
        val tempos = ok.mapNotNull { it.msParaAbrir }
        if (tempos.isNotEmpty()) {
            appendLine("Tempo para abrir: média ${segundos(tempos.average())} · mais lento ${segundos(tempos.max().toDouble())}")
        }
        appendLine()
        appendLine("Por grupo:")
        resultados.groupBy { it.canal.grupo }.forEach { (grupo, rs) ->
            appendLine("$grupo: ${rs.count { it.tocou }} de ${rs.size}")
        }
        appendLine()
        appendLine("Não tocaram:")
        resultados.filterNot { it.tocou }.forEach { appendLine("- ${it.canal.nome} (${it.canal.grupo})") }
    }
}
