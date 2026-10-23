package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.TabelaDeDados
import com.conversa.app.core.ui.tema.ConversaTema

/** Coluna larga demais quebra o texto a partir daqui. */
private val LARGURA_MAXIMA_COLUNA = 240.dp
private val LARGURA_MINIMA_COLUNA = 24.dp
private val ESPACO_CELULA = 8.dp

/**
 * O bloco ```csv/```tsv como tabela (MSG-26; web `tabelaCsv.ts`): o cabeçalho em negrito com o
 * fundo do cabeçalho do código, as linhas com divisórias, as colunas de número à direita e
 * rolagem horizontal. Cada coluna tem a largura do maior texto dela, até 240 dp.
 */
@Composable
internal fun TabelaNaBolha(tabela: TabelaDeDados, modifier: Modifier = Modifier) {
    val estilo = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val estiloCabecalho = estilo.copy(fontWeight = FontWeight.SemiBold)
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    val larguras = remember(tabela, estilo) {
        tabela.cabecalho.indices.map { i ->
            val maior = (listOf(tabela.cabecalho[i] to estiloCabecalho) + tabela.corpo.map { it[i] to estilo })
                .maxOf { (texto, comEstilo) -> medidor.measure(texto, comEstilo).size.width }
            with(densidade) { maior.toDp() }.coerceIn(LARGURA_MINIMA_COLUNA, LARGURA_MAXIMA_COLUNA) + ESPACO_CELULA * 2
        }
    }
    val divisor = ConversaTema.cores.divisorLista
    Box(modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, divisor)) {
            LinhaDaTabela(tabela.cabecalho, larguras, null, estiloCabecalho, divisor, ConversaTema.cores.campoEntrada)
            tabela.corpo.forEach { linha ->
                HorizontalDivider(color = divisor)
                LinhaDaTabela(linha, larguras, tabela.aDireita, estilo, divisor, Color.Transparent)
            }
        }
    }
}

/** Uma linha: as células com a largura da coluna e a altura da mais alta; [aDireita] nulo no cabeçalho. */
@Composable
private fun LinhaDaTabela(
    celulas: List<String>,
    larguras: List<Dp>,
    aDireita: List<Boolean>?,
    estilo: TextStyle,
    divisor: Color,
    fundo: Color,
) {
    Row(Modifier.height(IntrinsicSize.Min).background(fundo)) {
        celulas.forEachIndexed { i, celula ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(divisor))
            Text(
                celula,
                style = estilo,
                textAlign = if (aDireita?.getOrNull(i) == true) TextAlign.End else TextAlign.Start,
                modifier = Modifier.width(larguras[i]).padding(horizontal = ESPACO_CELULA, vertical = 4.dp),
            )
        }
    }
}
