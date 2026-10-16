package com.conversa.app.feature.conversas.grupo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.SeletorEmojis
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.conversas.R

/**
 * Imagem ou emoji do grupo (FC-217), como o web (`PainelGrupo.vue`, `CreateGroupModal.vue`):
 * o avatar com o que está valendo e "Imagem", "Emoji" e "Remover" (quando há um dos dois).
 * Um tira o outro. [salvando] desabilita os botões e mostra "Salvando…".
 */
@Composable
internal fun EscolhaAvatarGrupo(
    nome: String,
    url: String?,
    emoji: String?,
    salvando: Boolean,
    aoImagem: (Uri) -> Unit,
    aoEmoji: (String) -> Unit,
    aoRemover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var escolhendoEmoji by rememberSaveable { mutableStateOf(false) }
    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(aoImagem) }
    if (escolhendoEmoji) {
        SeletorEmojis(
            aoEscolher = {
                escolhendoEmoji = false
                aoEmoji(it)
            },
            aoFechar = { escolhendoEmoji = false },
        )
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(nome.ifBlank { "G" }, url, tamanho = 64.dp, emoji = emoji)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !salvando,
                ) { Text(stringResource(R.string.grupo_imagem)) }
                TextButton(onClick = { escolhendoEmoji = true }, enabled = !salvando) { Text(stringResource(R.string.grupo_emoji)) }
                if (!url.isNullOrBlank() || !emoji.isNullOrBlank()) {
                    TextButton(onClick = aoRemover, enabled = !salvando) {
                        Text(stringResource(R.string.grupo_remover_avatar), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (salvando) {
                Text(
                    stringResource(R.string.grupo_salvando_avatar),
                    style = MaterialTheme.typography.labelSmall,
                    color = ConversaTema.cores.textoTerciario,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}
