package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.conversa.app.core.model.CamadaMapa
import com.conversa.app.core.model.DescricaoTrajeto
import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.descricaoTrajeto
import com.conversa.app.core.model.formatarCoordenadas
import com.conversa.app.core.model.linkMapa
import com.conversa.app.core.model.nomePonto
import com.conversa.app.core.network.dto.lerLocalizacao
import com.conversa.app.core.ui.tema.ConversaTema

/** Largura do mapa na bolha (o web usa 288 px) e altura (160 px). */
private val LARGURA_MAPA_BOLHA = 264.dp
private val ALTURA_MAPA_BOLHA = 160.dp

/** A linha do trajeto com o texto do web: "Trajeto", "Trajeto · 12,3 km em linha reta", "Trajeto · 12,3 km · 18 min de carro". */
@Composable
internal fun textoDoTrajeto(localizacao: Localizacao): String = when (val descricao = descricaoTrajeto(localizacao)) {
    DescricaoTrajeto.SoTrajeto -> stringResource(R.string.trajeto)
    is DescricaoTrajeto.LinhaReta -> stringResource(R.string.trajeto_linha_reta, descricao.distancia)
    is DescricaoTrajeto.DeCarro -> stringResource(R.string.trajeto_de_carro, descricao.distancia, descricao.tempo)
}

/**
 * Localização ou trajeto na bolha (MSG-25, conteúdo tipo 9; web `ConteudoLocalizacao.vue`): o mapa
 * (tocar amplia), o nome do local ou A e B com a descrição do trajeto, e "Abrir no Google Maps".
 * JSON que não se lê: só "Localização".
 */
@Composable
internal fun ConteudoLocalizacao(conteudo: String, cor: Color, aoAmpliar: (Localizacao) -> Unit) {
    val localizacao = remember(conteudo) { lerLocalizacao(conteudo) }
    if (localizacao == null) {
        Text(stringResource(R.string.localizacao), color = cor, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val uri = LocalUriHandler.current
    Column(Modifier.width(LARGURA_MAPA_BOLHA), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val estado = rememberEstadoMapa(localizacao, LARGURA_MAPA_BOLHA, ALTURA_MAPA_BOLHA)
        MapaDeTiles(
            estado,
            localizacao,
            Modifier
                .fillMaxWidth()
                .height(ALTURA_MAPA_BOLHA)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClickLabel = stringResource(R.string.ampliar_mapa), role = Role.Button) { aoAmpliar(localizacao) },
        )
        if (localizacao.trajeto) {
            localizacao.pontos.forEachIndexed { i, ponto -> LinhaDoPonto(if (i == 0) "A" else "B", i == 0, nomePonto(ponto), cor, 16.dp) }
            Text(textoDoTrajeto(localizacao), color = cor.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        } else {
            Text(
                nomePonto(localizacao.pontos.first()),
                color = cor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(R.string.abrir_no_google_maps),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.End)
                .clickable(role = Role.Button) { uri.openUri(linkMapa(localizacao)) }
                .padding(vertical = 4.dp),
        )
    }
}

/** Um ponto do trajeto: o círculo com A (verde) ou B (vermelho) e o nome. */
@Composable
private fun LinhaDoPonto(letra: String, origem: Boolean, nome: String, cor: Color, tamanho: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(
                tamanho,
            ).clip(CircleShape).background(if (origem) ConversaTema.cores.chamadaAtender else ConversaTema.cores.chamadaEncerrar),
            contentAlignment = Alignment.Center,
        ) {
            Text(letra, color = Color.White, fontSize = (tamanho.value * 0.56f).sp, fontWeight = FontWeight.Bold)
        }
        Text(nome, color = cor, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** O estado do mapa enquadrado num tamanho conhecido em dp (a bolha). */
@Composable
internal fun rememberEstadoMapa(localizacao: Localizacao, largura: Dp, altura: Dp): EstadoMapa {
    val densidade = LocalDensity.current
    return remember(localizacao) {
        with(densidade) { EstadoMapa(enquadramentoDe(localizacao, largura.toPx(), altura.toPx())) }
    }
}

/**
 * O mapa grande (web `VisualizadorLocalizacao.vue`): título (os nomes, "A → B" no trajeto),
 * subtítulo (coordenadas ou a descrição do trajeto), "Abrir no Google Maps", ruas ou satélite,
 * arrastar e pinça; no trajeto, A e B com o nome inteiro embaixo.
 */
@Composable
internal fun VisualizadorLocalizacao(localizacao: Localizacao, aoFechar: () -> Unit) {
    val uri = LocalUriHandler.current
    var satelite by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (localizacao.trajeto) {
                                localizacao.pontos.joinToString(" → ") {
                                    nomePonto(it)
                                }
                            } else {
                                nomePonto(localizacao.pontos.first())
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (localizacao.trajeto) {
                                textoDoTrajeto(localizacao)
                            } else {
                                localizacao.pontos.first().let { formatarCoordenadas(it.lat, it.lng) }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = ConversaTema.cores.textoTerciario,
                        )
                    }
                    IconButton(onClick = aoFechar) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fechar)) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { uri.openUri(linkMapa(localizacao)) }) { Text(stringResource(R.string.abrir_no_google_maps)) }
                    TextButton(onClick = { satelite = !satelite }) {
                        Text(stringResource(if (satelite) R.string.mapa_ruas else R.string.mapa_satelite))
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val densidade = LocalDensity.current
                    val estado = remember(localizacao) {
                        with(densidade) {
                            EstadoMapa(enquadramentoDe(localizacao, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
                        }
                    }
                    MapaDeTiles(
                        estado,
                        localizacao,
                        Modifier.fillMaxSize(),
                        camada = if (satelite) CamadaMapa.SATELITE else CamadaMapa.RUAS,
                        interativo = true,
                    )
                }
                if (localizacao.trajeto) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        localizacao.pontos.forEachIndexed { i, ponto ->
                            LinhaDoPonto(if (i == 0) "A" else "B", i == 0, nomePonto(ponto), MaterialTheme.colorScheme.onSurface, 20.dp)
                        }
                    }
                }
            }
        }
    }
}
