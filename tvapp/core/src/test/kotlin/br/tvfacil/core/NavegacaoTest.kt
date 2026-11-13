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
    fun `lista - Voltar de novo fica com o sistema e sai do app`() {
        assertNull(Navegacao.reagir(LISTA, Tecla.VOLTAR))
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
}
