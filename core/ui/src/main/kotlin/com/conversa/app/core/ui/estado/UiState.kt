package com.conversa.app.core.ui.estado

/**
 * Estado de uma tela que carrega dados. Um só lugar decide o que aparece
 * (evita o problema #26 do legado: lista, vazio e erro brigando entre si).
 */
sealed interface UiState<out T> {
    data object Carregando : UiState<Nothing>

    data class Conteudo<T>(val dados: T, val atualizando: Boolean = false) : UiState<T>

    data object Vazio : UiState<Nothing>

    data class Erro(val mensagem: String) : UiState<Nothing>
}

/** Converte uma lista em [UiState.Conteudo] ou [UiState.Vazio]. */
fun <T> List<T>.comoUiState(): UiState<List<T>> = if (isEmpty()) UiState.Vazio else UiState.Conteudo(this)
