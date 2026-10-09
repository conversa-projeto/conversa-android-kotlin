package com.conversa.app.principal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.R
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.DialogoConfirmacao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConfiguracoesViewModel @Inject constructor(
    sessao: SessaoRepositorio,
    servidor: ServidorRepositorio,
    private val autenticacao: AutenticacaoRepositorio,
    private val perfil: PerfilRepositorio,
) : ViewModel() {
    val sessao = sessao.sessao
    val servidor = servidor.atual

    /** A foto do perfil (8.3): a URL assinada acompanha o identificador da sessão. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fotoUrl: StateFlow<String?> = sessao.sessao
        .map { it?.avatarIdentificador }
        .distinctUntilChanged()
        .mapLatest { identificador -> identificador?.let { perfil.urlDaFoto(it).getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _saindo = MutableStateFlow(false)
    val saindo = _saindo.asStateFlow()

    /** AUT-05. O app volta ao login quando a sessão acaba ([com.conversa.app.MainViewModel]). */
    fun sair() {
        if (_saindo.value) return
        _saindo.value = true
        viewModelScope.launch { autenticacao.sair() }
    }
}

/**
 * Aba "Configurações" provisória: quem está logado, o servidor e "Sair".
 * Perfil, senha, notificações e o restante chegam na etapa 8 (`:feature:config`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguracoesProvisorias(
    aoAbrirPerfil: () -> Unit,
    aoTrocarServidor: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfiguracoesViewModel = hiltViewModel(),
) {
    val sessao by viewModel.sessao.collectAsStateWithLifecycle()
    val servidor by viewModel.servidor.collectAsStateWithLifecycle()
    val fotoUrl by viewModel.fotoUrl.collectAsStateWithLifecycle()
    val saindo by viewModel.saindo.collectAsStateWithLifecycle()
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        TopAppBar(title = { Text(stringResource(R.string.aba_configuracoes)) })
        // Toque na linha do usuário: o perfil (foto, nome e e-mail, senha — 8.3).
        Row(
            modifier = Modifier.fillMaxWidth().clickable(
                onClickLabel = stringResource(R.string.config_perfil),
                onClick = aoAbrirPerfil,
            ).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(nome = sessao?.nome, url = fotoUrl, tamanho = 56.dp)
            Column {
                Text(sessao?.nome.orEmpty(), style = MaterialTheme.typography.titleMedium)
                sessao?.email?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.config_servidor)) },
            supportingContent = { Text(servidor?.base?.toString().orEmpty()) },
            leadingContent = { Icon(Icons.Outlined.Dns, contentDescription = null) },
            modifier = Modifier.clickable(onClick = aoTrocarServidor),
        )
        ListItem(
            headlineContent = { Text(stringResource(if (saindo) R.string.config_saindo else R.string.config_sair)) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null) },
            modifier = Modifier.clickable(enabled = !saindo) { confirmarSaida = true },
        )
        Text(
            stringResource(R.string.config_em_breve),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    if (confirmarSaida) {
        DialogoConfirmacao(
            titulo = stringResource(R.string.config_sair),
            mensagem = stringResource(R.string.config_sair_confirmar),
            textoConfirmar = stringResource(R.string.config_sair),
            perigo = true,
            aoConfirmar = {
                confirmarSaida = false
                viewModel.sair()
            },
            aoCancelar = { confirmarSaida = false },
        )
    }
}
