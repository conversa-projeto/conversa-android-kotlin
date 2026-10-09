package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.ocorrencias
import com.conversa.app.core.model.textoParaPesquisa
import com.conversa.app.core.model.tipoParaPesquisa
import com.conversa.app.core.model.trechoComTermo
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")

/** O texto com o [termo] destacado (negrito sobre a primária clara; o FMX não tem amarelo de marca-texto). */
fun destacarTermo(texto: String, termo: String, fundo: Color): AnnotatedString = buildAnnotatedString {
    append(texto)
    ocorrencias(texto, termo).forEach { faixa ->
        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, background = fundo), faixa.first, faixa.last + 1)
    }
}

/**
 * Um resultado da pesquisa (8.2, como o `PesquisaAvancada.vue`): quem mandou, a data
 * `dd/MM/aa HH:mm` e o trecho com o termo destacado; sem texto, "Imagem", "Áudio" ou "Arquivo".
 */
@Composable
fun LinhaResultadoPesquisa(mensagem: Mensagem, termo: String, aoAbrir: () -> Unit, modifier: Modifier = Modifier) {
    val fundo = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val texto = textoParaPesquisa(mensagem)
    val semTexto = when (tipoParaPesquisa(mensagem)) {
        TipoConteudo.IMAGEM -> stringResource(R.string.previa_imagem)
        TipoConteudo.AUDIO -> stringResource(R.string.previa_audio)
        else -> stringResource(R.string.previa_arquivo)
    }
    val trecho = remember(texto, termo, fundo, semTexto) {
        if (texto == null) AnnotatedString(semTexto) else destacarTermo(trechoComTermo(texto, termo), termo, fundo)
    }
    Column(
        modifier.fillMaxWidth().clickable(onClick = aoAbrir).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                mensagem.remetente,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                FORMATO_DATA_HORA.format(mensagem.dataEfetiva.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelSmall,
                color = ConversaTema.cores.textoTerciario,
            )
        }
        Text(
            trecho,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
