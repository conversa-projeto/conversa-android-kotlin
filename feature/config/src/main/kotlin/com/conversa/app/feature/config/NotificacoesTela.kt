package com.conversa.app.feature.config

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.notificacoes.CanaisNotificacao
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.ui.componentes.Interruptor
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Os canais na ordem em que aparecem (os mesmos criados no `Application`). */
private val ORDEM_DOS_CANAIS = listOf(
    CanaisNotificacao.MENSAGENS,
    CanaisNotificacao.CHAMADAS_RECEBIDAS,
    CanaisNotificacao.CHAMADA_ATIVA,
    CanaisNotificacao.SISTEMA,
    CanaisNotificacao.CONEXAO,
)

@HiltViewModel
class NotificacoesViewModel @Inject constructor(private val preferencias: PreferenciasRepositorio) : ViewModel() {
    /** "Receber com o app fechado" (ADR 0002, opção A). */
    val conexaoSegundoPlano: StateFlow<Boolean> = preferencias.conexaoSegundoPlano.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun alterarConexaoSegundoPlano(ligada: Boolean) {
        viewModelScope.launch { preferencias.alterarConexaoSegundoPlano(ligada) }
    }
}

/** Um canal como o sistema o vê agora: nome e descrição já no idioma do aparelho. */
private data class Canal(val id: String, val nome: String, val descricao: String?, val ligado: Boolean)

/**
 * Notificações (8.5, FC-806): as do app e as de cada canal (mensagens, chamadas recebidas,
 * chamada em andamento, sistema), ligadas ou não. Som, vibração e estilo se mudam nas
 * configurações do Android: cada linha abre a do canal.
 */
@Composable
fun NotificacoesRotaTela(aoVoltar: () -> Unit, viewModel: NotificacoesViewModel = hiltViewModel()) {
    val contexto = LocalContext.current
    val conexaoSegundoPlano by viewModel.conexaoSegundoPlano.collectAsStateWithLifecycle()
    fun semRestricaoBateria() =
        contexto.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(contexto.packageName) != false
    var bateriaLivre by remember { mutableStateOf(semRestricaoBateria()) }
    val gerenciador = remember(contexto) { NotificationManagerCompat.from(contexto) }
    fun lerCanais() = ORDEM_DOS_CANAIS.mapNotNull { id ->
        gerenciador.getNotificationChannelCompat(id)?.let {
            Canal(id, it.name?.toString().orEmpty(), it.description, it.importance != NotificationManagerCompat.IMPORTANCE_NONE)
        }
    }
    var appLigado by remember { mutableStateOf(gerenciador.areNotificationsEnabled()) }
    var canais by remember { mutableStateOf(lerCanais()) }
    LifecycleResumeEffect(Unit) {
        appLigado = gerenciador.areNotificationsEnabled()
        canais = lerCanais()
        bateriaLivre = semRestricaoBateria()
        onPauseOrDispose {}
    }
    SubTela(titulo = stringResource(R.string.notificacoes_titulo), aoVoltar = aoVoltar) { modificador ->
        Column(modificador.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.notificacoes_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.padding(16.dp),
            )
            Linha(
                titulo = stringResource(R.string.notificacoes_do_app),
                descricao = null,
                ligado = appLigado,
                aoTocar = {
                    abrirNoSistema(
                        contexto,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
                    )
                },
            )
            HorizontalDivider()
            AppFechado(
                ligada = conexaoSegundoPlano,
                aoAlterar = viewModel::alterarConexaoSegundoPlano,
                bateriaLivre = bateriaLivre,
                aoAjustarBateria = {
                    @SuppressLint("BatteryLife")
                    val pedido = if (bateriaLivre) {
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    } else {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", contexto.packageName, null))
                    }
                    abrirNoSistema(contexto, pedido)
                },
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.notificacoes_tipos),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            canais.forEach { canal ->
                // Com as do app desligadas, nenhum canal aparece, mesmo ligado.
                Linha(
                    titulo = canal.nome,
                    descricao = canal.descricao,
                    ligado = appLigado && canal.ligado,
                    aoTocar = {
                        abrirNoSistema(
                            contexto,
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, canal.id),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Sem FCM (ADR 0002, opção A): a chave da conexão em segundo plano e, ligada, se o Android
 * deixa o app usar a rede com a tela apagada (otimização de bateria).
 */
@Composable
private fun AppFechado(ligada: Boolean, aoAlterar: (Boolean) -> Unit, bateriaLivre: Boolean, aoAjustarBateria: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.notificacoes_app_fechado)) },
        supportingContent = { Text(stringResource(R.string.notificacoes_app_fechado_texto), color = ConversaTema.cores.textoTerciario) },
        trailingContent = { Interruptor(ligada) },
        modifier = Modifier.toggleable(value = ligada, role = Role.Switch, onValueChange = aoAlterar),
    )
    if (!ligada) return
    ListItem(
        headlineContent = { Text(stringResource(R.string.notificacoes_bateria)) },
        supportingContent = {
            Text(
                stringResource(if (bateriaLivre) R.string.notificacoes_bateria_livre else R.string.notificacoes_bateria_restrita),
                color = if (bateriaLivre) ConversaTema.cores.chamadaAtender else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.notificacoes_abrir_no_sistema), onClick = aoAjustarBateria),
    )
}

@Composable
private fun Linha(titulo: String, descricao: String?, ligado: Boolean, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = {
            Column {
                descricao?.let { Text(it, color = ConversaTema.cores.textoTerciario) }
                Text(
                    stringResource(if (ligado) R.string.notificacoes_ativadas else R.string.notificacoes_desativadas),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ligado) ConversaTema.cores.chamadaAtender else MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.notificacoes_abrir_no_sistema), onClick = aoTocar),
    )
}
