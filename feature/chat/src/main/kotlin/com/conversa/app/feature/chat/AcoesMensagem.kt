package com.conversa.app.feature.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.REACOES_A_MOSTRA
import com.conversa.app.core.model.REACOES_RAPIDAS
import com.conversa.app.core.model.Reacao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoExibicao
import com.conversa.app.core.model.classificarMensagem
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Menu só para mensagem de verdade (com id do servidor), que não é chamada nem oculta (TODO 7.1). */
fun podeAbrirMenu(mensagem: Mensagem): Boolean =
    mensagem.id > 0 && !mensagem.oculta && classificarMensagem(mensagem) != TipoExibicao.CHAMADA

/**
 * Toque longo que funciona por cima dos filhos (links, imagem, áudio): olha os eventos
 * antes deles e, quando o toque vira longo, consome o resto para o filho não receber o clique.
 * A chave é a própria [aoTocarLongo]: a LazyColumn reaproveita a composição de um item para
 * outra mensagem, e com `pointerInput(Unit)` o gesto continuaria chamando a função antiga.
 */
fun Modifier.toqueLongo(habilitado: Boolean, aoTocarLongo: () -> Unit): Modifier = if (!habilitado) {
    this
} else {
    pointerInput(aoTocarLongo) {
        awaitEachGesture {
            val inicio = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Soltou, mexeu ou acabou o gesto antes do tempo: não é toque longo.
            val soltou = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var acabou = false
                while (!acabou) {
                    val evento = awaitPointerEvent(PointerEventPass.Initial)
                    val mudanca = evento.changes.firstOrNull { it.id == inicio.id }
                    acabou = mudanca == null ||
                        !mudanca.pressed ||
                        (mudanca.position - inicio.position).getDistance() > viewConfiguration.touchSlop
                }
                true
            }
            if (soltou == null) {
                aoTocarLongo()
                do {
                    val evento = awaitPointerEvent(PointerEventPass.Initial)
                    evento.changes.forEach { it.consume() }
                } while (evento.changes.any { it.pressed })
            }
        }
    }
}

/** Quanto arrastar a bolha para a direita para responder. */
private val LIMIAR_RESPOSTA = 64.dp

/**
 * Arrastar a bolha para a direita além de [LIMIAR_RESPOSTA] responde (7.3, como o WhatsApp):
 * a bolha acompanha o dedo, o ícone de resposta aparece atrás e vibra ao passar do limite.
 */
@Composable
fun DeslizarParaResponder(habilitado: Boolean, aoResponder: () -> Unit, conteudo: @Composable () -> Unit) {
    if (!habilitado) {
        conteudo()
        return
    }
    val limiar = with(LocalDensity.current) { LIMIAR_RESPOSTA.toPx() }
    val deslocamento = remember { Animatable(0f) }
    val escopo = rememberCoroutineScope()
    val haptico = LocalHapticFeedback.current
    var passou by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                escopo.launch {
                    val novo = (deslocamento.value + delta).coerceIn(0f, limiar * 1.4f)
                    deslocamento.snapTo(novo)
                    if (!passou && novo >= limiar) haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    passou = novo >= limiar
                }
            },
            onDragStopped = {
                if (deslocamento.value >= limiar) aoResponder()
                passou = false
                deslocamento.animateTo(0f)
            },
        ),
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Reply,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp).size(24.dp).graphicsLayer {
                val fracao = (deslocamento.value / limiar).coerceIn(0f, 1f)
                alpha = fracao
                scaleX = 0.6f + 0.4f * fracao
                scaleY = 0.6f + 0.4f * fracao
            },
        )
        Box(Modifier.offset { IntOffset(deslocamento.value.roundToInt(), 0) }) { conteudo() }
    }
}

/** Ações do menu da mensagem. */
data class AcoesMenu(
    val aoResponder: () -> Unit,
    val aoResponderNoPrivado: () -> Unit,
    val aoEncaminhar: () -> Unit,
    val aoReagir: (String) -> Unit,
    val aoMaisEmojis: () -> Unit,
    val aoCopiar: () -> Unit,
    val aoOcultar: () -> Unit,
)

