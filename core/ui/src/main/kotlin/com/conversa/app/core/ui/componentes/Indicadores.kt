package com.conversa.app.core.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.PreviaConversa
import com.conversa.app.core.model.RotuloData
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema

/** Três pontinhos pulando: alguém está digitando (lista de conversas e cabeçalho do chat). */
@Composable
fun IndicadorDigitando(modifier: Modifier = Modifier, cor: Color = MaterialTheme.colorScheme.primary) {
    val descricao = stringResource(R.string.digitando)
    val transicao = rememberInfiniteTransition(label = "digitando")
    Row(
        modifier = modifier.semantics { contentDescription = descricao },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { indice ->
            val alfa by transicao.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, delayMillis = indice * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "ponto$indice",
            )
            Box(Modifier.size(6.dp).alpha(alfa).background(cor, CircleShape))
        }
    }
}

/**
 * Faixa "Sem conexão em tempo real" (GER-02): aparece depois de 5 s com o socket
 * fora, com o botão "Tentar agora". Cor: `avisoConexao` (docs/design/cores.md).
 */
@Composable
fun FaixaSemConexao(visivel: Boolean, aoTentarAgora: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visivel, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConversaTema.cores.avisoConexao)
                .padding(start = 16.dp, end = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sem_tempo_real),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = aoTentarAgora) { Text(stringResource(R.string.tentar_agora), color = Color.Black) }
        }
    }
}

/** Texto da prévia na lista de conversas. */
@Composable
fun textoDaPrevia(previa: PreviaConversa): String = when (previa) {
    PreviaConversa.SemMensagens -> stringResource(R.string.previa_sem_mensagens)
    PreviaConversa.Imagem -> stringResource(R.string.previa_imagem)
    PreviaConversa.Figurinha -> stringResource(R.string.previa_figurinha)
    PreviaConversa.SemTexto -> stringResource(R.string.previa_sem_texto)
    is PreviaConversa.Texto -> previa.texto
}

@Composable
fun textoDoRotulo(rotulo: RotuloData): String = when (rotulo) {
    is RotuloData.Hora -> rotulo.texto
    RotuloData.Ontem -> stringResource(R.string.ontem)
    is RotuloData.Data -> rotulo.texto
}

@Preview(showBackground = true)
@Composable
private fun IndicadoresPreview() {
    ConversaTema {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FaixaSemConexao(visivel = true, aoTentarAgora = {})
            IndicadorDigitando(Modifier.padding(16.dp))
        }
    }
}
