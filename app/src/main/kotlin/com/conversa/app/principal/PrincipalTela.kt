package com.conversa.app.principal

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.R
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.FaixaSemConexao
import com.conversa.app.core.ui.componentes.LocalAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PrincipalViewModel @Inject constructor(
    sincronizacao: SyncManager,
    private val conexao: ConexaoTempoReal,
    iniciador: IniciadorSessao,
    private val preferencias: PreferenciasStore,
) : ViewModel() {
    val atividadesNovas = sincronizacao.atividadesNovas
    val semTempoReal = conexao.semTempoReal
    val falhaInicio = iniciador.falha

    /** `null` enquanto carrega (não mostra nada até saber). */
    val pediuNotificacoes: StateFlow<Boolean?> = preferencias.pediuNotificacoes.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun tentarAgora() = conexao.tentarAgora()

    fun notificacoesPedidas() {
        viewModelScope.launch { preferencias.marcarPediuNotificacoes() }
    }

    val pediuTelaCheia: StateFlow<Boolean?> = preferencias.pediuTelaCheia.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun telaCheiaPedida() {
        viewModelScope.launch { preferencias.marcarPediuTelaCheia() }
    }
}

/** Abas da barra inferior (TODO 2.5). */
enum class Aba(@StringRes val rotulo: Int, val icone: ImageVector) {
    CONVERSAS(R.string.aba_conversas, Icons.AutoMirrored.Outlined.Chat),
    CHAMADAS(R.string.aba_chamadas, Icons.Outlined.Call),
    ATIVIDADES(R.string.aba_atividades, Icons.Outlined.Notifications),
    CONFIGURACOES(R.string.aba_configuracoes, Icons.Outlined.Settings),
}

/**
 * Tela principal: faixa de conexão no topo, conteúdo da aba e a barra inferior.
 * Erro ao carregar a sessão (que não seja 401) aparece no Snackbar com
 * "(tentando de novo…)" até dar certo (AUT-03).
 */
@Composable
fun PrincipalTela(
    conversas: @Composable (Modifier) -> Unit,
    chamadas: @Composable (Modifier) -> Unit,
    configuracoes: @Composable (Modifier) -> Unit,
    viewModel: PrincipalViewModel = hiltViewModel(),
) {
    var aba by rememberSaveable { mutableStateOf(Aba.CONVERSAS) }
    val atividadesNovas by viewModel.atividadesNovas.collectAsStateWithLifecycle()
    val semTempoReal by viewModel.semTempoReal.collectAsStateWithLifecycle()
    val falha by viewModel.falhaInicio.collectAsStateWithLifecycle()
    val pediuNotificacoes by viewModel.pediuNotificacoes.collectAsStateWithLifecycle()
    PedidoNotificacoes(pediuNotificacoes, viewModel::notificacoesPedidas)
    // Depois do pedido de notificações, para não abrir dois diálogos juntos.
    if (pediuNotificacoes == true) {
        val pediuTelaCheia by viewModel.pediuTelaCheia.collectAsStateWithLifecycle()
        PedidoTelaCheia(pediuTelaCheia, viewModel::telaCheiaPedida)
    }
    val avisos = LocalAvisos.current
    val tentando = stringResource(R.string.tentando_de_novo)
    val textoFalha = falha?.mensagemAmigavel()

    LaunchedEffect(textoFalha) {
        if (textoFalha == null) {
            avisos.currentSnackbarData?.takeIf { it.visuals.message.endsWith(tentando) }?.dismiss()
        } else {
            avisos.currentSnackbarData?.dismiss()
            avisos.showSnackbar("$textoFalha $tentando", duration = SnackbarDuration.Indefinite)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { item ->
                    NavigationBarItem(
                        selected = aba == item,
                        onClick = { aba = item },
                        icon = {
                            if (item == Aba.ATIVIDADES && atividadesNovas > 0) {
                                val descricao = pluralStringResource(R.plurals.atividades_novas, atividadesNovas, atividadesNovas)
                                BadgedBox(badge = { Badge { Text(if (atividadesNovas > 99) "99+" else atividadesNovas.toString()) } }) {
                                    Icon(
                                        item.icone,
                                        contentDescription = null,
                                        modifier = Modifier.semantics {
                                            contentDescription =
                                                descricao
                                        },
                                    )
                                }
                            } else {
                                Icon(item.icone, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(item.rotulo)) },
                    )
                }
            }
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(bottom = margens.calculateBottomPadding())) {
            FaixaSemConexao(visivel = semTempoReal, aoTentarAgora = viewModel::tentarAgora, modifier = Modifier.statusBarsPadding())
            val conteudo = Modifier.weight(1f)
            when (aba) {
                Aba.CONVERSAS -> conversas(conteudo)
                Aba.CHAMADAS -> chamadas(conteudo)
                Aba.ATIVIDADES -> EstadoVazio(
                    titulo = stringResource(R.string.atividades_em_breve),
                    descricao = stringResource(R.string.em_breve_descricao),
                    icone = Icons.Outlined.Notifications,
                    modifier = conteudo.statusBarsPadding(),
                )
                Aba.CONFIGURACOES -> configuracoes(conteudo)
            }
        }
    }
}

