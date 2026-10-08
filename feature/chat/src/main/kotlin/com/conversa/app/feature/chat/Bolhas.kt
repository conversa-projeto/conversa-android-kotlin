package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.app.core.model.ChamadaNaMensagem
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.ResumoCitacao
import com.conversa.app.core.model.SegmentoCodigo
import com.conversa.app.core.model.SegmentoTexto
import com.conversa.app.core.model.StatusEntrega
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoExibicao
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.classificarMensagem
import com.conversa.app.core.model.ehVideo
import com.conversa.app.core.model.formatarDuracao
import com.conversa.app.core.model.resumoCitacao
import com.conversa.app.core.model.separarBlocosDeCodigo
import com.conversa.app.core.model.separarTexto
import com.conversa.app.core.model.statusEntrega
import com.conversa.app.core.network.dto.lerChamadaDaMensagem
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

/** Hora `HH:mm` direto do `Instant` no fuso do aparelho (nada de `toString()`, problema #29). */
fun horaDa(mensagem: Mensagem, zona: ZoneId = ZoneId.systemDefault()): String = FORMATO_HORA.format(mensagem.dataEfetiva.atZone(zona))

/** O que a bolha pode pedir. */
data class AcoesBolha(
    val aoReenviar: (Long) -> Unit = {},
    val aoDescartar: (Long) -> Unit = {},
    val aoMencao: (Long) -> Unit = {},
    val aoLigar: (TipoChamada) -> Unit = {},
    val aoAbrirArquivo: (Conteudo) -> Unit = {},
    /** Salvar em Downloads (ANX-09). */
    val aoBaixar: (Conteudo) -> Unit = {},
    val aoCompartilhar: (Conteudo) -> Unit = {},
    /** URL assinada do vídeo (ou o arquivo local, enviando) para o visualizador tocar. */
    val urlDoVideo: suspend (Conteudo) -> String? = { null },
    val aoAbrirImagem: (Mensagem, Conteudo) -> Unit = { _, _ -> },
    val aoAlternarAudio: (Mensagem, Conteudo) -> Unit = { _, _ -> },
    /** Transcrição de áudio (identificador do anexo). */
    val aoTranscrever: (String) -> Unit = {},
    val aoAcompanharTranscricao: (String) -> Unit = {},
    /** Chave do áudio ([chaveDoAudio]) e fração da barra (0..1). */
    val aoBuscarAudio: (String, Float) -> Unit = { _, _ -> },
)

/**
 * Uma mensagem na lista (MSG-07): escolhe a bolha por [classificarMensagem].
 * Minhas à direita (cor `bolhaPropria`), dos outros à esquerda (`bolhaOutro`), cores do FMX.
 */
