package com.conversa.app.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.app.core.model.SegmentoCodigo
import com.conversa.app.core.model.TipoToken
import com.conversa.app.core.model.destacarCodigo
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.delay

/** Altura do bloco recolhido (o `max-h-60` do web). */
private val ALTURA_RECOLHIDO = 240.dp

/** Quanto tempo o "Copiado!" fica (como o web). */
private const val COPIADO_MS = 2_000L

/**
 * Bloco de código na bolha (MSG-15, TODO 7.9), como o `MessageContent.vue`: cabeçalho com a
 * linguagem ("code" sem ela) e "Copiar" → "Copiado!"; o código com destaque de sintaxe, rolagem
 * horizontal e, acima de 240 dp, recolhido com "Expandir código" / "Recolher código".
 */
@Composable
internal fun BlocoCodigo(codigo: SegmentoCodigo.Codigo) {
    val contexto = LocalContext.current
    var copiado by remember { mutableStateOf(false) }
    var expandido by rememberSaveable(codigo.conteudo) { mutableStateOf(false) }
    var longo by remember(codigo.conteudo) { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) {
            delay(COPIADO_MS)
            copiado = false
        }
    }
    val corFundo = MaterialTheme.colorScheme.surface
    val corCabecalho = ConversaTema.cores.campoEntrada
    val texto = textoDestacado(codigo)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(corFundo)) {
        Row(
            Modifier.fillMaxWidth().background(corCabecalho).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(codigo.linguagem ?: "code", style = MaterialTheme.typography.labelSmall, color = ConversaTema.cores.textoTerciario)
            Text(
                stringResource(if (copiado) R.string.copiado else R.string.copiar),
                style = MaterialTheme.typography.labelSmall,
                color = if (copiado) ConversaTema.cores.chamadaAtender else ConversaTema.cores.iconeAcao,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        contexto.getSystemService(
                            ClipboardManager::class.java,
                        )?.setPrimaryClip(ClipData.newPlainText(null, codigo.conteudo))
                        copiado = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Box {
            Text(
                texto,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { if (it.didOverflowHeight) longo = true },
                modifier = Modifier
                    .then(if (expandido) Modifier else Modifier.heightIn(max = ALTURA_RECOLHIDO))
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            )
            if (longo && !expandido) {
                // O fim some num degradê, como no web.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, corFundo))),
                )
            }
        }
        if (longo) {
            Text(
                stringResource(if (expandido) R.string.recolher_codigo else R.string.expandir_codigo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(corCabecalho)
                    .clickable { expandido = !expandido }
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * As cores do destaque vêm de tokens que já existem (`docs/design/cores.md` §6.4): o FMX não
 * tem destaque de sintaxe.
 */
@Composable
private fun textoDestacado(codigo: SegmentoCodigo.Codigo): AnnotatedString {
    val palavra = MaterialTheme.colorScheme.primary
    val cores = ConversaTema.cores
    return remember(codigo, palavra, cores) {
        val tokens = destacarCodigo(codigo.conteudo, codigo.linguagem)
        buildAnnotatedString {
            append(codigo.conteudo)
            for (token in tokens) {
                val estilo = when (token.tipo) {
                    TipoToken.PALAVRA_CHAVE, TipoToken.TAG -> SpanStyle(color = palavra)
                    TipoToken.TEXTO -> SpanStyle(color = cores.chamadaAtender)
                    TipoToken.COMENTARIO -> SpanStyle(color = cores.textoTerciario, fontStyle = FontStyle.Italic)
                    TipoToken.NUMERO -> SpanStyle(color = cores.chamadaEncerrar)
                    TipoToken.ATRIBUTO -> SpanStyle(color = cores.link)
                }
                addStyle(estilo, token.inicio, token.fim)
            }
        }
    }
}
