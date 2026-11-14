package br.tvfacil.core

enum class Tecla { OK, VOLTAR, MENU, CANAL_MAIS, CANAL_MENOS }
enum class Camada { ASSISTINDO, LISTA, MENU, BUSCA, GRADE }
enum class Efeito { CANAL_MAIS, CANAL_MENOS, AVISAR_SAIDA }
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

    /**
     * null = a camada não trata a tecla: ela segue para o item em foco ou para o sistema (Voltar na lista sai do app).
     * msDesdeVoltar: tempo desde o último aviso de saída; Voltar na lista só sai se for até 3 s depois dele.
     */
    fun reagir(
        camada: Camada,
        tecla: Tecla,
        canalComProblema: Boolean = false,
        msDesdeVoltar: Long = Long.MAX_VALUE,
    ): Reacao? = when (camada) {
        Camada.ASSISTINDO -> when (tecla) {
            Tecla.OK -> if (canalComProblema) null else Reacao(Camada.LISTA)
            Tecla.VOLTAR -> Reacao(Camada.LISTA) // Voltar: assistindo -> lista -> aviso -> sai do app
            Tecla.MENU -> Reacao(Camada.MENU)
            Tecla.CANAL_MAIS -> Reacao(camada, Efeito.CANAL_MAIS)
            Tecla.CANAL_MENOS -> Reacao(camada, Efeito.CANAL_MENOS)
        }
        Camada.LISTA, Camada.MENU, Camada.BUSCA, Camada.GRADE -> when (tecla) {
            Tecla.VOLTAR -> when {
                camada != Camada.LISTA -> Reacao(Camada.ASSISTINDO)
                msDesdeVoltar <= 3_000 -> null
                else -> Reacao(camada, Efeito.AVISAR_SAIDA)
            }
            Tecla.MENU -> Reacao(if (camada == Camada.MENU) Camada.ASSISTINDO else Camada.MENU)
            else -> null
        }
    }
}
