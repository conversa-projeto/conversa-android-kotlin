package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Postagem
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** O que a bolha da postagem (7.13) pede ao ViewModel. */
class AcoesPostagem(
    /** Eu: "Você" nas minhas respostas. */
    val eu: Long = 0,
    /** Minhas respostas ainda na fila (enviando ou com falha), por postagem. */
    val pendentes: StateFlow<Map<Long, List<Mensagem>>> = MutableStateFlow(emptyMap()),
    /** A postagem cuja resposta está no campo do chat. */
    val respondendo: StateFlow<Long?> = MutableStateFlow(null),
    /** Foto de cada membro do grupo, para as respostas. */
    val avatares: StateFlow<Map<Long, String?>> = MutableStateFlow(emptyMap()),
    /** "Ver todas": as respostas da postagem, da mais antiga à mais nova. */
    val respostas: suspend (Long) -> Result<List<Mensagem>> = { Result.success(emptyList()) },
    /** "Responder à postagem": a próxima mensagem do campo vai para baixo dela. */
    val aoResponder: (Mensagem) -> Unit = {},
)

/**
 * Postagem de grupo (7.13, MSG-22, `BolhaPostagem.vue` do web `e33ecba`): um cartão da largura do
 * chat, de quem quer que seja. Em cima, "Postagem · <quem>" e o título; no meio, os conteúdos,
 * como numa mensagem; embaixo, as respostas (as 2 últimas, ou todas com "Ver todas as N") e o
 * botão "Responder à postagem", que leva a resposta para o campo do chat.
 */
@Composable
internal fun BolhaPostagem(mensagem: Mensagem, propria: Boolean, acoes: AcoesBolha, progresso: Float?) {
    val postagem = mensagem.postagem ?: return
    val forma = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, forma),
    ) {
        CabecalhoPostagem(mensagem, postagem, propria)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Como no web: o conteúdo com as cores de uma mensagem recebida, de quem quer que seja.
            CorpoPadrao(mensagem, propria = false, acoes, Modifier.fillMaxWidth(), progresso)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RespostasDaPostagem(mensagem, postagem, acoes)
    }
}

@Composable
private fun CabecalhoPostagem(mensagem: Mensagem, postagem: Postagem, propria: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Outlined.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.postagem),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "· " + if (propria) stringResource(R.string.voce) else mensagem.remetente,
                style = MaterialTheme.typography.labelMedium,
                color = ConversaTema.cores.textoTerciario,
                maxLines = 1,
            )
        }
        postagem.titulo?.takeIf { it.isNotBlank() }?.let { titulo ->
            Text(
                titulo,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * As respostas: a contagem ("Nenhuma resposta ainda", "1 resposta", "N respostas"), o selo
 * "N novas", as 2 últimas (ou todas, lidas de novo a cada resposta nova), as minhas ainda na
 * fila e o botão de responder.
 */
@Composable
private fun RespostasDaPostagem(mensagem: Mensagem, postagem: Postagem, acoes: AcoesBolha) {
    val acoesPostagem = acoes.postagens
    var verTodas by rememberSaveable(mensagem.id) { mutableStateOf(false) }
    var todas by remember(mensagem.id) { mutableStateOf<List<Mensagem>?>(null) }
    LaunchedEffect(verTodas, postagem.respostas, postagem.ultimas.lastOrNull()?.id) {
        if (verTodas) acoesPostagem.respostas(mensagem.id).onSuccess { todas = it }
    }
    val pendentes by acoesPostagem.pendentes.collectAsStateWithLifecycle()
    val respondendo by acoesPostagem.respondendo.collectAsStateWithLifecycle()
    val avatares by acoesPostagem.avatares.collectAsStateWithLifecycle()
    val total = postagem.respostas
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (total ==
                    0
                ) {
                    stringResource(R.string.nenhuma_resposta_ainda)
                } else {
                    pluralStringResource(R.plurals.postagem_respostas, total, total)
                },
                style = MaterialTheme.typography.labelMedium,
                color = ConversaTema.cores.textoTerciario,
            )
            if (postagem.naoLidas > 0) {
                Text(
                    pluralStringResource(R.plurals.postagem_novas, postagem.naoLidas, postagem.naoLidas),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ConversaTema.cores.textoBadge,
                    modifier = Modifier.background(ConversaTema.cores.badgeNaoLidas, CircleShape).padding(horizontal = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (total > postagem.ultimas.size) {
                TextButton(onClick = { verTodas = !verTodas }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (verTodas) stringResource(R.string.mostrar_menos) else stringResource(R.string.ver_todas_as_respostas, total))
                }
            }
        }
        val visiveis = (if (verTodas) todas else null) ?: postagem.ultimas
        visiveis.forEach { LinhaResposta(it, acoesPostagem.eu, avatares[it.remetenteId], acoes) }
        pendentes[mensagem.id].orEmpty().forEach { LinhaResposta(it, acoesPostagem.eu, avatares[it.remetenteId], acoes) }
        BotaoResponderPostagem(
            respondendoAqui = respondendo == mensagem.id,
            // A postagem ainda saindo não tem id no servidor: ainda não dá para responder.
            habilitado = mensagem.id > 0,
            aoResponder = { acoesPostagem.aoResponder(mensagem) },
        )
    }
}

/** Uma resposta: avatar, quem ("Você"), a hora e os conteúdos; a apagada vira "Mensagem apagada". */
@Composable
private fun LinhaResposta(resposta: Mensagem, eu: Long, avatar: String?, acoes: AcoesBolha) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Avatar(resposta.remetente, avatar, Modifier.padding(top = 2.dp), tamanho = 24.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (resposta.remetenteId == eu) stringResource(R.string.voce) else resposta.remetente,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(horaDa(resposta), style = MaterialTheme.typography.labelSmall, color = ConversaTema.cores.textoTerciario)
                if (resposta.enviando) {
                    Icon(
                        Icons.Outlined.AccessTime,
                        contentDescription = stringResource(R.string.status_enviando),
                        tint = ConversaTema.cores.textoTerciario,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (resposta.oculta) {
                Text(
                    stringResource(R.string.mensagem_apagada),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = ConversaTema.cores.textoTerciario,
                )
            } else {
                val cor = ConversaTema.cores.textoBolhaOutro
                resposta.conteudos.forEach { ConteudoNaBolha(resposta, it, propria = false, cor, acoes) }
            }
            if (resposta.falhou) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { acoes.aoReenviar(resposta.id) }) { Text(stringResource(R.string.reenviar)) }
                    TextButton(onClick = { acoes.aoDescartar(resposta.id) }) {
                        Text(stringResource(R.string.apagar), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** "Responder à postagem" (ou "Respondendo no campo de mensagem", com a resposta já no campo). */
@Composable
private fun BotaoResponderPostagem(respondendoAqui: Boolean, habilitado: Boolean, aoResponder: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    val cor = if (respondendoAqui) MaterialTheme.colorScheme.primary else ConversaTema.cores.textoTerciario
    Row(
        Modifier
            .padding(top = 4.dp, bottom = 2.dp)
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(forma)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, if (respondendoAqui) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, forma)
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoResponder)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null, tint = cor, modifier = Modifier.size(16.dp))
        Text(
            stringResource(if (respondendoAqui) R.string.respondendo_no_campo else R.string.responder_a_postagem),
            style = MaterialTheme.typography.bodyMedium,
            color = cor,
        )
    }
}
