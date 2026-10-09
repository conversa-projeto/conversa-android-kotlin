package com.conversa.app.feature.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * Perfil (8.3, FC-802, AUT-06/07/08), como a aba "Usuário" do `ProfileSettingsModal.vue`:
 * "Foto de perfil" (Photo Picker → quadrado 256×256 → JPEG), "Dados do usuário" (nome e
 * e-mail) e "Senha" (atual, nova e confirmação). As senhas ficam só nos campos da tela.
 */
@Composable
fun PerfilRotaTela(aoVoltar: () -> Unit, viewModel: PerfilViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val escolherFoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.trocarFoto(uri)
    }
    PerfilTela(
        estado = estado,
        acoes = AcoesPerfil(
            aoVoltar = aoVoltar,
            aoAlterarFoto = { escolherFoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            aoRemoverFoto = viewModel::removerFoto,
            aoFotoFalhar = viewModel::fotoFalhou,
            aoSalvarDados = viewModel::salvarDados,
            aoSalvarSenha = viewModel::salvarSenha,
        ),
    )
}

class AcoesPerfil(
    val aoVoltar: () -> Unit = {},
    val aoAlterarFoto: () -> Unit = {},
    val aoRemoverFoto: () -> Unit = {},
    val aoFotoFalhar: () -> Unit = {},
    val aoSalvarDados: (nome: String, email: String) -> Unit = { _, _ -> },
    val aoSalvarSenha: (atual: String, nova: String, confirmacao: String) -> Unit = { _, _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PerfilTela(estado: PerfilUiState, acoes: AcoesPerfil) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perfil)) },
                navigationIcon = {
                    IconButton(onClick = acoes.aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.perfil_voltar))
                    }
                },
            )
        },
    ) { margens ->
        Column(
            // Com o teclado aberto, a área encolhe e o campo em foco aparece (os de senha ficam no fim).
            Modifier.fillMaxSize().padding(margens).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BlocoFoto(estado, acoes)
            HorizontalDivider()
            BlocoDados(estado, acoes)
            HorizontalDivider()
            BlocoSenha(estado, acoes)
        }
    }
}

@Composable
private fun Titulo(titulo: String, texto: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleSmall)
        Text(texto, style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario)
    }
}

@Composable
private fun TextoAviso(aviso: Aviso?) {
    aviso ?: return
    val texto = aviso.texto ?: aviso.recurso?.let { stringResource(it) } ?: return
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = if (aviso.ok) ConversaTema.cores.chamadaAtender else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun BlocoFoto(estado: PerfilUiState, acoes: AcoesPerfil) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(estado.nome, estado.fotoUrl, tamanho = 80.dp, aoFalhar = acoes.aoFotoFalhar)
            if (estado.enviandoFoto) CircularProgressIndicator(Modifier.size(80.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Titulo(stringResource(R.string.perfil_foto), stringResource(R.string.perfil_foto_texto))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !estado.enviandoFoto, onClick = acoes.aoAlterarFoto) {
                    Text(stringResource(if (estado.enviandoFoto) R.string.perfil_enviando else R.string.perfil_alterar_foto))
                }
                if (estado.temFoto) {
                    TextButton(enabled = !estado.enviandoFoto, onClick = acoes.aoRemoverFoto) {
                        Text(stringResource(R.string.perfil_remover_foto), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            TextoAviso(estado.avisoFoto)
        }
    }
}

@Composable
private fun BlocoDados(estado: PerfilUiState, acoes: AcoesPerfil) {
    // Os campos começam com o que está na sessão e ficam em estado local (o cursor não pula).
    var nome by rememberSaveable(estado.nome) { mutableStateOf(estado.nome) }
    var email by rememberSaveable(estado.email) { mutableStateOf(estado.email) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Titulo(stringResource(R.string.perfil_dados), stringResource(R.string.perfil_dados_texto))
        OutlinedTextField(
            value = nome,
            onValueChange = { nome = it },
            label = { Text(stringResource(R.string.perfil_nome)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.perfil_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        TextoAviso(estado.avisoDados)
        Button(enabled = !estado.salvandoDados, onClick = { acoes.aoSalvarDados(nome, email) }, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(if (estado.salvandoDados) R.string.perfil_salvando else R.string.perfil_salvar_dados))
        }
    }
}

@Composable
private fun BlocoSenha(estado: PerfilUiState, acoes: AcoesPerfil) {
    // Senha só aqui: não sobrevive a girar a tela de propósito (não vai para o estado salvo).
    var atual by remember { mutableStateOf("") }
    var nova by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    LaunchedEffect(estado.senhasTrocadas) {
        if (estado.senhasTrocadas > 0) {
            atual = ""
            nova = ""
            confirmacao = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Titulo(stringResource(R.string.perfil_senha), stringResource(R.string.perfil_senha_texto))
        CampoSenha(atual, { atual = it }, stringResource(R.string.perfil_senha_atual))
        CampoSenha(nova, { nova = it }, stringResource(R.string.perfil_senha_nova))
        CampoSenha(confirmacao, { confirmacao = it }, stringResource(R.string.perfil_senha_confirmar))
        TextoAviso(estado.avisoSenha)
        Button(
            enabled = !estado.salvandoSenha,
            onClick = { acoes.aoSalvarSenha(atual, nova, confirmacao) },
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(if (estado.salvandoSenha) R.string.perfil_salvando else R.string.perfil_salvar_senha))
        }
    }
}

@Composable
private fun CampoSenha(valor: String, aoMudar: (String) -> Unit, rotulo: String) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(rotulo) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
