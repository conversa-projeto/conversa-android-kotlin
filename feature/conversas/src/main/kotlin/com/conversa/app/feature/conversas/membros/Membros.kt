package com.conversa.app.feature.conversas.membros

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.DialogoConfirmacao
import com.conversa.app.core.ui.componentes.EstadoErro
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.core.ui.estado.resolver
import com.conversa.app.feature.conversas.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MembrosUiState(
    val carregando: Boolean = true,
    val erroCarga: String? = null,
    /** O servidor devolve lista vazia para quem não é membro. */
    val semAcesso: Boolean = false,
    val nome: String = "",
    val nomeOriginal: String = "",
    val membros: List<MembroConversa> = emptyList(),
    val euId: Long = 0,
    val candidatos: List<Contato> = emptyList(),
    val renomeando: Boolean = false,
    val adicionando: Boolean = false,
    val saindo: Boolean = false,
) {
    val podeRenomear: Boolean get() = nome.isNotBlank() && nome.trim() != nomeOriginal && !renomeando
}

sealed interface EventoMembros {
    data class Aviso(val texto: TextoUi) : EventoMembros

    data object Saiu : EventoMembros
}

/**
 * "Membros do grupo" (CON-08): renomear, adicionar e sair.
 * Não há "remover outra pessoa": o servidor só aceita remover o próprio vínculo
 * (contrato §11.4; o botão do web sempre dá 403).
 */
