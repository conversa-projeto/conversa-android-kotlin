package com.conversa.app.feature.conversas.enviarpara

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.anexos.Compartilhamentos
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.avatarDoContato
import com.conversa.app.core.model.contatoCombina
import com.conversa.app.core.model.conversaCombina
import com.conversa.app.core.model.diretaCom
import com.conversa.app.core.model.ordenarConversas
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.feature.conversas.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Um destino possível: conversa existente ou contato ainda sem conversa direta. */
@Immutable
data class Destino(
    val conversaId: Long?,
    val contatoId: Long?,
    val titulo: String,
    val detalhe: String,
    val avatarUrl: String?,
    val online: Boolean,
)

@Immutable
data class EnviarParaUiState(
    val destinos: List<Destino> = emptyList(),
    /** Resumo do que vai ser enviado. */
    val texto: String = "",
    val arquivos: Int = 0,
    val ignorados: Int = 0,
    val abrindo: Boolean = false,
)

sealed interface EventoEnviarPara {
    data class Abrir(val conversaId: Long) : EventoEnviarPara

    data class Erro(val mensagem: String) : EventoEnviarPara
}

/**
 * "Enviar para…" (TODO 4.9, AND-10): o que outro app compartilhou vai para a conversa
 * escolhida. Conversas na ordem da lista (fixadas primeiro) e, depois, os contatos sem
 * conversa direta (cria a conversa ao escolher).
 */
@HiltViewModel
class EnviarParaViewModel @Inject constructor(
    private val conversas: ConversasRepositorio,
    contatos: ContatosRepositorio,
    presenca: PresencaRepositorio,
    private val compartilhamentos: Compartilhamentos,
) : ViewModel() {
    private val termo = MutableStateFlow("")
    private val abrindo = MutableStateFlow(false)
    val eventos = EventosUnicos<EventoEnviarPara>()

    val estado: StateFlow<EnviarParaUiState> = combine(
        conversas.observarTodas(),
        contatos.observarOutros(),
        presenca.online,
        termo,
        combine(abrindo, compartilhamentos.pendente) { a, p -> a to p },
    ) { lista, pessoas, online, t, (abrindoAgora, pendente) ->
        val dasConversas = ordenarConversas(lista).filter { conversaCombina(it, t) }.map {
            Destino(it.id, null, it.titulo, "", it.avatarUrl, it.tipo == TipoConversa.DIRETA && it.destinatarioId in online)
        }
        val semConversa = pessoas.filter { lista.diretaCom(it.id) == null && contatoCombina(it, t) }.map {
            Destino(null, it.id, it.nome, it.login, avatarDoContato(it, lista), it.id in online)
        }
        EnviarParaUiState(
            destinos = dasConversas + semConversa,
            texto = pendente?.texto.orEmpty(),
            arquivos = pendente?.anexos?.size ?: 0,
            ignorados = pendente?.ignorados ?: 0,
            abrindo = abrindoAgora,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EnviarParaUiState())

    fun alterarTermo(texto: String) {
        termo.value = texto
    }

    fun escolher(destino: Destino) {
        if (abrindo.value) return
        destino.conversaId?.let {
            viewModelScope.launch { eventos.enviar(EventoEnviarPara.Abrir(it)) }
            return
        }
        val contato = destino.contatoId ?: return
        viewModelScope.launch {
            abrindo.value = true
            conversas.obterOuCriarDireta(contato)
                .onSuccess { eventos.enviar(EventoEnviarPara.Abrir(it)) }
                .onFailure { eventos.enviar(EventoEnviarPara.Erro(it.paraErroApi().mensagemAmigavel())) }
            abrindo.value = false
        }
    }

    /** Voltou sem escolher: o compartilhamento é descartado. */
    fun cancelar() = compartilhamentos.descartar()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnviarParaRotaTela(aoAbrirConversa: (Long) -> Unit, aoVoltar: () -> Unit, viewModel: EnviarParaViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Texto do campo em estado local (o cursor não pula).
    var termo by rememberSaveable { mutableStateOf("") }
    val avisos = LocalAvisos.current
    val sair = {
        viewModel.cancelar()
        aoVoltar()
    }
    BackHandler(onBack = sair)
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            is EventoEnviarPara.Abrir -> aoAbrirConversa(evento.conversaId)
            is EventoEnviarPara.Erro -> avisos.mostrarErro(evento.mensagem)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.enviar_para))
                        Text(resumo(estado), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = sair) {
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
                placeholder = { Text(stringResource(R.string.pesquisar)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (estado.destinos.isEmpty()) {
                EstadoVazio(titulo = stringResource(R.string.nenhum_resultado), descricao = null)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(estado.destinos, key = { "${it.conversaId}:${it.contatoId}" }) { destino ->
                        ListItem(
                            headlineContent = { Text(destino.titulo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = destino.detalhe.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = { Avatar(destino.titulo, destino.avatarUrl, online = destino.online) },
                            modifier = Modifier.clickable(enabled = !estado.abrindo) { viewModel.escolher(destino) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun resumo(estado: EnviarParaUiState): String {
    val partes = buildList {
        if (estado.arquivos > 0) add(pluralStringResource(R.plurals.arquivos_compartilhados, estado.arquivos, estado.arquivos))
        if (estado.texto.isNotBlank()) add("“${estado.texto.take(40)}”")
        if (estado.ignorados > 0) add(pluralStringResource(R.plurals.arquivos_ignorados, estado.ignorados, estado.ignorados))
    }
    return partes.joinToString(" · ")
}
