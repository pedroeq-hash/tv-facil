package br.tvfacil.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ResumoTest {
    // Trechos reais do registro.txt do stick (14 e 15/11/2026).
    private val rodada = """
        2026-11-15 06:51:00 inicio da rodada
          falhou: 45.162.230.234 - tempo esgotado
          falhou: www.youtube.com - pagina HTML
        2026-11-15 06:54:59 322 de 496 canais funcionando, 88 removidos pelos filtros, 234 na lista (3min 58s)
        2026-11-15 06:54:59 grupos: Sergipe=0; Noticias Brasil=21; Emissoras abertas=12; Filmes=31; Esportes=5; Outros=165;
    """.trimIndent()

    @Test
    fun `rodada dos canais vira uma frase com o horario de inicio e fim`() {
        assertEquals(
            listOf(
                "Próximo teste dos canais: por volta de 15/11 12:51.",
                "15/11 06:51 a 06:54 — Canais testados: 234 na lista (322 de 496 responderam).",
            ),
            Resumo.simples(rodada),
        )
    }

    @Test
    fun `canais das suas listas e avulsos entram na mesma frase`() {
        val log = """
            2026-11-14 22:27:37 inicio da rodada
            2026-11-14 22:31:47 323 de 496 canais funcionando, 86 removidos pelos filtros, 237 na lista (4min 9s)
            2026-11-14 22:31:47 grupos: Sergipe=0;
            2026-11-14 22:31:47 + 9 canais de listas proprias (sem teste)
            2026-11-14 22:31:47   (entre eles, 9 canais avulsos)
        """.trimIndent()
        assertEquals(
            "14/11 22:27 a 22:31 — Canais testados: 237 na lista (323 de 496 responderam) + 9 das suas listas (9 canais avulsos).",
            Resumo.simples(log).last(),
        )
    }

    @Test
    fun `filmes e series, interrupcao e o mais novo primeiro`() {
        val log = """
            2026-11-14 23:42:33 inicio: listas proprias (1)
            2026-11-14 23:48:42 listas proprias: 30203 filmes, 8426 series (286151 episodios), 0 canais ao vivo (6min 9s)
            2026-11-14 23:48:42 inicio: listas proprias (1)
            2026-11-14 23:52:33 ERRO na rodada: m60
            2026-11-14 23:52:33 ERRO na rodada: m60
            2026-11-14 23:53:24 inicio: listas proprias (1)
            2026-11-14 23:58:32 listas proprias: 30203 filmes, 8426 series (286151 episodios), 0 canais ao vivo (5min 7s)
        """.trimIndent()
        assertEquals(
            listOf(
                "Próxima atualização de filmes e séries: por volta de 15/11 23:53.",
                "14/11 23:53 a 23:58 — Filmes e séries atualizados: 30.203 filmes e 8.426 séries.",
                "⚠ 14/11 23:52 — A TV interrompeu a atualização; ela tenta de novo sozinha.",
                "14/11 23:42 a 23:48 — Filmes e séries atualizados: 30.203 filmes e 8.426 séries.",
            ),
            Resumo.simples(log),
        )
    }

    @Test
    fun `falhas, dia virando e rodada que ainda nao terminou`() {
        val log = """
            2026-11-14 23:58:00 inicio da rodada
            2026-11-14 23:58:01 Falhou ao baixar iptv-org.github.io (UnknownHostException)
            2026-11-15 00:02:00 ERRO: 0 canais lidos, 0 funcionando, 0 na lista - lista antiga mantida
            2026-11-15 01:00:00 inicio: listas proprias (1)
            2026-11-15 01:00:05 Falhou ao baixar painel.exemplo.com (IOException) - filmes e series anteriores mantidos
            2026-11-15 02:00:00 listas proprias: em dia, pulado
            2026-11-15 06:51:00 inicio da rodada
        """.trimIndent()
        assertEquals(
            listOf(
                "Próximo teste dos canais: por volta de 15/11 12:51.",
                "Próxima atualização de filmes e séries: por volta de 16/11 02:00.",
                "15/11 06:51 — Testando os canais agora…",
                "15/11 02:00 — Filmes e séries já estavam em dia.",
                "⚠ 15/11 01:00 — Não consegui baixar a lista painel.exemplo.com; ficou a anterior.",
                "⚠ 15/11 00:02 — Nenhuma lista baixou (sem internet?); ficou a lista anterior.",
                "⚠ 14/11 23:58 — Não consegui baixar a lista iptv-org.github.io; ficou a anterior.",
            ),
            Resumo.simples(log),
        )
    }

    @Test
    fun `rodada cortada no meio aparece como nao terminada`() {
        val log = "2026-11-15 06:51:00 inicio da rodada\n2026-11-15 07:00:00 inicio: listas proprias (1)\n" +
            "2026-11-15 07:05:00 listas proprias: 1 filmes, 0 series (0 episodios), 2 canais ao vivo (5min 0s)"
        assertEquals("15/11 06:51 — Começou a testar os canais e não terminou.", Resumo.simples(log).last())
    }

    @Test
    fun `registro vazio ou estragado`() {
        assertEquals(emptyList(), Resumo.simples(""))
        assertEquals(emptyList(), Resumo.simples("lixo\n  falhou: x\n2026-99-99 99:99:99 inicio da rodada"))
    }

    /** Registro real do stick (fora do repositorio): REGISTRO_REAL=caminho gradlew :core:test --tests '*ResumoTest*'. */
    @Test
    fun `registro real - mostra as frases (pulado sem REGISTRO_REAL)`() {
        val caminho = System.getenv("REGISTRO_REAL") ?: return
        Resumo.simples(java.io.File(caminho).readText()).forEach { println("RESUMO: $it") }
    }

    @Test
    fun `no maximo 15 frases`() {
        val log = (10..40).joinToString("\n") { "2026-11-15 06:$it:00 listas proprias: em dia, pulado" }
        assertEquals(1 + 15, Resumo.simples(log).size)
    }
}
