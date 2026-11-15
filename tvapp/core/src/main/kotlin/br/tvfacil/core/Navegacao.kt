package br.tvfacil.core

enum class Tecla { OK, VOLTAR, MENU, CANAL_MAIS, CANAL_MENOS, ESQUERDA, DIREITA }

// FILMES/SERIES = grades; SERIE = temporadas e episodios de uma serie; FILME = assistindo filme ou episodio.
enum class Camada { ASSISTINDO, LISTA, MENU, BUSCA, GRADE, CONFIGURAR, FILMES, SERIES, SERIE, FILME }
enum class Efeito { CANAL_MAIS, CANAL_MENOS, AVISAR_SAIDA, PAUSAR, VOLTAR_10S, AVANCAR_30S, MOSTRAR_BARRA, SAIR_FILME }
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
        21 -> Tecla.ESQUERDA // fora do filme a reacao e null: a seta segue para o foco
        22 -> Tecla.DIREITA
        else -> null
    }

    /**
     * null = a camada não trata a tecla: ela segue para o item em foco ou para o sistema (Voltar na lista sai do app).
     * msDesdeVoltar: tempo desde o último aviso de saída; Voltar na lista só sai se for até 3 s depois dele.
     * voltarPara: de onde o filme foi aberto (grade de filmes ou a série).
     */
    fun reagir(
        camada: Camada,
        tecla: Tecla,
        canalComProblema: Boolean = false,
        msDesdeVoltar: Long = Long.MAX_VALUE,
        voltarPara: Camada = Camada.FILMES,
    ): Reacao? = when (camada) {
        Camada.ASSISTINDO -> when (tecla) {
            Tecla.OK -> if (canalComProblema) null else Reacao(Camada.LISTA)
            Tecla.VOLTAR -> Reacao(Camada.LISTA) // Voltar: assistindo -> lista -> aviso -> sai do app
            Tecla.MENU -> Reacao(Camada.MENU)
            Tecla.CANAL_MAIS -> Reacao(camada, Efeito.CANAL_MAIS)
            Tecla.CANAL_MENOS -> Reacao(camada, Efeito.CANAL_MENOS)
            Tecla.ESQUERDA, Tecla.DIREITA -> null
        }
        Camada.FILME -> when (tecla) {
            Tecla.OK -> Reacao(camada, Efeito.PAUSAR)
            Tecla.ESQUERDA -> Reacao(camada, Efeito.VOLTAR_10S)
            Tecla.DIREITA -> Reacao(camada, Efeito.AVANCAR_30S)
            Tecla.MENU -> Reacao(camada, Efeito.MOSTRAR_BARRA)
            Tecla.CANAL_MAIS, Tecla.CANAL_MENOS -> Reacao(camada) // filme nao tem "proximo canal"
            Tecla.VOLTAR -> Reacao(voltarPara, Efeito.SAIR_FILME)
        }
        Camada.LISTA, Camada.MENU, Camada.BUSCA, Camada.GRADE, Camada.CONFIGURAR,
        Camada.FILMES, Camada.SERIES, Camada.SERIE -> when (tecla) {
            Tecla.VOLTAR -> when {
                camada == Camada.SERIE -> Reacao(Camada.SERIES)
                camada != Camada.LISTA -> Reacao(Camada.ASSISTINDO)
                msDesdeVoltar <= 3_000 -> null
                else -> Reacao(camada, Efeito.AVISAR_SAIDA)
            }
            Tecla.MENU -> Reacao(if (camada == Camada.MENU) Camada.ASSISTINDO else Camada.MENU)
            else -> null
        }
    }
}