/**
 * Depois do login, uma vez (FC-607, NOT-06): explica e pede `POST_NOTIFICATIONS`
 * (Android 13+). "Agora não" também conta como pedido: não insiste a cada abertura.
 */
@Composable
private fun PedidoNotificacoes(jaPediu: Boolean?, aoPedir: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || jaPediu != false) return
    val contexto = LocalContext.current
    if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        LaunchedEffect(Unit) { aoPedir() }
        return
    }
    var mostrar by rememberSaveable { mutableStateOf(true) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { aoPedir() }
    if (!mostrar) return
    AlertDialog(
        onDismissRequest = {
            mostrar = false
            aoPedir()
        },
        title = { Text(stringResource(R.string.permissao_notificacoes_titulo)) },
        text = { Text(stringResource(R.string.permissao_notificacoes_texto)) },
        confirmButton = {
            TextButton(onClick = {
                mostrar = false
                pedir.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.permissao_notificacoes_permitir)) }
        },
        dismissButton = {
            TextButton(onClick = {
                mostrar = false
                aoPedir()
            }) { Text(stringResource(R.string.permissao_notificacoes_agora_nao)) }
        },
    )
}

/**
 * Android 14+ (TODO 6.5): a chamada recebida só aparece em tela cheia (inclusive na tela
 * bloqueada) com a permissão "tela cheia". Se estiver negada, explica uma vez e leva às
 * configurações; a pessoa decide lá.
 */
@Composable
private fun PedidoTelaCheia(jaPediu: Boolean?, aoPedir: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || jaPediu != false) return
    val contexto = LocalContext.current
    if (contexto.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false) {
        LaunchedEffect(Unit) { aoPedir() }
        return
    }
    var mostrar by rememberSaveable { mutableStateOf(true) }
    if (!mostrar) return
    val fechar = {
        mostrar = false
        aoPedir()
    }
    AlertDialog(
        onDismissRequest = fechar,
        title = { Text(stringResource(R.string.permissao_tela_cheia_titulo)) },
        text = { Text(stringResource(R.string.permissao_tela_cheia_texto)) },
        confirmButton = {
            TextButton(onClick = {
                fechar()
                val configuracoes =
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.fromParts("package", contexto.packageName, null),
                    )
                try {
                    contexto.startActivity(configuracoes)
                } catch (_: ActivityNotFoundException) {
                }
            }) { Text(stringResource(R.string.permissao_tela_cheia_abrir)) }
        },
        dismissButton = { TextButton(onClick = fechar) { Text(stringResource(R.string.permissao_notificacoes_agora_nao)) } },
    )
}
