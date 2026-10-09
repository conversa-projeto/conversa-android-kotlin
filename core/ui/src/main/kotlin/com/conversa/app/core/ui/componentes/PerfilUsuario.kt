package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.FichaUsuario
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * O perfil de outra pessoa (8.4, AUT-10), como o `UserInfoModal.vue` do web: a foto (ou a
 * inicial), o nome, o e-mail e o telefone ("Não informado" quando vazios). [aoLigar] e
 * [aoVerAnexos] só aparecem quando vierem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolhaPerfilUsuario(
    ficha: FichaUsuario,
    aoFechar: () -> Unit,
    aoLigar: (() -> Unit)? = null,
    aoVerAnexos: (() -> Unit)? = null,
) {
    // Superfície branca, como a lista: o fundo da inicial (#F5F5F5) some sobre o fundo padrão da folha.
    ModalBottomSheet(onDismissRequest = aoFechar, containerColor = MaterialTheme.colorScheme.surface) {
        ConteudoPerfilUsuario(ficha, aoLigar, aoVerAnexos)
    }
}

@Composable
private fun ConteudoPerfilUsuario(ficha: FichaUsuario, aoLigar: (() -> Unit)?, aoVerAnexos: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Avatar(ficha.nome, ficha.avatarUrl, tamanho = 120.dp)
        Text(ficha.nome, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Surface(
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Dado(stringResource(R.string.perfil_usuario_email), ficha.email)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Dado(stringResource(R.string.perfil_usuario_telefone), ficha.telefone)
            }
        }
        if (aoLigar != null || aoVerAnexos != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                aoLigar?.let {
                    OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.perfil_usuario_ligar), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                aoVerAnexos?.let {
                    Button(onClick = it, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.perfil_usuario_ver_anexos), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dado(rotulo: String, valor: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelSmall, color = ConversaTema.cores.textoTerciario)
        Text(
            valor ?: stringResource(R.string.perfil_usuario_nao_informado),
            style = MaterialTheme.typography.bodyMedium,
            color = if (valor == null) ConversaTema.cores.textoTerciario else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviaPerfilUsuario() {
    ConversaTema {
        ConteudoPerfilUsuario(FichaUsuario(9, "Bia Souza", "bia@exemplo.test", null, null), aoLigar = {}, aoVerAnexos = {})
    }
}
