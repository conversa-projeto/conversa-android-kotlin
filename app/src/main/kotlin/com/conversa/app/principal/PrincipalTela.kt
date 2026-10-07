package com.conversa.app.principal

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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.R
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.FaixaSemConexao
import com.conversa.app.core.ui.componentes.LocalAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PrincipalViewModel @Inject constructor(
    sincronizacao: SyncManager,
    private val conexao: ConexaoTempoReal,
    iniciador: IniciadorSessao,
) : ViewModel() {
    val atividadesNovas = sincronizacao.atividadesNovas
    val semTempoReal = conexao.semTempoReal
    val falhaInicio = iniciador.falha

    fun tentarAgora() = conexao.tentarAgora()
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
    configuracoes: @Composable (Modifier) -> Unit,
    viewModel: PrincipalViewModel = hiltViewModel(),
) {
    var aba by rememberSaveable { mutableStateOf(Aba.CONVERSAS) }
    val atividadesNovas by viewModel.atividadesNovas.collectAsStateWithLifecycle()
    val semTempoReal by viewModel.semTempoReal.collectAsStateWithLifecycle()
    val falha by viewModel.falhaInicio.collectAsStateWithLifecycle()
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
                Aba.CHAMADAS -> EstadoVazio(
                    titulo = stringResource(R.string.chamadas_em_breve),
                    descricao = stringResource(R.string.em_breve_descricao),
                    icone = Icons.Outlined.Call,
                    modifier = conteudo.statusBarsPadding(),
                )
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
