package com.conversa.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.sistema.SistemaRepositorio
import com.conversa.app.core.model.Acessos
import com.conversa.app.core.model.CodigoPermissao
import com.conversa.app.core.model.UsuarioAcessos
import com.conversa.app.core.model.comPermissao
import com.conversa.app.core.model.filtrarUsuarios
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AcessosUiState(
    val carregando: Boolean = true,
    val acessos: Acessos? = null,
    /** Eu, para o "(você)". */
    val eu: Long = 0,
    /** A caixa esperando o servidor: "usuário:código". */
    val alterando: String? = null,
    val aviso: Aviso? = null,
)

/**
 * Acessos (8.5, FC-808, CFG-08): quem pode mexer no sistema e nas permissões. A caixa só
 * muda quando o servidor aceita; se ele recusa, o motivo aparece e a caixa fica como estava.
 */
@HiltViewModel
class AcessosViewModel @Inject constructor(private val sistema: SistemaRepositorio, sessoes: SessaoRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(AcessosUiState(eu = sessoes.sessao.value?.usuarioId ?: 0))
    val estado: StateFlow<AcessosUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            sistema.acessos().fold(
                onSuccess = { a -> _estado.update { it.copy(carregando = false, acessos = a) } },
                onFailure = { falha ->
                    _estado.update { it.copy(carregando = false, aviso = avisoDeErro(falha, R.string.acessos_erro_carregar)) }
                },
            )
        }
    }

    fun alternar(usuarioId: Long, codigo: String, conceder: Boolean) {
        if (_estado.value.alterando != null) return
        _estado.update { it.copy(alterando = "$usuarioId:$codigo", aviso = null) }
        viewModelScope.launch {
            val resultado = if (conceder) sistema.conceder(usuarioId, codigo) else sistema.retirar(usuarioId, codigo)
            resultado.fold(
                onSuccess = {
                    _estado.update { it.copy(alterando = null, acessos = it.acessos?.comPermissao(usuarioId, codigo, conceder)) }
                },
                onFailure = { falha ->
                    _estado.update { it.copy(alterando = null, aviso = avisoDeErro(falha, R.string.acessos_erro_alterar)) }
                },
            )
        }
    }
}

/** "Sistema" e "Acessos", como o web chama os códigos. */
@Composable
private fun nomeDaPermissao(codigo: String): String = when (codigo) {
    CodigoPermissao.PARAMETROS -> stringResource(R.string.config_sistema)
    CodigoPermissao.PERMISSOES -> stringResource(R.string.config_acessos)
    else -> codigo
}

@Composable
fun AcessosRotaTela(aoVoltar: () -> Unit, viewModel: AcessosViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    SubTela(titulo = stringResource(R.string.config_acessos), aoVoltar = aoVoltar) { modificador ->
        val acessos = estado.acessos
        when {
            estado.carregando -> Carregando(modificador)
            acessos == null -> Column(modificador.padding(16.dp)) { TextoAviso(estado.aviso) }
            else -> ListaAcessos(acessos, estado, viewModel::alternar, modificador)
        }
    }
}

@Composable
private fun ListaAcessos(acessos: Acessos, estado: AcessosUiState, aoAlternar: (Long, String, Boolean) -> Unit, modifier: Modifier) {
    var busca by rememberSaveable { mutableStateOf("") }
    val usuarios = filtrarUsuarios(acessos.usuarios, busca)
    LazyColumn(modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (acessos.modoAberto) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.acessos_modo_aberto_titulo), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.acessos_modo_aberto), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.acessos_o_que_libera),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    acessos.permissoes.forEach { p ->
                        Text("${nomeDaPermissao(p.codigo)}: ${p.descricao}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = { Text(stringResource(R.string.acessos_buscar)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextoAviso(estado.aviso)
            }
            HorizontalDivider()
        }
        items(usuarios, key = { it.id }) { usuario ->
            LinhaUsuario(usuario, acessos, estado, aoAlternar)
            HorizontalDivider()
        }
        if (usuarios.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.acessos_nenhum),
                    color = ConversaTema.cores.textoTerciario,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** No celular a tabela vira uma linha por usuário, com uma caixa por permissão. */
@Composable
private fun LinhaUsuario(usuario: UsuarioAcessos, acessos: Acessos, estado: AcessosUiState, aoAlternar: (Long, String, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val voce = if (usuario.id == estado.eu) " ${stringResource(R.string.acessos_voce)}" else ""
        Text(usuario.nome + voce, style = MaterialTheme.typography.bodyLarge)
        Text(usuario.login, style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            acessos.permissoes.forEach { p ->
                val marcada = p.codigo in usuario.permissoes
                val nome = nomeDaPermissao(p.codigo)
                val descricao = stringResource(R.string.acessos_caixa, nome, usuario.nome)
                Row(
                    Modifier
                        .toggleable(
                            value = marcada,
                            enabled = estado.alterando == null,
                            role = Role.Checkbox,
                        ) { aoAlternar(usuario.id, p.codigo, it) }
                        .semantics { contentDescription = descricao }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = marcada, onCheckedChange = null, enabled = estado.alterando != "${usuario.id}:${p.codigo}")
                    Text(nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
