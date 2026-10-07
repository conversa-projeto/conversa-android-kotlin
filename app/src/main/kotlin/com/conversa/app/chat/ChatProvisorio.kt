package com.conversa.app.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.R
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.IndicadorDigitando
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CabecalhoChat(val conversa: Conversa? = null, val online: Boolean = false, val digitando: Boolean = false)

@HiltViewModel
class ChatProvisorioViewModel @Inject constructor(
    salvo: SavedStateHandle,
    conversas: ConversasRepositorio,
    presenca: PresencaRepositorio,
) : ViewModel() {
    private val conversaId: Long = checkNotNull(salvo["conversaId"])

    val cabecalho = combine(conversas.observar(conversaId), presenca.online, presenca.digitando) { conversa, online, digitando ->
        CabecalhoChat(
            conversa = conversa,
            online = conversa?.tipo == TipoConversa.DIRETA && conversa.destinatarioId in online,
            digitando = digitando[conversaId].orEmpty().isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CabecalhoChat())
}

/**
 * Conversa aberta — provisória até o `:feature:chat` (etapa 3): só o cabeçalho
 * (avatar, online, digitando, membros do grupo). As mensagens vêm na etapa 3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatProvisorio(aoMembros: (Long) -> Unit, aoVoltar: () -> Unit, viewModel: ChatProvisorioViewModel = hiltViewModel()) {
    val cabecalho by viewModel.cabecalho.collectAsStateWithLifecycle()
    val conversa = cabecalho.conversa
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = aoVoltar) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(conversa?.titulo, conversa?.avatarUrl, tamanho = 36.dp, online = cabecalho.online)
                        Column {
                            Text(conversa?.titulo.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            when {
                                cabecalho.digitando -> IndicadorDigitando()
                                cabecalho.online -> Text(
                                    stringResource(R.string.chat_online),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (conversa?.tipo == TipoConversa.GRUPO) {
                        IconButton(onClick = { aoMembros(conversa.id) }) {
                            Icon(Icons.Outlined.Group, contentDescription = stringResource(R.string.chat_membros))
                        }
                    }
                },
            )
        },
    ) { margens ->
        EstadoVazio(
            titulo = stringResource(R.string.chat_em_breve),
            descricao = stringResource(R.string.chat_em_breve_descricao),
            icone = Icons.AutoMirrored.Outlined.Chat,
            modifier = Modifier.padding(margens),
        )
    }
}
