package com.conversa.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.formatarTamanho
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** O espaço dos arquivos que a pessoa enviou (servidor `ac710eb`, web `EspacoUsado.vue`). */
sealed interface EspacoUsado {
    data object Calculando : EspacoUsado

    data class Pronto(val bytes: Long) : EspacoUsado

    /** [motivo] do servidor; nulo = o texto padrão. */
    data class Erro(val motivo: String?) : EspacoUsado
}

/** "Armazenamento" e "Usuário desde" no Perfil, como a aba Conta › Perfil do web. */
@HiltViewModel
class ArmazenamentoViewModel @Inject constructor(private val perfil: PerfilRepositorio, contatos: ContatosRepositorio) : ViewModel() {
    private val _espaco = MutableStateFlow<EspacoUsado>(EspacoUsado.Calculando)
    val espaco: StateFlow<EspacoUsado> = _espaco.asStateFlow()

    /** `criado_em` do próprio usuário nos contatos; nulo enquanto o servidor não manda. */
    val desde: StateFlow<Instant?> = contatos.observarEu().map { it?.criadoEm }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            perfil.espacoUsado().fold(
                onSuccess = { _espaco.value = EspacoUsado.Pronto(it) },
                onFailure = { _espaco.value = EspacoUsado.Erro(it.paraErroApi().mensagemAmigavel()) },
            )
        }
    }
}

/** A seção "Armazenamento": "<tamanho> usados pelos arquivos que você enviou (…)." */
@Composable
internal fun BlocoArmazenamento(espaco: EspacoUsado) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.perfil_armazenamento), style = MaterialTheme.typography.titleSmall)
        val estilo = MaterialTheme.typography.bodySmall
        val cor = ConversaTema.cores.textoTerciario
        when (espaco) {
            EspacoUsado.Calculando -> Text(stringResource(R.string.perfil_espaco_calculando), style = estilo, color = cor)
            is EspacoUsado.Erro -> Text(espaco.motivo ?: stringResource(R.string.perfil_espaco_erro), style = estilo, color = cor)
            is EspacoUsado.Pronto -> {
                val resto = stringResource(R.string.perfil_espaco_usado)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)) {
                            append(formatarTamanho(espaco.bytes))
                        }
                        append(resto)
                    },
                    style = estilo,
                    color = cor,
                )
            }
        }
    }
}
