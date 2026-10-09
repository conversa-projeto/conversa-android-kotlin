package com.conversa.app.feature.config

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.sistema.SistemaRepositorio
import com.conversa.app.core.model.AlteracaoParametros
import com.conversa.app.core.model.ParametrosSistema
import com.conversa.app.core.model.alteracaoDosParametros
import com.conversa.app.core.model.diasDeGravacao
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.Interruptor
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SistemaUiState(
    val carregando: Boolean = true,
    /** O que está no servidor; nulo = não carregou. */
    val parametros: ParametrosSistema? = null,
    val salvando: Boolean = false,
    val aviso: Aviso? = null,
    /** Muda a cada gravação: a tela recomeça os campos do que o servidor devolveu. */
    val versao: Int = 0,
)

/** Sistema (8.5, FC-808, CFG-07): os parâmetros do servidor, para quem tem a permissão "Sistema". */
@HiltViewModel
class SistemaViewModel @Inject constructor(private val sistema: SistemaRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(SistemaUiState())
    val estado: StateFlow<SistemaUiState> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            sistema.parametros().fold(
                onSuccess = { p -> _estado.update { it.copy(carregando = false, parametros = p) } },
                onFailure = { falha ->
                    _estado.update { it.copy(carregando = false, aviso = avisoDeErro(falha, R.string.sistema_erro_carregar)) }
                },
            )
        }
    }

    /** Só o que mudou (a tela calcula com `alteracaoDosParametros`). */
    fun salvar(alteracao: AlteracaoParametros) {
        if (_estado.value.salvando) return
        _estado.update { it.copy(salvando = true, aviso = null) }
        viewModelScope.launch {
            sistema.alterarParametros(alteracao).fold(
                onSuccess = { p ->
                    _estado.update {
                        it.copy(
                            salvando = false,
                            parametros = p,
                            aviso = Aviso(ok = true, recurso = R.string.sistema_salvo),
                            versao =
                            it.versao + 1,
                        )
                    }
                },
                onFailure = { falha ->
                    _estado.update { it.copy(salvando = false, aviso = avisoDeErro(falha, R.string.sistema_erro_salvar)) }
                },
            )
        }
    }
}

@Composable
fun SistemaRotaTela(aoVoltar: () -> Unit, viewModel: SistemaViewModel = hiltViewModel()) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    SubTela(titulo = stringResource(R.string.config_sistema), aoVoltar = aoVoltar) { modificador ->
        val parametros = estado.parametros
        when {
            estado.carregando -> Carregando(modificador)
            parametros == null -> Column(modificador.padding(16.dp)) { TextoAviso(estado.aviso) }
            else -> FormularioSistema(parametros, estado, viewModel::salvar, modificador)
        }
    }
}

/**
 * Os campos ficam em estado local (o cursor não pula) e recomeçam a cada gravação. A chave
 * privada só fica no campo: nunca vai para o estado salvo nem volta do servidor.
 */
@Composable
private fun FormularioSistema(
    original: ParametrosSistema,
    estado: SistemaUiState,
    aoSalvar: (AlteracaoParametros) -> Unit,
    modifier: Modifier,
) {
    var editado by remember(original, estado.versao) { mutableStateOf(original) }
    var dias by remember(original, estado.versao) { mutableStateOf(original.gravacaoDias.toString()) }
    var novaChave by remember(original, estado.versao) { mutableStateOf("") }
    val diasValidos = diasDeGravacao(dias)
    val alteracao = diasValidos?.let { alteracaoDosParametros(original, editado.copy(gravacaoDias = it), novaChave) }
    Column(
        modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.sistema_aviso), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        Secao(R.string.sistema_firebase, R.string.sistema_firebase_texto)
        Campo(R.string.sistema_fcm_projeto, editado.fcmProjetoId) { editado = editado.copy(fcmProjetoId = it) }
        Campo(R.string.sistema_fcm_email, editado.fcmEmail, KeyboardType.Email) { editado = editado.copy(fcmEmail = it) }
        OutlinedTextField(
            value = novaChave,
            onValueChange = { novaChave = it },
            label = {
                val situacao = if (original.fcmChaveConfigurada) {
                    R.string.sistema_fcm_chave_configurada
                } else {
                    R.string.sistema_fcm_chave_nao_configurada
                }
                Text("${stringResource(R.string.sistema_fcm_chave)} ${stringResource(situacao)}")
            },
            placeholder = { if (original.fcmChaveConfigurada) Text(stringResource(R.string.sistema_fcm_chave_manter)) },
            supportingText = { Text(stringResource(R.string.sistema_fcm_chave_texto)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        Secao(R.string.sistema_chamadas)
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = editado.turnForcarRelay, role = Role.Switch) { editado = editado.copy(turnForcarRelay = it) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sistema_turn), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.sistema_turn_texto),
                    style = MaterialTheme.typography.bodySmall,
                    color = ConversaTema.cores.textoTerciario,
                )
            }
            Interruptor(editado.turnForcarRelay)
        }
        OutlinedTextField(
            value = dias,
            onValueChange = { dias = it },
            label = { Text(stringResource(R.string.sistema_gravacao)) },
            supportingText = {
                Text(stringResource(if (diasValidos == null) R.string.sistema_gravacao_invalida else R.string.sistema_gravacao_texto))
            },
            isError = diasValidos == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        Secao(R.string.sistema_transcricao)
        Campo(
            R.string.sistema_transcritor,
            editado.transcritorUrl,
            KeyboardType.Uri,
            R.string.sistema_transcritor_texto,
            R.string.sistema_transcritor_exemplo,
        ) {
            editado = editado.copy(transcritorUrl = it)
        }
        Campo(R.string.sistema_idioma, editado.transcritorIdioma, exemplo = R.string.sistema_idioma_exemplo) {
            editado =
                editado.copy(transcritorIdioma = it)
        }
        HorizontalDivider()
        Secao(R.string.sistema_armazenamento)
        Text(
            stringResource(R.string.sistema_bucket, original.s3Bucket.ifBlank { stringResource(R.string.sistema_bucket_vazio) }),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.sistema_bucket_texto),
            style = MaterialTheme.typography.bodySmall,
            color = ConversaTema.cores.textoTerciario,
        )
        TextoAviso(estado.aviso)
        Button(
            enabled = alteracao != null && !estado.salvando,
            onClick = { alteracao?.let(aoSalvar) },
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(if (estado.salvando) R.string.sistema_salvando else R.string.sistema_salvar)) }
    }
}

@Composable
private fun Secao(@StringRes titulo: Int, @StringRes texto: Int? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(titulo), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        texto?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario) }
    }
}

@Composable
private fun Campo(
    @StringRes rotulo: Int,
    valor: String,
    tipo: KeyboardType = KeyboardType.Text,
    @StringRes ajuda: Int? = null,
    @StringRes exemplo: Int? = null,
    aoMudar: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = aoMudar,
        label = { Text(stringResource(rotulo)) },
        placeholder = exemplo?.let { { Text(stringResource(it)) } },
        supportingText = ajuda?.let { { Text(stringResource(it)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = tipo, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
