package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.ui.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HORA = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "Ver chat da chamada" / "Esconder o chat da chamada" (CHA-26, FC-727, web `BolhaChamada.vue`):
 * o chat criado durante a ligação, só para ler, dentro da bolha da ligação ou do histórico.
 * Aberto, lê as mensagens ([carregar], até 100) e mostra quem, quando e o [conteudo] de cada
 * uma (quem usa decide como desenhar); embaixo, "Abrir o chat completo" ([aoAbrir]).
 */
@Composable
fun ChatDaChamada(
    cor: Color,
    carregar: suspend () -> Result<List<Mensagem>>,
    aoAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    conteudo: @Composable (Mensagem) -> Unit,
) {
    var aberto by rememberSaveable { mutableStateOf(false) }
    val rotulo = stringResource(if (aberto) R.string.chat_chamada_esconder else R.string.chat_chamada_ver)
    Column(modifier) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clickable(role = Role.Button) { aberto = !aberto }
                .semantics { stateDescription = rotulo },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                if (aberto) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = cor,
                modifier = Modifier.size(16.dp),
            )
            Text(rotulo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = cor)
        }
        if (aberto) MensagensDoChat(cor, carregar, aoAbrir, conteudo)
    }
}

@Composable
private fun MensagensDoChat(
    cor: Color,
    carregar: suspend () -> Result<List<Mensagem>>,
    aoAbrir: () -> Unit,
    conteudo: @Composable (Mensagem) -> Unit,
) {
    var resultado by remember { mutableStateOf<Result<List<Mensagem>>?>(null) }
    LaunchedEffect(Unit) { resultado = carregar() }
    val atual = resultado
    val estilo = MaterialTheme.typography.bodySmall
    Column(Modifier.padding(top = 4.dp)) {
        when {
            atual == null -> Text(stringResource(R.string.chat_chamada_carregando), style = estilo, color = cor.copy(alpha = 0.8f))
            atual.isFailure -> Text(stringResource(R.string.chat_chamada_erro), style = estilo, color = cor.copy(alpha = 0.8f))
            else -> {
                val mensagens = atual.getOrThrow().filter { !it.oculta && it.conteudos.isNotEmpty() }
                if (mensagens.isEmpty()) {
                    Text(stringResource(R.string.chat_chamada_vazio), style = estilo, color = cor.copy(alpha = 0.8f))
                } else {
                    // Como uma citação com várias pessoas: a linha à esquerda e as mensagens.
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(2.dp).fillMaxHeight().background(cor.copy(alpha = 0.6f)))
                        Column(
                            Modifier.padding(start = 8.dp).heightIn(max = 384.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            mensagens.forEach { mensagem ->
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            mensagem.remetente,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cor,
                                        )
                                        Text(
                                            HORA.format(mensagem.dataEfetiva.atZone(ZoneId.systemDefault())),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cor.copy(alpha = 0.7f),
                                        )
                                    }
                                    conteudo(mensagem)
                                }
                            }
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.chat_chamada_abrir),
            style = MaterialTheme.typography.labelMedium.copy(textDecoration = TextDecoration.Underline),
            color = cor,
            modifier = Modifier.padding(
                top = 6.dp,
            ).heightIn(min = 32.dp).clickable(role = Role.Button, onClick = aoAbrir).padding(vertical = 6.dp),
        )
    }
}
