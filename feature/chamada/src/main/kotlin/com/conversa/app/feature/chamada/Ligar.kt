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
}

/**
 * Ação dos botões de voz e vídeo: pede o microfone (e a câmera, no vídeo) na hora
 * e liga. Sem a câmera, liga só com áudio; sem o microfone, não liga.
 */
@Composable
fun rememberLigar(conversa: Conversa?, viewModel: LigarViewModel = hiltViewModel()): (TipoChamada) -> Unit {
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val conversaAtual by rememberUpdatedState(conversa)
    var pendente by remember { mutableStateOf<TipoChamada?>(null) }
    val permissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val tipo = pendente ?: return@rememberLauncherForActivityResult
        pendente = null
        val alvo = conversaAtual ?: return@rememberLauncherForActivityResult
        if (contexto.tem(Manifest.permission.RECORD_AUDIO)) {
            viewModel.ligar(alvo, tipo)
        } else {
            escopo.launch { avisos.showSnackbar(recursos.getString(R.string.sem_microfone)) }
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.avisos.collect { aviso -> avisos.showSnackbar(textoDoAviso(aviso, recursos::getString)) }
    }
    return { tipo ->
        conversaAtual?.let { alvo ->
            val faltam = listOfNotNull(
                Manifest.permission.RECORD_AUDIO.takeUnless { contexto.tem(it) },
                Manifest.permission.CAMERA.takeIf { tipo == TipoChamada.VIDEO && !contexto.tem(it) },
            )
            if (faltam.isEmpty()) {
                viewModel.ligar(alvo, tipo)
            } else {
                pendente = tipo
                permissoes.launch(faltam.toTypedArray())
            }
        }
    }
}

internal fun Context.tem(permissao: String) = ContextCompat.checkSelfPermission(this, permissao) == PackageManager.PERMISSION_GRANTED

internal fun textoDoAviso(aviso: AvisoChamada, texto: (Int) -> String): String = when (aviso) {
    AvisoChamada.JaEmChamada -> texto(R.string.ja_existe_chamada)
    AvisoChamada.MicrofoneIndisponivel -> texto(R.string.sem_microfone)
    is AvisoChamada.Falhou -> aviso.detalhe?.let { texto(R.string.falha_chamada_detalhe).format(it) } ?: texto(R.string.falha_chamada)
}
