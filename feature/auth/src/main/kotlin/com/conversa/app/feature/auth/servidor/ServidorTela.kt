package com.conversa.app.feature.auth.servidor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.auth.R

@Composable
fun ServidorRotaTela(
    aoSalvar: () -> Unit,
    aoVoltar: (() -> Unit)?,
    viewModel: ServidorViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            EventoServidor.Salvo -> aoSalvar()
        }
    }
    ServidorTela(
        estado = estado,
        aoAlterar = viewModel::alterarEndereco,
        aoTestar = viewModel::testar,
        aoSalvar = viewModel::salvar,
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServidorTela(
    estado: ServidorUiState,
    aoAlterar: (String) -> Unit,
    aoTestar: () -> Unit,
    aoSalvar: () -> Unit,
    aoVoltar: (() -> Unit)?,
) {
    val ocupado = estado.testando || estado.salvando
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.servidor_titulo)) },
                navigationIcon = {
                    if (aoVoltar != null) {
                        IconButton(onClick = aoVoltar) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                        }
                    }
                },
            )
        },
    ) { margens ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.servidor_explicacao), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = estado.endereco,
                onValueChange = aoAlterar,
                label = { Text(stringResource(R.string.servidor_campo)) },
                placeholder = { Text(stringResource(R.string.servidor_exemplo)) },
                supportingText = estado.enderecoNormalizado?.let { { Text(stringResource(R.string.servidor_sera_usado, it)) } },
                singleLine = true,
                enabled = !ocupado,
                isError = estado.aviso != null && estado.aviso != AvisoServidor.CONEXAO_OK,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { aoSalvar() }),
                modifier = Modifier.fillMaxWidth(),
            )
            estado.aviso?.let { Aviso(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = aoTestar, enabled = !ocupado) {
                    Text(stringResource(R.string.servidor_testar))
                }
                Button(onClick = aoSalvar, enabled = !ocupado) {
                    Text(stringResource(R.string.salvar_servidor))
                }
                if (ocupado) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun Aviso(aviso: AvisoServidor) {
    val ok = aviso == AvisoServidor.CONEXAO_OK
    val texto = stringResource(
        when (aviso) {
            AvisoServidor.ENDERECO_INVALIDO -> R.string.servidor_aviso_invalido
            AvisoServidor.CONEXAO_OK -> R.string.servidor_aviso_ok
            AvisoServidor.NAO_EH_CONVERSA -> R.string.servidor_aviso_nao_conversa
            AvisoServidor.CERTIFICADO_INVALIDO -> R.string.servidor_aviso_certificado
            AvisoServidor.HOST_NAO_ENCONTRADO -> R.string.servidor_aviso_host
            AvisoServidor.INDISPONIVEL -> R.string.servidor_aviso_indisponivel
            AvisoServidor.SEM_CONEXAO -> R.string.servidor_aviso_sem_conexao
        },
    )
    val cor = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null, tint = cor)
        Text(texto, color = cor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true)
@Composable
private fun ServidorTelaPreview() {
    ConversaTema {
        ServidorTela(
            estado = ServidorUiState(
                endereco = "192.168.2.5",
                enderecoNormalizado = "https://192.168.2.5/",
                aviso = AvisoServidor.CERTIFICADO_INVALIDO,
            ),
            aoAlterar = {},
            aoTestar = {},
            aoSalvar = {},
            aoVoltar = null,
        )
    }
}
