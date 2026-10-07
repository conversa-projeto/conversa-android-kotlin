package com.conversa.app.feature.conversas.novaconversa

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.model.avatarDoContato
import com.conversa.app.core.model.contatoCombina
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.feature.conversas.R
import com.conversa.app.feature.conversas.lista.ItemContato
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class NovaConversaUiState(val termo: String = "", val contatos: List<ItemContato> = emptyList(), val abrindo: Boolean = false)

sealed interface EventoNovaConversa {
    data class Abrir(val conversaId: Long) : EventoNovaConversa

    data class Erro(val mensagem: String) : EventoNovaConversa
}

/** Contatos para começar uma conversa (CON-06, CON-11), com o atalho "Novo grupo". */
@HiltViewModel
class NovaConversaViewModel @Inject constructor(
    private val conversas: ConversasRepositorio,
    private val contatos: ContatosRepositorio,
    presenca: PresencaRepositorio,
) : ViewModel() {
    private val termo = MutableStateFlow("")
    private val abrindo = MutableStateFlow(false)
    val eventos = EventosUnicos<EventoNovaConversa>()

    val estado: StateFlow<NovaConversaUiState> = combine(
        contatos.observarOutros(),
        conversas.observarTodas(),
        presenca.online,
        termo,
        abrindo,
    ) { pessoas, lista, online, t, abrindoAgora ->
        NovaConversaUiState(
            termo = t,
            contatos = pessoas.filter { contatoCombina(it, t) }
                .map { ItemContato(it.id, it.nome, it.login, avatarDoContato(it, lista), it.id in online) },
            abrindo = abrindoAgora,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NovaConversaUiState())

    init {
        // A lista de contatos muda pouco; atualiza ao abrir a tela, sem travar nada.
        viewModelScope.launch { contatos.atualizar() }
    }

    fun alterarTermo(texto: String) {
        termo.value = texto
    }

    fun abrir(contatoId: Long) {
        if (abrindo.value) return
        viewModelScope.launch {
            abrindo.value = true
            conversas.obterOuCriarDireta(contatoId)
                .onSuccess { eventos.enviar(EventoNovaConversa.Abrir(it)) }
                .onFailure { eventos.enviar(EventoNovaConversa.Erro(it.paraErroApi().mensagemAmigavel())) }
            abrindo.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovaConversaRotaTela(
    aoAbrirConversa: (Long) -> Unit,
    aoNovoGrupo: () -> Unit,
    aoVoltar: () -> Unit,
    viewModel: NovaConversaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Texto do campo em estado local (síncrono): passar pelo combine/stateIn do ViewModel
    // atrasa um quadro e o cursor pula enquanto se digita.
    var termo by rememberSaveable { mutableStateOf("") }
    val avisos = LocalAvisos.current
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoNovaConversa.Abrir -> aoAbrirConversa(evento.conversaId)
            is EventoNovaConversa.Erro -> avisos.mostrarErro(evento.mensagem)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nova_conversa)) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
            )
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens)) {
            if (estado.abrindo) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = termo,
                onValueChange = {
                    termo = it
                    viewModel.alterarTermo(it)
                },
                placeholder = { Text(stringResource(R.string.contatos_filtrar)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "grupo") {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.novo_grupo), color = MaterialTheme.colorScheme.primary) },
                        leadingContent = {
                            Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable(enabled = !estado.abrindo, onClick = aoNovoGrupo),
                    )
                }
                if (estado.contatos.isEmpty()) {
                    item(key = "vazio") { EstadoVazio(titulo = stringResource(R.string.contatos_vazio)) }
                }
                items(estado.contatos, key = { it.id }) { contato ->
                    ListItem(
                        headlineContent = { Text(contato.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(contato.detalhe, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(contato.nome, contato.avatarUrl, online = contato.online) },
                        modifier = Modifier.clickable(enabled = !estado.abrindo) { viewModel.abrir(contato.id) },
                    )
                }
            }
        }
    }
}