@HiltViewModel
class MembrosViewModel @Inject constructor(
    salvo: SavedStateHandle,
    private val conversas: ConversasRepositorio,
    contatos: ContatosRepositorio,
    sessao: SessaoRepositorio,
) : ViewModel() {
    private val conversaId: Long = checkNotNull(salvo["conversaId"])
    private val formulario = MutableStateFlow(MembrosUiState(euId = sessao.sessao.value?.usuarioId ?: 0))
    val eventos = EventosUnicos<EventoMembros>()

    val estado: StateFlow<MembrosUiState> = combine(formulario, contatos.observarOutros()) { f, pessoas ->
        val dentro = f.membros.map { it.usuarioId }.toSet()
        f.copy(candidatos = pessoas.filter { it.id !in dentro })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), formulario.value)

    init {
        viewModelScope.launch {
            val conversa = conversas.observar(conversaId).filterNotNull().first()
            formulario.update { it.copy(nome = conversa.descricao.orEmpty(), nomeOriginal = conversa.descricao.orEmpty()) }
        }
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            formulario.update { it.copy(carregando = true, erroCarga = null) }
            conversas.membros(conversaId)
                .onSuccess { lista -> formulario.update { it.copy(carregando = false, membros = lista, semAcesso = lista.isEmpty()) } }
                .onFailure { falha ->
                    formulario.update { it.copy(carregando = false, erroCarga = falha.paraErroApi().mensagemAmigavel()) }
                }
        }
    }

    fun alterarNome(texto: String) = formulario.update { it.copy(nome = texto.take(LIMITE_NOME)) }

    fun renomear() {
        val atual = formulario.value
        if (!atual.podeRenomear) return
        viewModelScope.launch {
            formulario.update { it.copy(renomeando = true) }
            conversas.renomear(conversaId, atual.nome)
                .onSuccess {
                    formulario.update { it.copy(renomeando = false, nomeOriginal = atual.nome.trim(), nome = atual.nome.trim()) }
                    eventos.enviar(EventoMembros.Aviso(TextoUi.Recurso(R.string.membros_renomeado)))
                }
                .onFailure { falha -> falhou(falha) { it.copy(renomeando = false) } }
        }
    }

    fun adicionar(usuarioId: Long) {
        if (formulario.value.adicionando) return
        viewModelScope.launch {
            formulario.update { it.copy(adicionando = true) }
            conversas.adicionarMembro(conversaId, usuarioId)
                .onSuccess {
                    formulario.update { it.copy(adicionando = false) }
                    eventos.enviar(EventoMembros.Aviso(TextoUi.Recurso(R.string.membros_adicionado)))
                    carregar()
                }
                .onFailure { falha -> falhou(falha) { it.copy(adicionando = false) } }
        }
    }

    fun sair() {
        val atual = formulario.value
        val meuVinculo = atual.membros.firstOrNull { it.usuarioId == atual.euId }?.vinculoId ?: return
        if (atual.saindo) return
        viewModelScope.launch {
            formulario.update { it.copy(saindo = true) }
            conversas.sair(conversaId, meuVinculo)
                .onSuccess { eventos.enviar(EventoMembros.Saiu) }
                .onFailure { falha -> falhou(falha) { it.copy(saindo = false) } }
        }
    }

    private fun falhou(falha: Throwable, desfazer: (MembrosUiState) -> MembrosUiState) {
        formulario.update(desfazer)
        eventos.enviar(EventoMembros.Aviso(TextoUi.Literal(falha.paraErroApi().mensagemAmigavel())))
    }

    companion object {
        const val LIMITE_NOME = 100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembrosRotaTela(aoSair: () -> Unit, aoVoltar: () -> Unit, viewModel: MembrosViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = LocalAvisos.current
    var escolhendo by rememberSaveable { mutableStateOf(false) }
    var confirmarSaida by rememberSaveable { mutableStateOf(false) }
    // Texto do campo em estado local (síncrono): passar pelo combine/stateIn do ViewModel
    // atrasa um quadro e o cursor pula enquanto se digita. Começa com o nome atual do grupo.
    var nome by rememberSaveable(estado.nomeOriginal) { mutableStateOf(estado.nomeOriginal) }
    val contexto = LocalContext.current

    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoMembros.Aviso -> avisos.showSnackbar(evento.texto.resolver(contexto), withDismissAction = true)
            EventoMembros.Saiu -> aoSair()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.membros_titulo)) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
            )
        },
    ) { margens ->
        when {
            estado.carregando && estado.membros.isEmpty() -> Carregando(Modifier.padding(margens))
            estado.erroCarga != null -> EstadoErro(estado.erroCarga!!, viewModel::carregar, Modifier.padding(margens))
            estado.semAcesso -> EstadoErro(stringResource(R.string.membros_sem_acesso), null, Modifier.padding(margens))
            else -> LazyColumn(Modifier.fillMaxSize().padding(margens)) {
                item(key = "nome") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = nome,
                            onValueChange = {
                                nome = it.take(MembrosViewModel.LIMITE_NOME)
                                viewModel.alterarNome(it)
                            },
                            label = { Text(stringResource(R.string.criar_grupo_nome)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = viewModel::renomear, enabled = estado.podeRenomear) {
                            Text(stringResource(if (estado.renomeando) R.string.membros_salvando else R.string.membros_renomear))
                        }
                    }
                }
                item(key = "adicionar") {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (estado.adicionando) R.string.membros_adicionando else R.string.membros_adicionar))
                        },
                        leadingContent = { Icon(Icons.Outlined.PersonAdd, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.primary,
                            leadingIconColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.clickable(enabled = !estado.adicionando) { escolhendo = true },
                    )
                    HorizontalDivider()
                }
                items(estado.membros, key = { it.vinculoId }) { membro ->
                    val eu = membro.usuarioId == estado.euId
                    ListItem(
                        headlineContent = {
                            Text(
                                if (eu) "${membro.nome} (${stringResource(R.string.membros_voce)})" else membro.nome,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = { Avatar(membro.nome, membro.avatarUrl) },
                    )
                }
                item(key = "sair") {
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(stringResource(if (estado.saindo) R.string.membros_saindo else R.string.membros_sair)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.clickable(enabled = !estado.saindo) { confirmarSaida = true },
                    )
                    Text(
                        stringResource(R.string.membros_remover_aviso),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    if (escolhendo) {
        ModalBottomSheet(onDismissRequest = { escolhendo = false }) {
            Text(
                stringResource(R.string.membros_adicionar),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (estado.candidatos.isEmpty()) {
                Text(stringResource(R.string.membros_todos), modifier = Modifier.padding(24.dp))
            }
            LazyColumn {
                items(estado.candidatos, key = { it.id }) { contato ->
                    ListItem(
                        headlineContent = { Text(contato.nome) },
                        supportingContent = { Text(contato.login) },
                        leadingContent = { Avatar(contato.nome, contato.avatarUrl) },
                        modifier = Modifier.clickable {
                            escolhendo = false
                            viewModel.adicionar(contato.id)
                        },
                    )
                }
            }
        }
    }

    if (confirmarSaida) {
        DialogoConfirmacao(
            titulo = stringResource(R.string.membros_sair),
            mensagem = stringResource(R.string.membros_sair_confirmar),
            textoConfirmar = stringResource(R.string.membros_sair),
            perigo = true,
            aoConfirmar = {
                confirmarSaida = false
                viewModel.sair()
            },
            aoCancelar = { confirmarSaida = false },
        )
    }
}
