package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema
import java.text.BreakIterator

/**
 * Primeira "letra" de verdade do nome: o primeiro grafema, que funciona com
 * acento combinado e emoji (GER-07). Nome vazio vira "?".
 */
fun inicialDoNome(nome: String?): String {
    val limpo = nome?.trim().orEmpty()
    if (limpo.isEmpty()) return "?"
    val iterador = BreakIterator.getCharacterInstance()
    iterador.setText(limpo)
    val fim = iterador.next().takeIf { it != BreakIterator.DONE } ?: limpo.length
    return limpo.substring(0, fim).uppercase()
}

/**
 * Avatar redondo: a foto quando há URL e ela carrega; senão a inicial
 * (cores do FMX: fundo claro, letra azul). Imagem que falha volta à inicial.
 */
@Composable
fun Avatar(
    nome: String?,
    url: String?,
    modifier: Modifier = Modifier,
    tamanho: Dp = 40.dp,
    online: Boolean = false,
) {
    val textoOnline = stringResource(R.string.online)
    Box(
        modifier = modifier
            .size(tamanho)
            .semantics { contentDescription = if (online) "${nome.orEmpty()}, $textoOnline" else nome.orEmpty() },
    ) {
        ImagemAvatar(nome, url, tamanho)
        if (online) {
            // Bolinha verde no canto (PRE-01), com borda da cor do fundo para destacar.
            val diametro = (tamanho.value * 0.3f).coerceAtLeast(10f).dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(diametro)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ConversaTema.cores.online),
            )
        }
    }
}

@Composable
private fun ImagemAvatar(nome: String?, url: String?, tamanho: Dp) {
    var falhou by remember(url) { mutableStateOf(false) }
    val cores = ConversaTema.cores
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(CircleShape)
            .background(cores.avatarFundo),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank() || falhou) {
            Text(
                text = inicialDoNome(nome),
                color = cores.avatarLetra,
                fontWeight = FontWeight.SemiBold,
                fontSize = (tamanho.value * 0.42f).sp,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { falhou = true },
                modifier = Modifier.size(tamanho),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AvatarPreview() {
    ConversaTema {
        Row {
            Avatar(nome = "Ana Souza", url = null)
            Avatar(nome = "Élcio", url = null, tamanho = 56.dp)
            Avatar(nome = "😀 Festa", url = null, online = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF323232)
@Composable
private fun AvatarEscuroPreview() {
    ConversaTema(escuro = true) { Avatar(nome = "Bruno", url = null) }
}
