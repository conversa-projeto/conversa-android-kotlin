package com.conversa.app.inicio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.R
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.RealtimeClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InicioViewModel @Inject constructor(servidor: ServidorRepositorio, tempoReal: RealtimeClient) : ViewModel() {
    val servidor = servidor.atual
    val conexao = tempoReal.estado
}

/** Tela provisória da fundação: mostra o servidor e o estado do tempo real. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioTela(aoTrocarServidor: () -> Unit, viewModel: InicioViewModel = hiltViewModel()) {
    val servidor by viewModel.servidor.collectAsStateWithLifecycle()
    val conexao by viewModel.conexao.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.inicio_titulo)) }) },
    ) { margens ->
        Column(
            modifier = Modifier.fillMaxSize().padding(margens).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.inicio_em_construcao), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.inicio_servidor, servidor?.base?.toString().orEmpty()))
            Text(
                stringResource(
                    when (conexao) {
                        EstadoConexao.DESCONECTADO -> R.string.tempo_real_desconectado
                        EstadoConexao.CONECTANDO -> R.string.tempo_real_conectando
                        EstadoConexao.CONECTADO -> R.string.tempo_real_conectado
                        EstadoConexao.AGUARDANDO -> R.string.tempo_real_aguardando
                    },
                ),
            )
            OutlinedButton(onClick = aoTrocarServidor) { Text(stringResource(R.string.inicio_trocar_servidor)) }
        }
    }
}
