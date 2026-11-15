package br.tvfacil.core

import br.tvfacil.core.Camada.ASSISTINDO
import br.tvfacil.core.Camada.BUSCA
import br.tvfacil.core.Camada.GRADE
import br.tvfacil.core.Camada.LISTA
import br.tvfacil.core.Camada.MENU
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavegacaoTest {
    @Test
    fun `codigos medidos no controle real`() {
        assertEquals(Tecla.OK, Navegacao.tecla(23))
        assertEquals(Tecla.VOLTAR, Navegacao.tecla(4))
        assertEquals(Tecla.MENU, Navegacao.tecla(82))
        assertEquals(Tecla.CANAL_MAIS, Navegacao.tecla(166))
        assertEquals(Tecla.CANAL_MENOS, Navegacao.tecla(167))
        assertNull(Navegacao.tecla(19)) // seta para cima: fica com a tela (foco)
    }

    @Test
    fun `assistindo - OK abre a lista, menu abre o menu, CH troca de canal`() {
        assertEquals(Reacao(LISTA), Navegacao.reagir(ASSISTINDO, Tecla.OK))
        assertEquals(Reacao(MENU), Navegacao.reagir(ASSISTINDO, Tecla.MENU))
        assertEquals(Reacao(ASSISTINDO, Efeito.CANAL_MAIS), Navegacao.reagir(ASSISTINDO, Tecla.CANAL_MAIS))
        assertEquals(Reacao(ASSISTINDO, Efeito.CANAL_MENOS), Navegacao.reagir(ASSISTINDO, Tecla.CANAL_MENOS))
    }

    @Test
    fun `assistindo - Voltar abre a lista e OK vai para os botoes do canal com problema`() {
        assertEquals(Reacao(LISTA), Navegacao.reagir(ASSISTINDO, Tecla.VOLTAR))
        assertEquals(Reacao(LISTA), Navegacao.reagir(ASSISTINDO, Tecla.VOLTAR, canalComProblema = true))
        assertNull(Navegacao.reagir(ASSISTINDO, Tecla.OK, canalComProblema = true))
    }

    @Test
    fun `lista - 1o Voltar avisa, 2o em ate 3 s fica com o sistema e sai do app`() {
        val aviso = Reacao(LISTA, Efeito.AVISAR_SAIDA)
        assertEquals(aviso, Navegacao.reagir(LISTA, Tecla.VOLTAR)) // nenhum Voltar antes
        assertNull(Navegacao.reagir(LISTA, Tecla.VOLTAR, msDesdeVoltar = 500))
        assertNull(Navegacao.reagir(LISTA, Tecla.VOLTAR, msDesdeVoltar = 3_000))
        assertEquals(aviso, Navegacao.reagir(LISTA, Tecla.VOLTAR, msDesdeVoltar = 3_001)) // demorou: avisa de novo
    }

    @Test
    fun `lista e menu - menu alterna, OK e CH ficam com a tela, Voltar fecha o menu`() {
        assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(MENU, Tecla.VOLTAR))
        for (camada in listOf(LISTA, MENU)) {
            assertNull(Navegacao.reagir(camada, Tecla.OK))
            assertNull(Navegacao.reagir(camada, Tecla.CANAL_MAIS))
        }
        assertEquals(Reacao(MENU), Navegacao.reagir(LISTA, Tecla.MENU))
        assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(MENU, Tecla.MENU))
    }

    @Test
    fun `busca - Voltar fecha, menu abre o menu, OK e CH ficam com a tela`() {
        assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(BUSCA, Tecla.VOLTAR))
        assertEquals(Reacao(MENU), Navegacao.reagir(BUSCA, Tecla.MENU))
        assertNull(Navegacao.reagir(BUSCA, Tecla.OK))
        assertNull(Navegacao.reagir(BUSCA, Tecla.CANAL_MAIS))
    }

    @Test
    fun `grade - Voltar volta a assistir, menu abre o menu, OK e CH ficam com o cartao`() {
        assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(GRADE, Tecla.VOLTAR))
        assertEquals(Reacao(MENU), Navegacao.reagir(GRADE, Tecla.MENU))
        assertNull(Navegacao.reagir(GRADE, Tecla.OK))
        assertNull(Navegacao.reagir(GRADE, Tecla.CANAL_MAIS))
    }

    @Test
    fun `configurar - Voltar volta a assistir, menu abre o menu, OK fica com a tela`() {
        assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(Camada.CONFIGURAR, Tecla.VOLTAR))
        assertEquals(Reacao(MENU), Navegacao.reagir(Camada.CONFIGURAR, Tecla.MENU))
        assertNull(Navegacao.reagir(Camada.CONFIGURAR, Tecla.OK))
    }

    @Test
    fun `setas so viram tecla para o filme - fora dele seguem para o foco`() {
        assertEquals(Tecla.ESQUERDA, Navegacao.tecla(21))
        assertEquals(Tecla.DIREITA, Navegacao.tecla(22))
        for (camada in listOf(ASSISTINDO, LISTA, GRADE, Camada.FILMES, Camada.SERIES, Camada.SERIE)) {
            assertNull(Navegacao.reagir(camada, Tecla.ESQUERDA), "$camada")
            assertNull(Navegacao.reagir(camada, Tecla.DIREITA), "$camada")
        }
    }

    @Test
    fun `filmes e series - grade como a de canais`() {
        for (camada in listOf(Camada.FILMES, Camada.SERIES)) {
            assertEquals(Reacao(ASSISTINDO), Navegacao.reagir(camada, Tecla.VOLTAR))
            assertEquals(Reacao(MENU), Navegacao.reagir(camada, Tecla.MENU))
            assertNull(Navegacao.reagir(camada, Tecla.OK))
            assertNull(Navegacao.reagir(camada, Tecla.CANAL_MAIS))
        }
    }

    @Test
    fun `serie - Voltar volta para a grade de series`() {
        assertEquals(Reacao(Camada.SERIES), Navegacao.reagir(Camada.SERIE, Tecla.VOLTAR))
        assertEquals(Reacao(MENU), Navegacao.reagir(Camada.SERIE, Tecla.MENU))
        assertNull(Navegacao.reagir(Camada.SERIE, Tecla.OK))
    }

    @Test
    fun `filme - OK pausa, setas pulam, CH nao troca nada, Voltar volta de onde veio`() {
        val f = Camada.FILME
        assertEquals(Reacao(f, Efeito.PAUSAR), Navegacao.reagir(f, Tecla.OK))
        assertEquals(Reacao(f, Efeito.VOLTAR_10S), Navegacao.reagir(f, Tecla.ESQUERDA))
        assertEquals(Reacao(f, Efeito.AVANCAR_30S), Navegacao.reagir(f, Tecla.DIREITA))
        assertEquals(Reacao(f, Efeito.MOSTRAR_BARRA), Navegacao.reagir(f, Tecla.MENU))
        assertEquals(Reacao(f), Navegacao.reagir(f, Tecla.CANAL_MAIS))
        assertEquals(Reacao(f), Navegacao.reagir(f, Tecla.CANAL_MENOS))
        assertEquals(Reacao(Camada.FILMES, Efeito.SAIR_FILME), Navegacao.reagir(f, Tecla.VOLTAR))
        assertEquals(Reacao(Camada.SERIE, Efeito.SAIR_FILME), Navegacao.reagir(f, Tecla.VOLTAR, voltarPara = Camada.SERIE))
    }
}
