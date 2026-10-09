package com.conversa.app.feature.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.PreferenciaTema
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.DialogoConfirmacao

/** Para onde cada item da lista leva (as telas ficam no `app`, na navegação). */
class AcoesConfiguracoes(
    val aoAbrirPerfil: () -> Unit = {},
    val aoTrocarServidor: () -> Unit = {},
    val aoAbrirSobre: () -> Unit = {},
    val aoAbrirNotificacoes: () -> Unit = {},
    val aoAbrirPermissoes: () -> Unit = {},
    val aoAbrirChamadas: () -> Unit = {},
    val aoFotoFalhar: () -> Unit = {},
    val aoAlterarTema: (PreferenciaTema) -> Unit = {},
    val aoSair: () -> Unit = {},
)

@Composable
fun ConfiguracoesRotaTela(
    aoAbrirPerfil: () -> Unit,
    aoTrocarServidor: () -> Unit,
    aoAbrirSobre: () -> Unit,
    aoAbrirNotificacoes: () -> Unit,
    aoAbrirPermissoes: () -> Unit,
    aoAbrirChamadas: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfiguracoesViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    ConfiguracoesTela(
        estado = estado,
        acoes = AcoesConfiguracoes(
            aoAbrirPerfil = aoAbrirPerfil,
            aoTrocarServidor = aoTrocarServidor,
            aoAbrirSobre = aoAbrirSobre,
            aoAbrirNotificacoes = aoAbrirNotificacoes,
            aoAbrirPermissoes = aoAbrirPermissoes,
            aoAbrirChamadas = aoAbrirChamadas,
            aoFotoFalhar = viewModel::fotoFalhou,
            aoAlterarTema = viewModel::alterarTema,
            aoSair = viewModel::sair,
        ),
        modifier = modifier,
    )
}

/**
 * Configurações (8.5, CFG-01), como a lista de abas do web no celular: quem está logado
 * (toque → perfil) e as seções, cada uma com o resumo do que está valendo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfiguracoesTela(estado: ConfiguracoesUiState, acoes: AcoesConfiguracoes, modifier: Modifier = Modifier) {
    var escolherTema by rememberSaveable { mutableStateOf(false) }
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        // Dentro da tela principal: a barra inferior já cuida da área do sistema embaixo.
        contentWindowInsets = WindowInsets.statusBars,
        // Superfície branca, como a lista de conversas: o fundo da inicial (#F5F5F5) precisa de contraste.
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.config_titulo)) }) },
    ) { margens ->
        Column(Modifier.padding(margens).verticalScroll(rememberScrollState())) {
            LinhaUsuario(estado, acoes)
            HorizontalDivider()
            Item(
                icone = Icons.Outlined.Palette,
                titulo = stringResource(R.string.config_aparencia),
                resumo = stringResource(textoDoTema(estado.tema)),
                aoTocar = { escolherTema = true },
            )
            Item(
                Icons.Outlined.Notifications,
                stringResource(R.string.config_notificacoes),
                stringResource(R.string.config_notificacoes_resumo),
                acoes.aoAbrirNotificacoes,
            )
            Item(
                Icons.Outlined.Call,
                stringResource(R.string.config_chamadas),
                stringResource(R.string.config_chamadas_resumo),
                acoes.aoAbrirChamadas,
            )
            Item(
                Icons.Outlined.VerifiedUser,
                stringResource(R.string.config_permissoes),
                stringResource(R.string.config_permissoes_resumo),
                acoes.aoAbrirPermissoes,
            )
            Item(Icons.Outlined.Dns, stringResource(R.string.config_servidor), estado.servidor, acoes.aoTrocarServidor)
            Item(Icons.Outlined.Info, stringResource(R.string.config_sobre), versaoDoApp(), acoes.aoAbrirSobre)
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(if (estado.saindo) R.string.config_saindo else R.string.config_sair),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                modifier = Modifier.clickable(enabled = !estado.saindo) { confirmarSaida = true },
            )
        }
    }

    if (escolherTema) {
        EscolherTema(
            atual = estado.tema,
            aoEscolher = {
                escolherTema = false
                acoes.aoAlterarTema(it)
            },
            aoFechar = { escolherTema = false },
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
                acoes.aoSair()
            },
            aoCancelar = { confirmarSaida = false },
        )
    }
}

@Composable
private fun LinhaUsuario(estado: ConfiguracoesUiState, acoes: AcoesConfiguracoes) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.config_perfil), onClick = acoes.aoAbrirPerfil)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(nome = estado.nome, url = estado.fotoUrl, tamanho = 56.dp, aoFalhar = acoes.aoFotoFalhar)
        Column(Modifier.weight(1f)) {
            Text(estado.nome, style = MaterialTheme.typography.titleMedium)
            estado.email?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Item(icone: ImageVector, titulo: String, resumo: String, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = { Text(resumo) },
        leadingContent = { Icon(icone, contentDescription = null) },
        modifier = Modifier.clickable(onClick = aoTocar),
    )
}

internal fun textoDoTema(tema: PreferenciaTema): Int = when (tema) {
    PreferenciaTema.SISTEMA -> R.string.config_tema_sistema
    PreferenciaTema.CLARO -> R.string.config_tema_claro
    PreferenciaTema.ESCURO -> R.string.config_tema_escuro
}

/** "Seguir o sistema" / "Claro" / "Escuro": vale na hora, em todas as telas (CFG-02). */
@Composable
private fun EscolherTema(atual: PreferenciaTema, aoEscolher: (PreferenciaTema) -> Unit, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.config_tema)) },
        text = {
            Column(Modifier.selectableGroup()) {
                PreferenciaTema.entries.forEach { opcao ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = opcao == atual, role = Role.RadioButton) { aoEscolher(opcao) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = opcao == atual, onClick = null)
                        Text(stringResource(textoDoTema(opcao)), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = aoFechar) { Text(stringResource(com.conversa.app.core.ui.R.string.cancelar)) }
        },
    )
}

/** "Versão 1.2.3" a partir do pacote instalado. */
@Composable
internal fun versaoDoApp(): String {
    val contexto = LocalContext.current
    val versao = runCatching { contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName }.getOrNull()
    return stringResource(R.string.config_versao, versao.orEmpty())
}
