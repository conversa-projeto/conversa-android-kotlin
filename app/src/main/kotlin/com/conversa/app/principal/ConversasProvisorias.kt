package com.conversa.app.principal

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.conversa.app.R
import com.conversa.app.core.ui.componentes.EstadoVazio

/** Lugar da lista de conversas até o `:feature:conversas` entrar (próximo bloco da etapa 2). */
@Composable
fun ConversasProvisorias(modifier: Modifier = Modifier) {
    EstadoVazio(
        titulo = stringResource(R.string.aba_conversas),
        descricao = stringResource(R.string.em_breve_descricao),
        icone = Icons.AutoMirrored.Outlined.Chat,
        modifier = modifier,
    )
}