/** Toque longo na bolha (TODO 7.1): reações rápidas + "mais" e as ações (textos do web). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuMensagem(mensagem: Mensagem, propria: Boolean, grupo: Boolean, acoes: AcoesMenu, aoFechar: () -> Unit) {
    val haptico = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (emoji in REACOES_RAPIDAS) {
                val reagiu = mensagem.reacoes.any { it.emoji == emoji && it.reagiu }
                Text(
                    emoji,
                    fontSize = 26.sp,
                    modifier = Modifier.clip(CircleShape)
                        .then(if (reagiu) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable {
                            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            acoes.aoReagir(emoji)
                        }
                        .padding(6.dp),
                )
            }
            Icon(
                Icons.Outlined.AddReaction,
                contentDescription = stringResource(R.string.mais_emojis),
                modifier = Modifier.clip(CircleShape).clickable(onClick = acoes.aoMaisEmojis).padding(8.dp),
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        ItemMenu(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.responder), onClick = acoes.aoResponder)
        if (grupo && !propria) {
            ItemMenu(Icons.Outlined.PersonOutline, stringResource(R.string.responder_no_privado), onClick = acoes.aoResponderNoPrivado)
        }
        // Votação não pode ser encaminhada: o servidor recusa (FC-518).
        if (mensagem.conteudos.none { it.tipo == TipoConteudo.ENQUETE }) {
            ItemMenu(Icons.AutoMirrored.Outlined.Forward, stringResource(R.string.encaminhar), onClick = acoes.aoEncaminhar)
        }
        val temTexto = mensagem.conteudos.any { it.tipo == TipoConteudo.TEXTO || it.tipo == TipoConteudo.IMAGEM }
        if (temTexto) {
            ItemMenu(Icons.Outlined.ContentCopy, stringResource(R.string.copiar), onClick = acoes.aoCopiar)
        }
        if (propria && !mensagem.oculta) {
            ItemMenu(Icons.Outlined.VisibilityOff, stringResource(R.string.ocultar), perigo = true, onClick = acoes.aoOcultar)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ItemMenu(icone: androidx.compose.ui.graphics.vector.ImageVector, texto: String, perigo: Boolean = false, onClick: () -> Unit) {
    val cor = if (perigo) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(texto, color = cor) },
        leadingContent = { Icon(icone, contentDescription = null, tint = cor) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/**
 * Chips das reações embaixo da bolha (TODO 7.2): emoji e contagem; destacado se eu reagi.
 * Toque alterna a minha reação; toque longo mostra quem reagiu. Até [REACOES_A_MOSTRA]
 * à mostra; o resto fica num "+N" (como o web), que abre a lista dos demais.
 */