@Composable
fun LinhaMensagem(mensagem: Mensagem, propria: Boolean, mostrarRemetente: Boolean, acoes: AcoesBolha, progresso: Float? = null) {
    // Bolha ocupa no máximo 80% da largura da janela.
    val larguraMax = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.width * 0.8f).toDp() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = if (propria) Alignment.End else Alignment.Start,
    ) {
        if (mostrarRemetente) {
            Text(
                mensagem.remetente,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
            )
        }
        Box(Modifier.widthIn(max = larguraMax)) {
            when (classificarMensagem(mensagem)) {
                TipoExibicao.EMOJI -> BolhaEmoji(mensagem, propria)
                TipoExibicao.CHAMADA -> BolhaChamada(mensagem, propria, acoes)
                TipoExibicao.OCULTA -> BolhaOculta(mensagem, propria)
                TipoExibicao.TEXTO_CURTO -> Fundo(propria) { TextoCurto(mensagem, propria, acoes) }
                TipoExibicao.CODIGO -> Fundo(propria) { CorpoPadrao(mensagem, propria, acoes) }
                TipoExibicao.IMAGEM -> BolhaImagem(mensagem, propria, progresso, acoes)
                else -> Fundo(propria) { CorpoPadrao(mensagem, propria, acoes, progresso) }
            }
        }
        if (mensagem.falhou) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { acoes.aoReenviar(mensagem.id) }) { Text(stringResource(R.string.reenviar)) }
                TextButton(onClick = { acoes.aoDescartar(mensagem.id) }) {
                    Text(stringResource(R.string.apagar), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun Fundo(propria: Boolean, conteudo: @Composable () -> Unit) {
    val cores = ConversaTema.cores
    val forma = if (propria) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    Box(
        Modifier
            .background(if (propria) cores.bolhaPropria else cores.bolhaOutro, forma)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { conteudo() }
}

private fun corTexto(propria: Boolean): @Composable () -> Color = {
    if (propria) ConversaTema.cores.textoBolhaPropria else ConversaTema.cores.textoBolhaOutro
}

/** Bolha curta (≤ 60 caracteres, uma linha): hora ao lado. */
@Composable
private fun TextoCurto(mensagem: Mensagem, propria: Boolean, acoes: AcoesBolha) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextoRico(mensagem.conteudos.first().conteudo, corTexto(propria)(), acoes, Modifier.weight(1f, fill = false))
        Rodape(mensagem, propria)
    }
}

/** Bolha padrão: citação (se houver), cada conteúdo e o rodapé embaixo à direita. */
@Composable
private fun CorpoPadrao(mensagem: Mensagem, propria: Boolean, acoes: AcoesBolha, progresso: Float? = null) {
    val cor = corTexto(propria)()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        mensagem.referencia?.let { referencia ->
            referencia.mensagem?.let { citada -> Citacao(referencia.tipo, citada.remetente, resumoCitacao(citada)) }
        }
        mensagem.conteudos.forEach { ConteudoNaBolha(mensagem, it, propria, cor, acoes) }
        progresso?.let { androidx.compose.material3.LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
        Rodape(mensagem, propria, Modifier.align(Alignment.End))
    }
}

@Composable
private fun ConteudoNaBolha(mensagem: Mensagem, conteudo: Conteudo, propria: Boolean, cor: Color, acoes: AcoesBolha) {
    when (conteudo.tipo) {
        TipoConteudo.TEXTO -> separarBlocosDeCodigo(conteudo.conteudo).forEach { parte ->
            when (parte) {
                is SegmentoCodigo.Texto -> if (parte.conteudo.isNotBlank()) TextoRico(parte.conteudo.trim('\n'), cor, acoes)
                is SegmentoCodigo.Codigo -> BlocoCodigo(parte)
            }
        }
        TipoConteudo.IMAGEM -> ImagemAnexo(
            conteudo,
            Modifier
                .sizeIn(minWidth = 120.dp, minHeight = 90.dp, maxWidth = 240.dp, maxHeight = 280.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { acoes.aoAbrirImagem(mensagem, conteudo) },
        )
        TipoConteudo.AUDIO, TipoConteudo.GRAVACAO_AUDIO -> PlayerNaBolha(mensagem, conteudo, propria, cor, acoes)
        TipoConteudo.ARQUIVO -> if (ehVideo(conteudo)) VideoNaBolha(mensagem, conteudo, acoes) else LinhaArquivo(conteudo, cor, acoes)
        TipoConteudo.FIGURINHA -> Marcador("🏷", stringResource(R.string.conteudo_figurinha), cor)
        TipoConteudo.ENQUETE -> Column {
            Text("📊 " + stringResource(R.string.votacao), color = cor, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.votacao_no_computador), color = cor, style = MaterialTheme.typography.bodySmall)
        }
        TipoConteudo.CHAMADA, TipoConteudo.DESCONHECIDO -> Marcador("•", stringResource(R.string.conteudo_desconhecido), cor)
    }
}

/** Anexos, figurinha e votação ainda sem a bolha própria (etapas 4 e 7). A bolha nunca fica vazia. */
@Composable
private fun Marcador(icone: String, texto: String, cor: Color) {
    Text("$icone $texto", color = cor, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
}

@Composable
private fun BlocoCodigo(codigo: SegmentoCodigo.Codigo) {
    Column(
        Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        codigo.linguagem?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = ConversaTema.cores.textoTerciario)
        }
        Text(
            codigo.conteudo,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/** Citação de resposta ou encaminhada (MSG-06). Toque para ir à original entra na etapa 7. */
@Composable
private fun Citacao(tipo: TipoReferencia, remetente: String, resumo: ResumoCitacao) {
    val titulo = if (tipo == TipoReferencia.ENCAMINHAMENTO) stringResource(R.string.encaminhada_de, remetente) else remetente
    val texto = when (resumo) {
        ResumoCitacao.Oculta -> stringResource(R.string.mensagem_oculta)
        is ResumoCitacao.Texto -> resumo.texto
        is ResumoCitacao.Tipo -> when (resumo.tipo) {
            TipoConteudo.IMAGEM -> stringResource(R.string.conteudo_imagem)
            TipoConteudo.GRAVACAO_AUDIO, TipoConteudo.AUDIO -> stringResource(R.string.conteudo_audio)
            TipoConteudo.FIGURINHA -> stringResource(R.string.conteudo_figurinha)
            TipoConteudo.ENQUETE -> stringResource(R.string.votacao)
            else -> stringResource(R.string.conteudo_arquivo)
        }
    }
    Row(
        Modifier
            .background(Color.Black.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(end = 8.dp),
    ) {
        Box(Modifier.width(3.dp).size(width = 3.dp, height = 40.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(
                texto,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                fontStyle = if (resumo is ResumoCitacao.Oculta) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}

/** Texto com links e menções clicáveis (MSG-11). Link abre no navegador; menção abre a conversa direta. */
@Composable
fun TextoRico(texto: String, cor: Color, acoes: AcoesBolha, modifier: Modifier = Modifier) {
    val corLink = ConversaTema.cores.link
    val corMencao = MaterialTheme.colorScheme.primary
    val anotado: AnnotatedString = buildAnnotatedString {
        separarTexto(texto).forEach { parte ->
            when (parte) {
                is SegmentoTexto.Texto -> append(parte.conteudo)
                is SegmentoTexto.Link -> withLink(
                    LinkAnnotation.Url(
                        parte.destino,
                        TextLinkStyles(SpanStyle(color = corLink, textDecoration = TextDecoration.Underline)),
                    ),
                ) { append(parte.url) }
                is SegmentoTexto.Mencao -> withLink(
                    LinkAnnotation.Clickable("mencao-${parte.usuarioId}") { acoes.aoMencao(parte.usuarioId) },
                ) { withStyle(SpanStyle(color = corMencao, fontWeight = FontWeight.SemiBold)) { append("@${parte.nome}") } }
            }
        }
    }
    Text(anotado, color = cor, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
}

/** Hora e, nas minhas, o status de entrega (MSG-08). */
@Composable
private fun Rodape(mensagem: Mensagem, propria: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(horaDa(mensagem), style = MaterialTheme.typography.labelSmall, color = ConversaTema.cores.horaBolha)
        if (propria) IconeStatus(statusEntrega(mensagem))
    }
}

@Composable
fun IconeStatus(status: StatusEntrega) {
    val cores = ConversaTema.cores
    val (icone, cor, descricao) = when (status) {
        StatusEntrega.ENVIANDO -> Triple(Icons.Outlined.AccessTime, cores.statusEntregue, R.string.status_enviando)
        StatusEntrega.FALHOU -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, R.string.status_falhou)
        StatusEntrega.ENVIADA -> Triple(Icons.Outlined.Check, cores.statusEntregue, R.string.status_enviada)
        StatusEntrega.ENTREGUE -> Triple(Icons.Outlined.DoneAll, cores.statusEntregue, R.string.status_entregue)
        StatusEntrega.LIDA -> Triple(Icons.Outlined.DoneAll, cores.statusLida, R.string.status_lida)
    }
    val texto = stringResource(descricao)
    Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(14.dp).semantics { contentDescription = texto })
}

/** Só emojis (MSG-14): grande e sem fundo. */
@Composable
private fun BolhaEmoji(mensagem: Mensagem, propria: Boolean) {
    Column(horizontalAlignment = if (propria) Alignment.End else Alignment.Start) {
        Text(mensagem.conteudos.first().conteudo, fontSize = 40.sp, lineHeight = 48.sp)
        Rodape(mensagem, propria)
    }
}

/** "Mensagem oculta" (MSG-16): toque revela ou esconde o conteúdo original. */
@Composable
private fun BolhaOculta(mensagem: Mensagem, propria: Boolean) {
    var revelada by rememberSaveable(mensagem.id) { mutableStateOf(false) }
    val cor = ConversaTema.cores.textoTerciario
    Box(
        Modifier
            .background(if (propria) ConversaTema.cores.bolhaPropria else ConversaTema.cores.bolhaOutro, RoundedCornerShape(16.dp))
            .clickable { revelada = !revelada }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.VisibilityOff, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.mensagem_oculta), fontStyle = FontStyle.Italic, color = cor)
                Rodape(mensagem, propria)
            }
            if (revelada) {
                mensagem.conteudos.filter { it.tipo == TipoConteudo.TEXTO }.forEach {
                    Text(it.conteudo, color = cor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

/** Resumo da chamada (MSG-13). Toque liga de novo, com o mesmo tipo. */
@Composable
private fun BolhaChamada(mensagem: Mensagem, propria: Boolean, acoes: AcoesBolha) {
    val dados = lerChamadaDaMensagem(mensagem.conteudos.first().conteudo)
        ?: ChamadaNaMensagem(0, TipoChamada.AUDIO, 0, null, emptyList())
    val cores = ConversaTema.cores
    val corIcone = when {
        dados.encerrada -> cores.chamadaRecebida
        dados.falhou -> cores.chamadaPerdida
        else -> cores.iconeDiscreto
    }
    val titulo = stringResource(
        when {
            dados.tipo == TipoChamada.VIDEO && dados.emGrupo -> R.string.chamada_video_grupo
            dados.tipo == TipoChamada.VIDEO -> R.string.chamada_video
            dados.emGrupo -> R.string.chamada_audio_grupo
            else -> R.string.chamada_audio
        },
    )
    Fundo(propria) {
        Column(Modifier.clickable { acoes.aoLigar(dados.tipo) }.widthIn(min = 200.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(32.dp).background(corIcone.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (dados.tipo == TipoChamada.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call,
                        contentDescription = null,
                        tint = corIcone,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (!dados.emGrupo) {
                        val detalhe = dados.duracaoSegundos?.takeIf { it > 0 }?.let(::formatarDuracao) ?: statusChamada(dados.status)
                        Text(
                            "${mensagem.remetente} · $detalhe",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (dados.falhou) cores.chamadaPerdida else cores.horaBolha,
                        )
                    }
                }
            }
            if (dados.emGrupo) {
                dados.participantes.forEach { p ->
                    Text(
                        "${p.nome} · ${p.duracaoSegundos?.let(::formatarDuracao) ?: statusParticipante(p.status)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 40.dp, top = 2.dp),
                    )
                }
            }
            Text(
                horaDa(mensagem),
                style = MaterialTheme.typography.labelSmall,
                color = cores.horaBolha,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun statusChamada(status: Int): String = when (status) {
    2 -> stringResource(R.string.chamada_recusada)
    5 -> stringResource(R.string.chamada_perdida)
    6 -> stringResource(R.string.chamada_cancelada)
    else -> ""
}

@Composable
private fun statusParticipante(status: Int): String = when (status) {
    1 -> stringResource(R.string.chamada_perdida)
    2 -> stringResource(R.string.participante_recusou)
    5 -> stringResource(R.string.participante_desconectou)
    else -> ""
}
