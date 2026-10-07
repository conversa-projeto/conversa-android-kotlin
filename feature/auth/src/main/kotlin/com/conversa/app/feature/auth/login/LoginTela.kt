package com.conversa.app.feature.auth.login

import androidx.annotation.Keep
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.core.ui.estado.resolver
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.auth.R

/** Aviso que a tela de login mostra ao abrir. Vai na rota tipada: @Keep para o R8 não quebrar o serializer. */
@Keep
enum class AvisoLogin { NENHUM, SESSAO_EXPIRADA, CONTA_CRIADA }

@Composable
fun LoginRotaTela(
    usuarioInicial: String?,
    aviso: AvisoLogin,
    aoEntrar: () -> Unit,
    aoCriarConta: () -> Unit,
    aoTrocarServidor: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = LocalAvisos.current
    val textoAviso = when (aviso) {
        AvisoLogin.NENHUM -> null
        AvisoLogin.SESSAO_EXPIRADA -> stringResource(R.string.login_sessao_expirada)
        AvisoLogin.CONTA_CRIADA -> stringResource(R.string.cadastro_sucesso)
    }
    LaunchedEffect(usuarioInicial) { usuarioInicial?.let(viewModel::preencherUsuario) }
    LaunchedEffect(textoAviso) { textoAviso?.let { avisos.showSnackbar(it, withDismissAction = true) } }
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            EventoLogin.Entrou -> aoEntrar()
        }
    }
    LoginTela(
        estado = estado,
        aoAlterarUsuario = viewModel::alterarUsuario,
        aoAlterarSenha = viewModel::alterarSenha,
        aoEntrar = viewModel::entrar,
        aoCriarConta = aoCriarConta,
        aoTrocarServidor = aoTrocarServidor,
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LoginTela(
    estado: LoginUiState,
    aoAlterarUsuario: (String) -> Unit,
    aoAlterarSenha: (String) -> Unit,
    aoEntrar: () -> Unit,
    aoCriarConta: () -> Unit,
    aoTrocarServidor: () -> Unit,
) {
    var senhaVisivel by rememberSaveable { mutableStateOf(false) }
    val focoUsuario = remember { FocusRequester() }
    val focoSenha = remember { FocusRequester() }
    // Foco inicial no usuário (como o web); se já vem preenchido, na senha.
    LaunchedEffect(Unit) { runCatching { if (estado.usuario.isEmpty()) focoUsuario.requestFocus() else focoSenha.requestFocus() } }

    Scaffold { margens ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(margens)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Outlined.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Text(
                stringResource(R.string.app_nome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = estado.usuario,
                    onValueChange = aoAlterarUsuario,
                    label = { Text(stringResource(R.string.login_usuario)) },
                    singleLine = true,
                    enabled = !estado.entrando,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focoSenha.requestFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focoUsuario)
                        .semantics { contentType = ContentType.Username },
                )
                OutlinedTextField(
                    value = estado.senha,
                    onValueChange = aoAlterarSenha,
                    label = { Text(stringResource(R.string.login_senha)) },
                    singleLine = true,
                    enabled = !estado.entrando,
                    visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                            Icon(
                                if (senhaVisivel) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = stringResource(if (senhaVisivel) R.string.senha_esconder else R.string.senha_mostrar),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { aoEntrar() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focoSenha)
                        .semantics { contentType = ContentType.Password },
                )
                estado.erro?.let {
                    Text(
                        it.resolver(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(onClick = aoEntrar, enabled = !estado.entrando, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (estado.entrando) R.string.login_entrando else R.string.login_entrar))
                }
                TextButton(onClick = aoCriarConta, enabled = !estado.entrando, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_criar_conta))
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.login_servidor, estado.servidor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = aoTrocarServidor, enabled = !estado.entrando) { Text(stringResource(R.string.login_trocar_servidor)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginTelaPreview() {
    ConversaTema {
        LoginTela(
            estado = LoginUiState(usuario = "ana", erro = TextoUi.Literal("Senha incorreta!"), servidor = "192.168.2.5"),
            aoAlterarUsuario = {},
            aoAlterarSenha = {},
            aoEntrar = {},
            aoCriarConta = {},
            aoTrocarServidor = {},
        )
    }
}
