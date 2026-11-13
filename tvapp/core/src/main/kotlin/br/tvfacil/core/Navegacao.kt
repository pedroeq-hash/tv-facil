package br.tvfacil.core

enum class Tecla { OK, VOLTAR, MENU, CANAL_MAIS, CANAL_MENOS }
enum class Camada { ASSISTINDO, LISTA, MENU, BUSCA }
enum class Efeito { CANAL_MAIS, CANAL_MENOS }
data class Reacao(val camada: Camada, val efeito: Efeito? = null)

/** O que cada botão do controle faz em cada camada da tela. */
object Navegacao {
    // Códigos de android.view.KeyEvent, conferidos no controle real (docs/plano-tvapp.md).
    fun tecla(keyCode: Int): Tecla? = when (keyCode) {
        23, 66, 160 -> Tecla.OK // DPAD_CENTER, ENTER, NUMPAD_ENTER
        4 -> Tecla.VOLTAR
        82 -> Tecla.MENU
        166 -> Tecla.CANAL_MAIS
        167 -> Tecla.CANAL_MENOS
        else -> null
    }

    /** null = a camada não trata a tecla: ela segue para o item em foco ou para o sistema (Voltar na lista sai do app). */
    fun reagir(camada: Camada, tecla: Tecla, canalComProblema: Boolean = false): Reacao? = when (camada) {
        Camada.ASSISTINDO -> when (tecla) {
            Tecla.OK -> if (canalComProblema) null else Reacao(Camada.LISTA)
            Tecla.VOLTAR -> Reacao(Camada.LISTA) // Voltar: assistindo -> lista -> sai do app
            Tecla.MENU -> Reacao(Camada.MENU)
            Tecla.CANAL_MAIS -> Reacao(camada, Efeito.CANAL_MAIS)
            Tecla.CANAL_MENOS -> Reacao(camada, Efeito.CANAL_MENOS)
        }
        Camada.LISTA, Camada.MENU, Camada.BUSCA -> when (tecla) {
            Tecla.VOLTAR -> if (camada == Camada.LISTA) null else Reacao(Camada.ASSISTINDO)
            Tecla.MENU -> Reacao(if (camada == Camada.MENU) Camada.ASSISTINDO else Camada.MENU)
            else -> null
        }
    }
}
