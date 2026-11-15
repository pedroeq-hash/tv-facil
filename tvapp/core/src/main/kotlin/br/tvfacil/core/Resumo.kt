package br.tvfacil.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * O registro.txt em frases simples, para quem configura a TV (pagina de configuracao).
 * Le as linhas que o TesteJob grava; as "falhou: dominio" ficam so no registro completo.
 */
object Resumo {
    private val LINHA = Regex("""^(\d{4}-\d\d-\d\d \d\d:\d\d:\d\d) (.*)$""")
    private val LER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    private val HORA = DateTimeFormatter.ofPattern("HH:mm")
    private val CANAIS = Regex("""^(\d+) de (\d+) canais funcionando, .*, (\d+) na lista""")
    private val PROPRIAS = Regex("""^listas proprias: (\d+) filmes, (\d+) series \(\d+ episodios\), (\d+) canais ao vivo""")
    private val MAIS = Regex("""^\+ (\d+) canais de listas proprias""")
    private val AVULSOS = Regex("""^\s*\(entre eles, (\d+) canais avulsos\)""")
    private val FALHOU = Regex("""^Falhou ao baixar (\S+)""")

    private enum class Tarefa { CANAIS, PROPRIAS }

    fun simples(log: String, max: Int = 15): List<String> {
        val frases = mutableListOf<String>()
        var aberta: Pair<Tarefa, LocalDateTime>? = null
        var inicioCanais: LocalDateTime? = null
        var inicioProprias: LocalDateTime? = null

        fun dizer(frase: String) {
            if (frases.lastOrNull() != frase) frases += frase // o registro as vezes repete a mesma linha
        }
        fun naoTerminou() {
            val (tarefa, hora) = aberta ?: return
            dizer("${DIA_HORA.format(hora)} — Começou a ${if (tarefa == Tarefa.CANAIS) "testar os canais" else "atualizar filmes e séries"} e não terminou.")
            aberta = null
        }
        // "15/11 06:51 a 06:55" (ou so a hora do fim, se o inicio ficou fora do trecho lido)
        fun quando(fim: LocalDateTime, tarefa: Tarefa): String {
            val inicio = aberta?.takeIf { it.first == tarefa }?.second
            aberta = null
            if (inicio == null) return DIA_HORA.format(fim)
            val ate = if (inicio.toLocalDate() == fim.toLocalDate()) HORA.format(fim) else DIA_HORA.format(fim)
            return "${DIA_HORA.format(inicio)} a $ate"
        }

        for (linha in log.lineSequence()) {
            val m = LINHA.matchEntire(linha) ?: continue
            val hora = runCatching { LocalDateTime.parse(m.groupValues[1], LER) }.getOrNull() ?: continue
            val texto = m.groupValues[2]
            when {
                texto == "inicio da rodada" -> {
                    naoTerminou()
                    aberta = Tarefa.CANAIS to hora
                    inicioCanais = hora
                }
                texto.startsWith("inicio: listas proprias") -> {
                    naoTerminou()
                    aberta = Tarefa.PROPRIAS to hora
                    inicioProprias = hora
                }
                texto.startsWith("listas proprias: em dia") -> {
                    inicioProprias = hora
                    dizer("${DIA_HORA.format(hora)} — Filmes e séries já estavam em dia.")
                }
                CANAIS.find(texto) != null -> {
                    val (ok, lidos, lista) = CANAIS.find(texto)!!.destructured
                    dizer("${quando(hora, Tarefa.CANAIS)} — Canais testados: $lista na lista ($ok de $lidos responderam).")
                }
                PROPRIAS.find(texto) != null -> {
                    val (filmes, series, aoVivo) = PROPRIAS.find(texto)!!.destructured
                    val vivo = if (aoVivo != "0") " e ${milhar(aoVivo)} canais ao vivo" else ""
                    dizer("${quando(hora, Tarefa.PROPRIAS)} — Filmes e séries atualizados: ${milhar(filmes)} filmes e ${milhar(series)} séries$vivo.")
                }
                MAIS.find(texto) != null && frases.isNotEmpty() ->
                    frases[frases.lastIndex] = frases.last().removeSuffix(".") + " + ${MAIS.find(texto)!!.groupValues[1]} das suas listas."
                AVULSOS.find(texto) != null && frases.isNotEmpty() ->
                    frases[frases.lastIndex] = frases.last().removeSuffix(".") + " (${AVULSOS.find(texto)!!.groupValues[1]} canais avulsos)."
                FALHOU.find(texto) != null -> {
                    if (aberta?.first == Tarefa.PROPRIAS) aberta = null // lista propria que falha encerra a atualizacao
                    dizer("⚠ ${DIA_HORA.format(hora)} — Não consegui baixar a lista ${FALHOU.find(texto)!!.groupValues[1]}; ficou a anterior.")
                }
                texto.startsWith("ERRO na rodada") -> {
                    aberta = null
                    dizer("⚠ ${DIA_HORA.format(hora)} — A TV interrompeu a atualização; ela tenta de novo sozinha.")
                }
                texto.startsWith("ERRO:") -> {
                    aberta = null
                    dizer("⚠ ${DIA_HORA.format(hora)} — Nenhuma lista baixou (sem internet?); ficou a lista anterior.")
                }
            }
        }
        aberta?.let { (tarefa, hora) ->
            frases += "${DIA_HORA.format(hora)} — ${if (tarefa == Tarefa.CANAIS) "Testando os canais" else "Atualizando filmes e séries"} agora…"
        }

        // shortcut: previsao = ultimo inicio + intervalo do job; o Android pode atrasar (stick dormindo, sem rede).
        val previsao = listOfNotNull(
            inicioCanais?.let { "Próximo teste dos canais: por volta de ${DIA_HORA.format(it.plusHours(6))}." },
            inicioProprias?.let { "Próxima atualização de filmes e séries: por volta de ${DIA_HORA.format(it.plusHours(24))}." },
        )
        return previsao + frases.asReversed().take(max)
    }

    private fun milhar(n: String) = n.reversed().chunked(3).joinToString(".").reversed()
}
