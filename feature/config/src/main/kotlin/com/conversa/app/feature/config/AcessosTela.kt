package com.conversa.app.feature.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.sistema.SistemaRepositorio
import com.conversa.app.core.model.Acessos
import com.conversa.app.core.model.CodigoPermissao
import com.conversa.app.core.model.GrupoPermissao
import com.conversa.app.core.model.PermissaoSistema
import com.conversa.app.core.model.UsuarioAcessos
import com.conversa.app.core.model.comPermissao
import com.conversa.app.core.model.filtrarUsuarios
import com.conversa.app.core.model.gruposDePermissoes
import com.conversa.app.core.model.propriaMarcada
import com.conversa.app.core.model.quantasDe
import com.conversa.app.core.model.temAdministracao
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

/** O nome curto de cada permissão, como o web (`NOMES_PERMISSAO`); desconhecida, a descrição do servidor. */
@Composable
private fun nomeDaPermissao(permissao: PermissaoSistema): String {
    val recurso = when (permissao.codigo) {
        CodigoPermissao.PARAMETROS -> R.string.config_sistema
        CodigoPermissao.PERMISSOES -> R.string.config_acessos
        CodigoPermissao.BOTS -> R.string.acessos_permissao_bots
        CodigoPermissao.USUARIOS -> R.string.acessos_permissao_usuarios
        CodigoPermissao.FERRAMENTAS -> R.string.acessos_permissao_ferramentas
        CodigoPermissao.CONHECIMENTO -> R.string.acessos_permissao_conhecimento
        CodigoPermissao.EMOJI -> R.string.acessos_permissao_emoji
        CodigoPermissao.FIGURINHA -> R.string.acessos_permissao_figurinha
        CodigoPermissao.IMAGEM -> R.string.acessos_permissao_imagem
        CodigoPermissao.VIDEO -> R.string.acessos_permissao_video
        CodigoPermissao.AUDIO -> R.string.acessos_permissao_audio
        CodigoPermissao.ARQUIVO -> R.string.acessos_permissao_arquivo
        CodigoPermissao.RESPONDER -> R.string.acessos_permissao_responder
        CodigoPermissao.ENCAMINHAR -> R.string.acessos_permissao_encaminhar
        CodigoPermissao.REAGIR -> R.string.acessos_permissao_reagir
        CodigoPermissao.AGENDAR -> R.string.acessos_permissao_agendar
        CodigoPermissao.CONFIRMACAO -> R.string.acessos_permissao_confirmacao
        CodigoPermissao.GRUPO -> R.string.acessos_permissao_grupo
        CodigoPermissao.POSTAGEM -> R.string.acessos_permissao_postagem
        CodigoPermissao.VOTACAO -> R.string.acessos_permissao_votacao
        CodigoPermissao.CHAMADA_AUDIO -> R.string.acessos_permissao_chamada_audio
        CodigoPermissao.CHAMADA_VIDEO -> R.string.acessos_permissao_chamada_video
        CodigoPermissao.COMPARTILHAR_TELA -> R.string.acessos_permissao_compartilhar_tela
        CodigoPermissao.TELEFONIA -> R.string.acessos_permissao_telefonia
        CodigoPermissao.OCULTAR -> R.string.acessos_permissao_ocultar
        CodigoPermissao.NOME -> R.string.acessos_permissao_nome
        CodigoPermissao.EMAIL -> R.string.acessos_permissao_email
        CodigoPermissao.AVATAR -> R.string.acessos_permissao_avatar
        CodigoPermissao.SENHA -> R.string.acessos_permissao_senha
        CodigoPermissao.PRIVACIDADE -> R.string.acessos_permissao_privacidade
        CodigoPermissao.CORES -> R.string.acessos_permissao_cores
        CodigoPermissao.CONFIG_CHAMADAS -> R.string.acessos_permissao_config_chamadas
        else -> null
    }
    return recurso?.let { stringResource(it) } ?: permissao.descricao.ifBlank { permissao.codigo }
}

@Composable
private fun tituloDoGrupo(grupo: GrupoPermissao): String = stringResource(
    when (grupo) {
        GrupoPermissao.ADMINISTRACAO -> R.string.acessos_administracao
        GrupoPermissao.ENVIAR -> R.string.acessos_grupo_enviar
        GrupoPermissao.MENSAGENS -> R.string.acessos_grupo_mensagens
        GrupoPermissao.GRUPOS -> R.string.acessos_grupo_grupos
        GrupoPermissao.CHAMADAS -> R.string.acessos_grupo_chamadas
        GrupoPermissao.CONFIGURACOES -> R.string.acessos_grupo_configuracoes
        GrupoPermissao.OUTRAS -> R.string.acessos_grupo_outras
    },
)

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

