package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema

@Composable
fun Carregando(modifier: Modifier = Modifier, texto: String? = null) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (texto != null) {
            Text(texto, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EstadoVazio(
    titulo: String,
    modifier: Modifier = Modifier,
    descricao: String? = null,
    icone: ImageVector = Icons.Outlined.Inbox,
) {
    Mensagem(modifier, icone, titulo, descricao, null, null)
}

@Composable
fun EstadoErro(
    mensagem: String,
    aoTentarDeNovo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Mensagem(
        modifier = modifier,
        icone = Icons.Outlined.CloudOff,
        titulo = mensagem,
        descricao = null,
        acao = aoTentarDeNovo?.let { stringResource(R.string.tentar_de_novo) },
        aoAgir = aoTentarDeNovo,
    )
}

@Composable
private fun Mensagem(
    modifier: Modifier,
    icone: ImageVector,
    titulo: String,
    descricao: String?,
    acao: String?,
    aoAgir: (() -> Unit)?,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icone, contentDescription = null, modifier = Modifier.size(48.dp), tint = ConversaTema.cores.iconeDiscreto)
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (descricao != null) {
                Text(
                    descricao,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (acao != null && aoAgir != null) OutlinedButton(onClick = aoAgir) { Text(acao) }
        }
    }
}

/** Mostra um erro no Snackbar global (GER-01). */
suspend fun SnackbarHostState.mostrarErro(mensagem: String) {
    currentSnackbarData?.dismiss()
    showSnackbar(mensagem, withDismissAction = true, duration = SnackbarDuration.Long)
}

@Preview(showBackground = true)
@Composable
private fun EstadosPreview() {
    ConversaTema {
        Column {
            Box(Modifier.size(300.dp)) { EstadoVazio(titulo = "Nenhuma conversa", descricao = "Comece uma pelo botão abaixo.") }
            Box(Modifier.size(300.dp)) { EstadoErro(mensagem = "Sem conexão com o servidor.", aoTentarDeNovo = {}) }
        }
    }
}
