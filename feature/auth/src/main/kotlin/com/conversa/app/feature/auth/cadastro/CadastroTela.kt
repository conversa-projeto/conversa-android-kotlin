package com.conversa.app.feature.auth.cadastro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.resolver
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.auth.R

@Composable
fun CadastroRotaTela(
    aoCriar: (usuario: String) -> Unit,
    aoVoltar: () -> Unit,
    viewModel: CadastroViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoCadastro.Criada -> aoCriar(evento.usuario)
        }
    }
    CadastroTela(
        estado = estado,
        aoAlterarNome = viewModel::alterarNome,
        aoAlterarUsuario = viewModel::alterarUsuario,
        aoAlterarEmail = viewModel::alterarEmail,
        aoAlterarSenha = viewModel::alterarSenha,
        aoCadastrar = viewModel::cadastrar,
        aoVoltar = aoVoltar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadastroTela(
    estado: CadastroUiState,
    aoAlterarNome: (String) -> Unit,
    aoAlterarUsuario: (String) -> Unit,
    aoAlterarEmail: (String) -> Unit,
    aoAlterarSenha: (String) -> Unit,
    aoCadastrar: () -> Unit,
    aoVoltar: () -> Unit,
) {
    val foco = LocalFocusManager.current
    val proximo = KeyboardActions(onNext = { foco.moveFocus(FocusDirection.Down) })
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cadastro_titulo)) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = estado.nome,
                    onValueChange = aoAlterarNome,
                    label = { Text(stringResource(R.string.cadastro_nome)) },
                    singleLine = true,
                    enabled = !estado.cadastrando,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = proximo,
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PersonFullName },
                )
                OutlinedTextField(
                    value = estado.usuario,
                    onValueChange = aoAlterarUsuario,
                    label = { Text(stringResource(R.string.login_usuario)) },
                    singleLine = true,
                    enabled = !estado.cadastrando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    keyboardActions = proximo,
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewUsername },
                )
                OutlinedTextField(
                    value = estado.email,
                    onValueChange = aoAlterarEmail,
                    label = { Text(stringResource(R.string.cadastro_email)) },
                    singleLine = true,
                    enabled = !estado.cadastrando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    keyboardActions = proximo,
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
                )
                OutlinedTextField(
                    value = estado.senha,
                    onValueChange = aoAlterarSenha,
                    label = { Text(stringResource(R.string.login_senha)) },
                    supportingText = { Text(stringResource(R.string.cadastro_senha_dica)) },
                    singleLine = true,
                    enabled = !estado.cadastrando,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { aoCadastrar() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword },
                )
                estado.erro?.let {
                    Text(
                        it.resolver(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(onClick = aoCadastrar, enabled = !estado.cadastrando, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (estado.cadastrando) R.string.cadastro_cadastrando else R.string.cadastro_cadastrar))
                }
                TextButton(onClick = aoVoltar, enabled = !estado.cadastrando, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cadastro_ja_tem_conta))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CadastroTelaPreview() {
    ConversaTema {
        CadastroTela(
            estado = CadastroUiState(nome = "Ana Souza", usuario = "ana"),
            aoAlterarNome = {},
            aoAlterarUsuario = {},
            aoAlterarEmail = {},
            aoAlterarSenha = {},
            aoCadastrar = {},
            aoVoltar = {},
        )
    }
}
