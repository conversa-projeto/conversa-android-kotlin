package com.conversa.app.core.ui.estado

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource

/**
 * Texto para a tela vindo do ViewModel: ou um recurso de `strings.xml` (textos do app),
 * ou um texto pronto (mensagem que veio do servidor, como "Senha incorreta!").
 */
@Immutable
sealed interface TextoUi {
    data class Recurso(@StringRes val id: Int) : TextoUi

    data class Literal(val texto: String) : TextoUi
}

@Composable
fun TextoUi.resolver(): String = when (this) {
    is TextoUi.Recurso -> stringResource(id)
    is TextoUi.Literal -> texto
}
