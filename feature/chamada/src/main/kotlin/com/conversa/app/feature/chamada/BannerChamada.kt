package com.conversa.app.feature.chamada

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BannerChamadaViewModel @Inject constructor(gerenciador: GerenciadorChamadas) : ViewModel() {
    val estado = gerenciador.estado
}

/**
 * Durante a chamada, as outras telas do app ganham no topo a faixa "Toque para voltar à
 * chamada" (TODO 6.12); o toque reabre a tela da chamada. A faixa ocupa a barra de status,
 * e as telas embaixo não a descontam de novo.
 */
@Composable
fun ComBannerDaChamada(viewModel: BannerChamadaViewModel = hiltViewModel(), conteudo: @Composable () -> Unit) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mostrar = estado.fase == FaseChamada.CHAMANDO || estado.fase == FaseChamada.CONECTANDO || estado.fase == FaseChamada.ATIVA
    val contexto = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        if (mostrar) {
            Row(
                Modifier.fillMaxWidth()
                    .background(ConversaTema.cores.chamadaAtender)
                    .statusBarsPadding()
                    .clickable(role = Role.Button) { contexto.startActivity(ChamadaActivity.intencao(contexto)) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.toque_para_voltar),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                estado.ativaDesde?.takeIf { estado.fase == FaseChamada.ATIVA }?.let { Duracao(it, cor = Color.White) }
            }
        }
        Box(Modifier.weight(1f).then(if (mostrar) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier)) {
            conteudo()
        }
    }
}