/**
 * Como o web desde o `9c6e8a6` (FC-813): com as 32 permissões (administração + uso), cada pessoa
 * é uma linha que se abre ("Administração", "N de M"), uma de cada vez, com as caixas nos grupos.
 */
@Composable
private fun ListaAcessos(acessos: Acessos, estado: AcessosUiState, aoAlternar: (Long, String, Boolean) -> Unit, modifier: Modifier) {
    var busca by rememberSaveable { mutableStateOf("") }
    var aberto by rememberSaveable { mutableStateOf<Long?>(null) }
    val usuarios = filtrarUsuarios(acessos.usuarios, busca)
    val grupos = remember(acessos.permissoes) { gruposDePermissoes(acessos.permissoes) }
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
                Text(
                    stringResource(R.string.acessos_explicacao),
                    style = MaterialTheme.typography.bodySmall,
                    color = ConversaTema.cores.textoTerciario,
                )
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
            LinhaUsuario(
                usuario = usuario,
                acessos = acessos,
                grupos = grupos,
                estado = estado,
                aberto = aberto == usuario.id,
                aoAbrir = { aberto = if (aberto == usuario.id) null else usuario.id },
                aoAlternar = aoAlternar,
            )
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

@Composable
private fun LinhaUsuario(
    usuario: UsuarioAcessos,
    acessos: Acessos,
    grupos: List<Pair<GrupoPermissao, List<PermissaoSistema>>>,
    estado: AcessosUiState,
    aberto: Boolean,
    aoAbrir: () -> Unit,
    aoAlternar: (Long, String, Boolean) -> Unit,
) {
    val primaria = MaterialTheme.colorScheme.primary
    val estadoDaLinha = stringResource(if (aberto) R.string.acessos_aberto else R.string.acessos_fechado)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = aoAbrir)
                .semantics { stateDescription = estadoDaLinha }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                val voce = if (usuario.id == estado.eu) " ${stringResource(R.string.acessos_voce)}" else ""
                Text(usuario.nome + voce, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    usuario.login,
                    style = MaterialTheme.typography.bodySmall,
                    color = ConversaTema.cores.textoTerciario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (acessos.temAdministracao(usuario)) {
                Text(
                    stringResource(R.string.acessos_administracao),
                    style = MaterialTheme.typography.labelSmall,
                    color = primaria,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(primaria.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                stringResource(R.string.acessos_quantas, acessos.quantasDe(usuario), acessos.permissoes.size),
                style = MaterialTheme.typography.labelSmall,
                color = ConversaTema.cores.textoTerciario,
            )
            Icon(
                if (aberto) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = ConversaTema.cores.textoTerciario,
            )
        }
        if (aberto) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                grupos.forEach { (grupo, permissoes) ->
                    Text(
                        tituloDoGrupo(grupo).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ConversaTema.cores.textoTerciario,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp).semantics { heading() },
                    )
                    permissoes.forEach { permissao -> CaixaPermissao(usuario, permissao, estado, aoAlternar) }
                }
            }
        }
    }
}

/** Uma caixa. As próprias, marcadas, ficam travadas: o servidor não deixa retirar. */
@Composable
private fun CaixaPermissao(
    usuario: UsuarioAcessos,
    permissao: PermissaoSistema,
    estado: AcessosUiState,
    aoAlternar: (Long, String, Boolean) -> Unit,
) {
    val marcada = permissao.codigo in usuario.permissoes
    val travada = usuario.propriaMarcada(estado.eu, permissao.codigo)
    val nome = nomeDaPermissao(permissao)
    val descricao = stringResource(R.string.acessos_caixa, nome, usuario.nome)
    val motivo = stringResource(R.string.acessos_propria_travada)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = marcada,
                enabled = estado.alterando == null && !travada,
                role = Role.Checkbox,
            ) { aoAlternar(usuario.id, permissao.codigo, it) }
            .semantics { contentDescription = if (travada) "$descricao. $motivo" else descricao },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcada, onCheckedChange = null, enabled = !travada && estado.alterando != "${usuario.id}:${permissao.codigo}")
        Text(nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
    }
}
