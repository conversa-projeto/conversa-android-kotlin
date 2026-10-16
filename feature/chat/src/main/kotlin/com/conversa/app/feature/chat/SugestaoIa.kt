package com.conversa.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ia.SugestoesRepositorio
import com.conversa.app.core.model.ESPERA_SUGESTAO_MS
import com.conversa.app.core.model.mencaoDigitada
import com.conversa.app.core.model.podePedirSugestao
import com.conversa.app.core.model.textoParaSugestao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn

/** A sugestão de texto da IA na conversa aberta (ENV-25): ligada só com a IA do servidor e a preferência. */
@HiltViewModel
class SugestaoViewModel @Inject constructor(salvo: SavedStateHandle, private val sugestoes: SugestoesRepositorio) : ViewModel() {
    private val conversaId: Long = checkNotNull(salvo["conversaId"])

    val ligada: StateFlow<Boolean> = sugestoes.ligadas.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun sugerir(texto: String): String = sugestoes.sugerir(conversaId, texto)
}

/** O que a IA sugeriu para [texto] (o campo quando pediu): só vale enquanto o campo não mudar. */
private data class Sugestao(val texto: String, val continuacao: String)

/**
 * Sugestão de texto da IA (ENV-25, como o `useSugestaoIa` do web). Depois de 600 ms parado, com o
 * campo em foco, o cursor no fim, sem seleção e sem a lista de menção, pede a continuação; voltar a
 * digitar cancela o pedido. No celular não há Tab: a sugestão aparece num chip acima do campo,
 * tocar insere e o "×" descarta. Qualquer mudança no campo (ou no cursor) apaga a sugestão.
 */
@Composable
internal fun SugestaoDaIa(texto: TextFieldState, focado: State<Boolean>, pedir: suspend (String) -> String) {
    var sugestao by remember { mutableStateOf<Sugestao?>(null) }
    LaunchedEffect(texto, pedir) {
        snapshotFlow { Triple(texto.text.toString(), texto.selection, focado.value) }.collectLatest { (atual, selecao, emFoco) ->
            sugestao = null
            if (!emFoco || !podePedirSugestao(atual, selecao.start, selecao.end)) return@collectLatest
            if (mencaoDigitada(atual, selecao.end) != null) return@collectLatest
            delay(ESPERA_SUGESTAO_MS)
            val continuacao = pedir(textoParaSugestao(atual, selecao.end))
            if (continuacao.isNotBlank()) sugestao = Sugestao(atual, continuacao)
        }
    }
    val atual = sugestao?.takeIf { it.texto == texto.text.toString() } ?: return
    val descricao = stringResource(R.string.sugestao_ia_descricao, atual.continuacao.trim())
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            onClick = {
                texto.edit {
                    val fim = selection.end
                    replace(fim, fim, atual.continuacao)
                    selection = TextRange(fim + atual.continuacao.length)
                }
                sugestao = null
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.weight(1f, fill = false).semantics { contentDescription = descricao },
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    atual.continuacao.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { sugestao = null }) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.descartar_sugestao))
        }
    }
}
