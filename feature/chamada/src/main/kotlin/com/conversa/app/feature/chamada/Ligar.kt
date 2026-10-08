package com.conversa.app.feature.chamada

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.ChamadaHistorico
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.ui.componentes.LocalAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Ligar a partir de uma conversa (TODO 6.4, CHA-01). */
@HiltViewModel
class LigarViewModel @Inject constructor(
    private val gerenciador: GerenciadorChamadas,
    private val conversas: ConversasRepositorio,
    private val sessao: SessaoRepositorio,
) : ViewModel() {
    private val falhas = MutableSharedFlow<AvisoChamada>(extraBufferCapacity = 1)

    /** Avisos do gerenciador e a falha ao buscar os membros do grupo. */
    val avisos: Flow<AvisoChamada> = merge(gerenciador.avisos, falhas)

    /** Participantes: direta = [eu, outro]; grupo = todos os membros, inclusive eu (`GET /conversa/usuarios`). */
    fun ligar(conversa: Conversa, tipo: TipoChamada) {
        viewModelScope.launch {
            val eu = sessao.sessao.value?.usuarioId ?: return@launch
            val outros = if (conversa.tipo == TipoConversa.GRUPO) {
                conversas.membros(conversa.id).getOrElse {
                    falhas.tryEmit(AvisoChamada.Falhou(it.message))
                    return@launch
                }.map { it.usuarioId }
            } else {
                listOfNotNull(conversa.destinatarioId)
            }
            gerenciador.ligar(tipo, (outros + eu).distinct(), conversa.id)
        }
    }

    /** Participantes já conhecidos (ex.: do histórico); eu entro sozinho na lista. */
    fun ligarPara(tipo: TipoChamada, participantes: List<Long>, conversaId: Long?) {
        gerenciador.ligar(tipo, participantes, conversaId)
    }
}

/**
 * Ação dos botões de voz e vídeo da conversa: pede o microfone (e a câmera, no vídeo)
 * na hora e liga. Sem a câmera, liga só com áudio; sem o microfone, não liga.
 */
@Composable
fun rememberLigar(conversa: Conversa?, viewModel: LigarViewModel = hiltViewModel()): (TipoChamada) -> Unit {
    val conversaAtual by rememberUpdatedState(conversa)
    val comPermissao = rememberComPermissaoDeLigar(viewModel)
    return { tipo -> conversaAtual?.let { alvo -> comPermissao(tipo) { viewModel.ligar(alvo, tipo) } } }
}

/** "Ligar novamente" do histórico (6.10): mesmo tipo e mesmos participantes. */
@Composable
fun rememberLigarNovamente(viewModel: LigarViewModel = hiltViewModel()): (ChamadaHistorico) -> Unit {
    val comPermissao = rememberComPermissaoDeLigar(viewModel)
    return { chamada ->
        comPermissao(chamada.tipo) { viewModel.ligarPara(chamada.tipo, chamada.participantes.map { it.usuarioId }, chamada.conversaId) }
    }
}

/**
 * Pede o que falta (microfone; câmera no vídeo) e só então liga. Mostra os avisos do
 * gerenciador ("Já existe uma chamada em andamento"…) no Snackbar da tela.
 */
@Composable
private fun rememberComPermissaoDeLigar(viewModel: LigarViewModel): (TipoChamada, () -> Unit) -> Unit {
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    var pendente by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val acao = pendente ?: return@rememberLauncherForActivityResult
        pendente = null
        if (contexto.tem(Manifest.permission.RECORD_AUDIO)) {
            acao()
        } else {
            escopo.launch { avisos.showSnackbar(recursos.getString(R.string.sem_microfone)) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { aviso -> avisos.showSnackbar(textoDoAviso(aviso, recursos::getString)) }
    }
    return { tipo, acao ->
        val faltam = listOfNotNull(
            Manifest.permission.RECORD_AUDIO.takeUnless { contexto.tem(it) },
            Manifest.permission.CAMERA.takeIf { tipo == TipoChamada.VIDEO && !contexto.tem(it) },
        )
        if (faltam.isEmpty()) {
            acao()
        } else {
            pendente = acao
            permissoes.launch(faltam.toTypedArray())
        }
    }
}

internal fun Context.tem(permissao: String) = ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED

internal fun textoDoAviso(aviso: AvisoChamada, texto: (Int) -> String): String = when (aviso) {
    AvisoChamada.JaEmChamada -> texto(R.string.ja_existe_chamada)
    AvisoChamada.MicrofoneIndisponivel -> texto(R.string.sem_microfone)
    is AvisoChamada.Falhou -> aviso.detalhe?.let { texto(R.string.falha_chamada_detalhe).format(it) } ?: texto(R.string.falha_chamada)
}