@Composable
fun ChipsDeReacao(
    reacoes: List<Reacao>,
    aoAlternar: (String) -> Unit,
    aoVerQuem: (String) -> Unit,
    modifier: Modifier = Modifier,
    aoVerMais: () -> Unit = {},
) {
    if (reacoes.isEmpty()) return
    val cores = ConversaTema.cores
    val extras = reacoes.drop(REACOES_A_MOSTRA)
    FlowRow(
        modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // O "+N" tem a área de toque mínima (48 dp): os chips ficam centralizados com ele.
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        for (reacao in reacoes.take(REACOES_A_MOSTRA)) {
            val descricao = stringResource(R.string.reacao_descricao, reacao.emoji, reacao.quantidade)
            Surface(
                shape = RoundedCornerShape(50),
                color = if (reacao.reagiu) MaterialTheme.colorScheme.primaryContainer else cores.campoEntrada,
                modifier = Modifier
                    .then(if (reacao.reagiu) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)) else Modifier)
                    .semantics { contentDescription = descricao }
                    .clip(RoundedCornerShape(50))
                    .combinedClickable(onClick = { aoAlternar(reacao.emoji) }, onLongClick = { aoVerQuem(reacao.emoji) }),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(reacao.emoji, fontSize = 14.sp)
                    if (reacao.quantidade > 1) {
                        Spacer(Modifier.width(4.dp))
                        Text(reacao.quantidade.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (extras.isNotEmpty()) {
            val reagiu = extras.any { it.reagiu }
            val descricao = pluralStringResource(R.plurals.mais_reacoes, extras.size, extras.size)
            Surface(
                onClick = aoVerMais,
                shape = RoundedCornerShape(50),
                color = if (reagiu) MaterialTheme.colorScheme.primaryContainer else cores.campoEntrada,
                border = if (reagiu) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.semantics { contentDescription = descricao },
            ) {
                Text(
                    "+${extras.size}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** As reações que não couberam nos chips ("+N"): emoji, quem reagiu e a contagem; toque alterna. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaisReacoes(reacoes: List<Reacao>, aoAlternar: (String) -> Unit, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            stringResource(R.string.reacoes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.heightIn(max = 480.dp).padding(bottom = 16.dp)) {
            items(reacoes, key = { it.emoji }) { reacao ->
                val cor = if (reacao.reagiu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ListItem(
                    leadingContent = { Text(reacao.emoji, fontSize = 22.sp) },
                    headlineContent = {
                        Text(
                            reacao.usuarios.joinToString(", ") { it.nome },
                            color = cor,
                            fontWeight = if (reacao.reagiu) FontWeight.SemiBold else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = { Text(reacao.quantidade.toString(), color = cor) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickable { aoAlternar(reacao.emoji) },
                )
            }
        }
    }
}

/** Quem reagiu (toque longo no chip): foto, nome e hora, por emoji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuemReagiu(reacoes: List<Reacao>, emojiInicial: String, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            stringResource(R.string.reacoes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        val ordenadas = reacoes.sortedByDescending { it.emoji == emojiInicial }
        LazyColumn(Modifier.heightIn(max = 480.dp).padding(bottom = 16.dp)) {
            for (reacao in ordenadas) {
                items(reacao.usuarios, key = { "${reacao.emoji}-${it.usuarioId}" }) { usuario ->
                    ListItem(
                        leadingContent = { Avatar(usuario.nome, usuario.avatarUrl, tamanho = 40.dp) },
                        headlineContent = { Text(usuario.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = usuario.reagidoEm?.let { { Text(horaDaReacao(it)) } },
                        trailingContent = { Text(reacao.emoji, fontSize = 22.sp) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }
}

/** Como o web: só a hora se foi hoje; senão `dd/MM HH:mm`. */
internal fun horaDaReacao(em: Instant, zona: ZoneId = ZoneId.systemDefault(), hoje: LocalDate = LocalDate.now(zona)): String {
    val local = em.atZone(zona)
    return if (local.toLocalDate() == hoje) HORA.format(local) else DIA_HORA.format(local)
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** "Mais emojis": o seletor de emoji do Android (AndroidX). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDeEmoji(aoEscolher: (String) -> Unit, aoFechar: () -> Unit) {
    ModalBottomSheet(onDismissRequest = aoFechar) {
        AndroidView(
            factory = { contexto -> EmojiPickerView(contexto).apply { setOnEmojiPickedListener { aoEscolher(it.emoji) } } },
            modifier = Modifier.fillMaxWidth().height(380.dp),
        )
    }
}

/**
 * Confirmação de ocultar (textos do web). A agendada que ainda não saiu vira "cancelar o envio",
 * porque o servidor apaga de vez (TODO 7.4).
 */
@Composable
fun ConfirmarOcultar(agendada: Boolean, aoConfirmar: () -> Unit, aoCancelar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(stringResource(if (agendada) R.string.cancelar_agendada_titulo else R.string.ocultar_titulo)) },
        text = { Text(stringResource(if (agendada) R.string.cancelar_agendada_texto else R.string.ocultar_texto)) },
        confirmButton = {
            TextButton(onClick = aoConfirmar) {
                Text(stringResource(if (agendada) R.string.cancelar_envio else R.string.ocultar), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(if (agendada) R.string.voltar else R.string.cancelar)) } },
    )
}

/** O que o toque longo abriu: o menu e o que sai dele. */
@Stable
class AcoesAbertas {
    var menuDe by mutableStateOf<Mensagem?>(null)

    /** Id da mensagem que vai receber o emoji do seletor. */
    var seletorPara by mutableStateOf<Long?>(null)

    /** Id da mensagem e o emoji do chip tocado. */
    var quemReagiu by mutableStateOf<Pair<Long, String>?>(null)

    /** Id da mensagem do "+N" tocado. */
    var maisReacoes by mutableStateOf<Long?>(null)

    /** Id da mensagem sendo encaminhada (a folha de destinos). */
    var encaminhando by mutableStateOf<Long?>(null)
    var ocultando by mutableStateOf<Mensagem?>(null)
}

/**
 * Menu da mensagem e o que sai dele (7.1, 7.2, 7.4, 7.6). A mensagem é relida da lista:
 * as reações mudam com a folha aberta (a minha, otimista, ou a de alguém pelo WS 7).
 */
@Composable
internal fun AcoesDaMensagem(abertas: AcoesAbertas, estado: ChatUiState, viewModel: ChatViewModel) {
    fun atual(id: Long): Mensagem? = estado.itens.firstNotNullOfOrNull { (it as? ItemChat.Bolha)?.mensagem?.takeIf { m -> m.id == id } }
    abertas.menuDe?.let { inicial ->
        val mensagem = atual(inicial.id) ?: inicial
        MenuMensagem(
            mensagem,
            propria = mensagem.remetenteId == estado.eu,
            grupo = estado.grupo,
            acoes = AcoesMenu(
                aoResponderNoPrivado = {
                    abertas.menuDe = null
                    viewModel.responderNoPrivado(mensagem)
                },
                aoEncaminhar = {
                    abertas.menuDe = null
                    abertas.encaminhando = mensagem.id
                },
                aoResponder = {
                    abertas.menuDe = null
                    viewModel.responder(mensagem)
                },
                aoReagir = { emoji ->
                    abertas.menuDe = null
                    viewModel.reagir(mensagem, emoji)
                },
                aoMaisEmojis = {
                    abertas.menuDe = null
                    abertas.seletorPara = mensagem.id
                },
                aoCopiar = {
                    abertas.menuDe = null
                    viewModel.copiar(mensagem)
                },
                aoOcultar = {
                    abertas.menuDe = null
                    abertas.ocultando = mensagem
                },
            ),
            aoFechar = { abertas.menuDe = null },
        )
    }
    abertas.seletorPara?.let { id ->
        SeletorDeEmoji(
            aoEscolher = { emoji ->
                abertas.seletorPara = null
                atual(id)?.let { viewModel.reagir(it, emoji) }
            },
            aoFechar = { abertas.seletorPara = null },
        )
    }
    abertas.quemReagiu?.let { (id, emoji) ->
        val reacoes = atual(id)?.reacoes.orEmpty()
        if (reacoes.isEmpty()) {
            // Tiraram a última reação com a folha aberta: fecha.
            LaunchedEffect(Unit) { abertas.quemReagiu = null }
        } else {
            QuemReagiu(reacoes, emoji, aoFechar = { abertas.quemReagiu = null })
        }
    }
    abertas.encaminhando?.let { id ->
        val mensagem = atual(id)
        if (mensagem == null) {
            LaunchedEffect(Unit) { abertas.encaminhando = null }
        } else {
            val destinos by viewModel.destinosEncaminhar.collectAsStateWithLifecycle()
            EncaminharMensagem(
                mensagem,
                destinos,
                aoEscolher = { destino ->
                    abertas.encaminhando = null
                    viewModel.encaminhar(mensagem, destino)
                },
                aoFechar = { abertas.encaminhando = null },
            )
        }
    }
    abertas.maisReacoes?.let { id ->
        val mensagem = atual(id)
        val extras = mensagem?.reacoes.orEmpty().drop(REACOES_A_MOSTRA)
        if (mensagem == null || extras.isEmpty()) {
            LaunchedEffect(Unit) { abertas.maisReacoes = null }
        } else {
            MaisReacoes(
                extras,
                aoAlternar = { emoji ->
                    abertas.maisReacoes = null
                    viewModel.reagir(mensagem, emoji)
                },
                aoFechar = { abertas.maisReacoes = null },
            )
        }
    }
    abertas.ocultando?.let { mensagem ->
        ConfirmarOcultar(
            agendada = mensagem.visivelEm?.isAfter(Instant.now()) == true,
            aoConfirmar = {
                abertas.ocultando = null
                viewModel.ocultar(mensagem)
            },
            aoCancelar = { abertas.ocultando = null },
        )
    }
}
