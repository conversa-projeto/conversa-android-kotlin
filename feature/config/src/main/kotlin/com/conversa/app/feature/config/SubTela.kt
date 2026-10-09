package com.conversa.app.feature.config

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Uma seção das Configurações aberta como tela (o web no celular: aba → sub-tela com voltar).
 * Superfície branca, como a lista de Configurações. [conteudo] recebe o modificador com as margens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubTela(
    titulo: String,
    aoVoltar: () -> Unit,
    acoes: @Composable RowScope.() -> Unit = {},
    conteudo: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(titulo) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(com.conversa.app.core.ui.R.string.voltar),
                        )
                    }
                },
                actions = acoes,
            )
        },
    ) { margens -> conteudo(Modifier.padding(margens)) }
}
