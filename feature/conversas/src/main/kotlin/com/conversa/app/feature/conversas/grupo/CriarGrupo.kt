package com.conversa.app.feature.conversas.grupo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.avatarDoContato
import com.conversa.app.core.model.contatoCombina
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.core.ui.estado.resolver
import com.conversa.app.feature.conversas.R
import com.conversa.app.feature.conversas.lista.ItemContato
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class CriarGrupoUiState(
    val nome: String = "",
    val termo: String = "",
    val contatos: List<ItemContato> = emptyList(),
    val selecionados: Set<Long> = emptySet(),
    val criando: Boolean = false,
    val erro: TextoUi? = null,
)

private data class Formulario(
    val nome: String = "",
    val termo: String = "",
    val selecionados: Set<Long> = emptySet(),
    val criando: Boolean = false,
    val erro: TextoUi? = null,
)

/**
 * "Criar grupo" (CON-07): nome, filtro e marcação de contatos. Cria com
 * `PUT /conversa {tipo:2}` e inclui cada marcado **e o criador**; depois abre o grupo.
 */
@HiltViewModel
class CriarGrupoViewModel @Inject constructor(private val conversas: ConversasRepositorio, contatos: ContatosRepositorio) : ViewModel() {
    private val formulario = MutableStateFlow(Formulario())
    val aoCriar = EventosUnicos<Long>()

    val estado: StateFlow<CriarGrupoUiState> = combine(
        contatos.observarOutros(),
        conversas.observarTodas(),
        formulario,
    ) { pessoas, lista, f ->
        CriarGrupoUiState(
            nome = f.nome,
            termo = f.termo,
            // Marcados continuam visíveis mesmo fora do filtro, para poder desmarcar.
            contatos = pessoas.filter { it.id in f.selecionados || contatoCombina(it, f.termo) }
                .map { ItemContato(it.id, it.nome, it.login, avatarDoContato(it, lista), online = false) },
            selecionados = f.selecionados,
            criando = f.criando,
            erro = f.erro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CriarGrupoUiState())

    fun alterarNome(texto: String) = formulario.update { it.copy(nome = texto.take(LIMITE_NOME), erro = null) }

    fun alterarTermo(texto: String) = formulario.update { it.copy(termo = texto) }

    fun alternar(id: Long) = formulario.update {
        it.copy(selecionados = if (id in it.selecionados) it.selecionados - id else it.selecionados + id, erro = null)
    }

    fun criar() {
        val atual = formulario.value
        if (atual.criando) return
        val erro = when {
            atual.nome.isBlank() -> R.string.criar_grupo_sem_nome
            atual.selecionados.isEmpty() -> R.string.criar_grupo_sem_membros
            else -> null
        }
        if (erro != null) {
            formulario.update { it.copy(erro = TextoUi.Recurso(erro)) }
            return
        }
        viewModelScope.launch {
            formulario.update { it.copy(criando = true, erro = null) }
            conversas.criarGrupo(atual.nome, atual.selecionados)
                .onSuccess {
                    formulario.update { f -> f.copy(criando = false) }
                    aoCriar.enviar(it)
                }
                .onFailure { falha ->
                    formulario.update { it.copy(criando = false, erro = TextoUi.Literal(falha.paraErroApi().mensagemAmigavel())) }
                }
        }
    }

    companion object {
        /** `conversa.descricao` é varchar(100). */
        const val LIMITE_NOME = 100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CriarGrupoRotaTela(
    aoCriado: (Long) -> Unit,
    aoVoltar: () -> Unit,
    viewModel: CriarGrupoViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    // Texto do campo em estado local (síncrono): passar pelo combine/stateIn do ViewModel
    // atrasa um quadro e o cursor pula enquanto se digita.
    var nome by rememberSaveable { mutableStateOf("") }
    var termo by rememberSaveable { mutableStateOf("") }
    ColetarEventos(viewModel.aoCriar.fluxo) { aoCriado(it) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.criar_grupo_titulo)) },
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::criar, enabled = !estado.criando) {
                        Text(stringResource(if (estado.criando) R.string.criar_grupo_criando else R.string.criar_grupo_criar))
                    }
                },
            )
        },
    ) { margens ->
        Column(Modifier.fillMaxSize().padding(margens).imePadding()) {
            OutlinedTextField(
                value = nome,
                onValueChange = {
                    nome = it.take(CriarGrupoViewModel.LIMITE_NOME)
                    viewModel.alterarNome(it)
                },
                label = { Text(stringResource(R.string.criar_grupo_nome)) },
                placeholder = { Text(stringResource(R.string.criar_grupo_exemplo)) },
                singleLine = true,
                enabled = !estado.criando,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            estado.erro?.let {
                Text(it.resolver(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            Text(
                stringResource(R.string.criar_grupo_selecionar) + " · " +
                    pluralStringResource(R.plurals.selecionados, estado.selecionados.size, estado.selecionados.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = termo,
                onValueChange = {
                    termo = it
                    viewModel.alterarTermo(it)
                },
                placeholder = { Text(stringResource(R.string.contatos_filtrar)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(estado.contatos, key = { it.id }) { contato ->
                    val marcado = contato.id in estado.selecionados
                    ListItem(
                        headlineContent = { Text(contato.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(contato.detalhe, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Avatar(contato.nome, contato.avatarUrl) },
                        trailingContent = { Checkbox(checked = marcado, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = marcado,
                            enabled = !estado.criando,
                            role = Role.Checkbox,
                            onValueChange = { viewModel.alternar(contato.id) },
                        ),
                    )
                }
            }
        }
    }
}
