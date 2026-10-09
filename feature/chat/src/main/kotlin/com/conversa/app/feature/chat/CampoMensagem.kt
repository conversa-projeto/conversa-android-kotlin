package com.conversa.app.feature.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Poll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.mensagens.ReferenciaPendente
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.MencaoInserida
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.dividirMencoes
import com.conversa.app.core.model.extrairMencoesCruas
import com.conversa.app.core.model.mencaoDigitada
import com.conversa.app.core.model.resumoDaMensagem
import com.conversa.app.core.model.substituirAtalhoNoFim
import com.conversa.app.core.model.sugestoesDeMencao
import com.conversa.app.core.model.textoParaEnvio
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.tema.ConversaTema
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** O que o microfone e a barra de gravação pedem (ANX-11). [estado] é lido só pelo campo. */
data class AcoesGravacao(
    val estado: StateFlow<EstadoGravacao> = MutableStateFlow(EstadoGravacao.Parada),
    val aoIniciar: () -> Unit = {},
    val aoTravar: () -> Unit = {},
    val aoPausar: () -> Unit = {},
    val aoContinuar: () -> Unit = {},
    val aoOuvir: () -> Unit = {},
    val aoEnviar: () -> Unit = {},
    val aoDescartar: () -> Unit = {},
    /** Pular no "ouvir" (chave no player, fração 0..1). */
    val aoBuscar: (String, Float) -> Unit = { _, _ -> },
)

/** Segurar o microfone pelo menos isto e soltar = enviar (igual ao web, `HOLD_THRESHOLD_MS`). */
private const val SEGURAR_MS = 300L

/**
 * Campo de mensagem (ENV-01) com o botão de anexo e a fila (ANX-01, ANX-03) e o microfone
 * (ANX-11, quando não há texto nem anexo). O texto fica em estado local (síncrono) e só é
 * limpo depois que a mensagem foi gravada no Room: nunca se perde.
 */
@Composable
internal fun Campo(
    fila: List<AnexoLocal>,
    textoCompartilhado: String?,
    acoes: AcoesChat,
    focar: Boolean = false,
    respondendo: ReferenciaPendente? = null,
    agendadas: List<Mensagem> = emptyList(),
    grupo: Boolean = false,
) {
    // Estado do texto local e síncrono (o cursor não pula); sobrevive a girar a tela.
    val texto = rememberTextFieldState()
    val foco = remember { FocusRequester() }
    // Menções inseridas pela lista (7.7): o campo mostra "@Nome"; no envio vira "@[Nome](id)".
    val mencoes = remember { mutableStateListOf<MencaoInserida>() }
    // "Inserir código" (7.11): pelo "+" (vazio) ou por um texto longo colado (preenchido).
    // "Nova votação" (7.12): só em grupo, pelo "+".
    var criandoVotacao by rememberSaveable { mutableStateOf(false) }
    if (criandoVotacao) NovaVotacao(acoes.aoCriarVotacao, aoFechar = { criandoVotacao = false })
    var vendoAgendadas by rememberSaveable { mutableStateOf(false) }
    if (vendoAgendadas) MensagensAgendadas(agendadas, acoes.aoCancelarAgendada, aoFechar = { vendoAgendadas = false })
    var codigoAberto by rememberSaveable { mutableStateOf(false) }
    var codigoColado by rememberSaveable { mutableStateOf<String?>(null) }
    val transformacao = remember {
        transformacaoDoCampo { colado ->
            codigoColado = colado
            codigoAberto = true
        }
    }
    if (codigoAberto) {
        InserirCodigo(
            codigoInicial = codigoColado.orEmpty(),
            aoEnviar = { bloco ->
                codigoAberto = false
                codigoColado = null
                acoes.aoEnviar(bloco) {}
            },
            aoCancelar = {
                codigoAberto = false
                // Cancelar cola o texto como estava: a janela era só uma sugestão.
                codigoColado?.let { colado ->
                    texto.edit {
                        val inicio = selection.min
                        replace(inicio, selection.max, colado)
                        selection = TextRange(inicio + colado.length)
                    }
                }
                codigoColado = null
            },
        )
    }
    // Pedido de foco (chat da chamada recém-criado): uma vez; sem o campo na tela (gravando), só descarta.
    LaunchedEffect(focar) {
        if (focar) {
            runCatching { foco.requestFocus() }
            acoes.aoCampoFocado()
        }
    }
    // Texto do rascunho (FC-519) ou que outro app compartilhou (AND-10): entra no campo uma vez,
    // para a pessoa revisar, e não conta como "digitando".
    var textoDoApp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(textoCompartilhado) {
        if (textoCompartilhado != null) {
            // Texto com "@[Nome](id)" volta a mostrar "@Nome", com as menções guardadas.
            val (limpo, cruas) = extrairMencoesCruas(textoCompartilhado)
            textoDoApp = limpo
            texto.setTextAndPlaceCursorAtEnd(limpo)
            mencoes.addAll(cruas)
            acoes.aoTextoUsado()
        }
    }
    // "Digitando" (ENV-15): cada mudança do texto (o ViewModel limita a um aviso a cada 2,5 s).
    LaunchedEffect(texto) {
        snapshotFlow { texto.text.toString() }.drop(1).collect { if (it != textoDoApp) acoes.aoDigitar(it) }
    }
    // Rascunho (FC-519): o texto com as menções cruas, a cada mudança.
    LaunchedEffect(texto) {
        snapshotFlow { textoParaEnvio(texto.text.toString(), mencoes.toList()) }.drop(1).collect { acoes.aoMudarRascunho(it) }
    }
    val gravacao by acoes.gravacao.estado.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        val atual = gravacao
        val comBarra = atual is EstadoGravacao.Pausada || (atual as? EstadoGravacao.Gravando)?.travada == true
        if (respondendo != null) BarraResposta(respondendo, acoes.aoCancelarResposta)
        if (atual == EstadoGravacao.Parada) SugestoesDeMencao(texto, mencoes, acoes.contatosMencao)
        if (fila.isNotEmpty() && atual == EstadoGravacao.Parada) FilaAnexos(fila, acoes.aoRemoverAnexo)
        if (comBarra) {
            BarraGravacao(atual, acoes.gravacao)
        } else {
            // A encaminhada pendente pode ir sem texto (os conteúdos dela vão junto): o Enviar aparece.
            val encaminhando = respondendo?.tipo == TipoReferencia.ENCAMINHAMENTO
            LinhaDoCampo(
                texto,
                fila.isNotEmpty() || encaminhando,
                atual as? EstadoGravacao.Gravando,
                acoes,
                foco,
                mencoes,
                transformacao,
                aoInserirCodigo = { codigoAberto = true },
                agendadas = agendadas.size,
                aoVerAgendadas = { vendoAgendadas = true },
                aoNovaVotacao = if (grupo) ({ criandoVotacao = true }) else null,
            )
        }
    }
}

/**
 * Respondendo (7.3) ou encaminhando ("Responder no privado", 7.6): quem escreveu e o resumo,
 * com borda azul à esquerda e o "×" (como o web).
 */
@Composable
private fun BarraResposta(referencia: ReferenciaPendente, aoCancelar: () -> Unit) {
    val mensagem = referencia.mensagem
    val titulo = if (referencia.tipo ==
        TipoReferencia.ENCAMINHAMENTO
    ) {
        stringResource(R.string.encaminhando_de, mensagem.remetente)
    } else {
        mensagem.remetente
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp)
            .background(ConversaTema.cores.campoEntrada, RoundedCornerShape(8.dp))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(
                textoDoResumo(resumoDaMensagem(mensagem)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = aoCancelar) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancelar_resposta))
        }
    }
}

/**
 * Lista de menção (7.7, `MencaoDropdown.vue`): com "@termo" antes do cursor, até 6 contatos
 * pelo nome ou login. Escolher troca o "@termo" por "@Nome " e guarda o id.
 */
@Composable
private fun SugestoesDeMencao(texto: TextFieldState, mencoes: SnapshotStateList<MencaoInserida>, contatos: StateFlow<List<Contato>>) {
    val selecao = texto.selection
    val digitada = if (selecao.collapsed) mencaoDigitada(texto.text.toString(), selecao.end) else null
    if (digitada == null) return
    val todos by contatos.collectAsStateWithLifecycle()
    val sugestoes = remember(todos, digitada.termo) { sugestoesDeMencao(todos, digitada.termo) }
    if (sugestoes.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp).widthIn(max = 280.dp),
    ) {
        Column {
            for (contato in sugestoes) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nome = contato.nome
                            texto.edit {
                                replace(digitada.inicio, selecao.end, "@$nome ")
                                selection = TextRange(digitada.inicio + nome.length + 2)
                            }
                            mencoes += MencaoInserida(nome, contato.id)
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Avatar(contato.nome, contato.avatarUrl, tamanho = 28.dp)
                    Text(contato.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** As menções inseridas aparecem destacadas no campo (só a aparência: o texto continua "@Nome"). */
private fun destaqueDasMencoes(mencoes: List<MencaoInserida>, cor: Color) = OutputTransformation {
    if (mencoes.isEmpty()) return@OutputTransformation
    var posicao = 0
    for (trecho in dividirMencoes(asCharSequence().toString(), mencoes)) {
        if (trecho.mencao !=
            null
        ) {
            addStyle(SpanStyle(color = cor, fontWeight = FontWeight.SemiBold), posicao, posicao + trecho.texto.length)
        }
        posicao += trecho.texto.length
    }
}

/**
 * Linha do campo. Segurando o microfone ([segurando]), o texto dá lugar ao tempo e ao
 * "deslize para cancelar"; o botão do microfone continua no mesmo lugar (o gesto não se perde).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LinhaDoCampo(
    texto: TextFieldState,
    temAnexos: Boolean,
    segurando: EstadoGravacao.Gravando?,
    acoes: AcoesChat,
    foco: FocusRequester,
    mencoes: SnapshotStateList<MencaoInserida>,
    transformacao: InputTransformation,
    aoInserirCodigo: () -> Unit,
    agendadas: Int,
    aoNovaVotacao: (() -> Unit)?,
    aoVerAgendadas: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (segurando == null) BotaoAnexar(acoes, aoInserirCodigo, aoNovaVotacao) else Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            if (segurando != null) {
                IndicadorSegurando(segurando)
            } else {
                TextField(
                    state = texto,
                    placeholder = { Text(stringResource(R.string.digite_uma_mensagem)) },
                    lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ConversaTema.cores.campoEntrada,
                        unfocusedContainerColor = ConversaTema.cores.campoEntrada,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    outputTransformation = destaqueDasMencoes(mencoes, MaterialTheme.colorScheme.primary),
                    inputTransformation = transformacao,
                    modifier = Modifier.fillMaxWidth().focusRequester(foco).contentReceiver(receptorDeImagens(acoes.aoColarAnexos)),
                )
            }
        }
        var menuAgendar by remember { mutableStateOf(false) }
        var agendando by rememberSaveable { mutableStateOf(false) }
        // Agora (nulo) ou agendada (7.10). Só limpa se a pessoa não mudou o texto enquanto a mensagem era gravada no Room.
        val enviar: (Instant?) -> Unit = { quando ->
            val enviado = texto.text.toString()
            val aoGravar = {
                if (texto.text.toString() == enviado) {
                    texto.clearText()
                    mencoes.clear()
                }
            }
            val corpo = textoParaEnvio(substituirAtalhoNoFim(enviado.trimEnd()), mencoes)
            if (quando == null) acoes.aoEnviar(corpo, aoGravar) else acoes.aoAgendar(corpo, quando, aoGravar)
        }
        // Com texto ou anexo: Enviar (toque longo: "Agendar mensagem"). Vazio: microfone (como o web).
        if (texto.text.isNotBlank() || temAnexos) {
            Box {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.enviar),
                            onLongClickLabel = stringResource(R.string.agendar_mensagem),
                            onLongClick = { menuAgendar = true },
                            onClick = { enviar(null) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.enviar),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                DropdownMenu(expanded = menuAgendar, onDismissRequest = { menuAgendar = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.agendar_mensagem)) },
                        leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                        onClick = {
                            menuAgendar = false
                            agendando = true
                        },
                    )
                }
            }
        } else {
            // Campo vazio com agendadas: o relógio abre a lista (🆕 web `7322e83`).
            if (agendadas > 0 && segurando == null) RelogioAgendadas(agendadas, aoVerAgendadas)
            BotaoMicrofone(segurando != null, acoes.gravacao)
        }
        if (agendando) {
            AgendarMensagem(
                aoConfirmar = { quando ->
                    agendando = false
                    enviar(quando)
                },
                aoFechar = { agendando = false },
            )
        }
    }
}

/**
 * Colar imagem (FC-412): imagem do teclado (figurinhas, GIFs) ou da área de transferência
 * entra na fila de anexos; o resto (texto) segue para o campo.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun receptorDeImagens(aoColar: (List<String>) -> Unit) = ReceiveContentListener { recebido ->
    if (!recebido.hasMediaType(MediaType.Image)) return@ReceiveContentListener recebido
    val uris = mutableListOf<String>()
    val resto = recebido.consume { item ->
        val uri = item.uri ?: return@consume false
        uris += uri.toString()
        true
    }
    if (uris.isNotEmpty()) aoColar(uris)
    resto
}

/**
 * Microfone (ANX-11): apertar já grava (pedindo a permissão na hora, #32 do legado).
 * - segurou ≥ 300 ms e soltou: envia;
 * - toque curto ou arrastou para cima: fica gravando com a barra (travado);
 * - arrastou para o lado: descarta.
 * Leitor de tela: um toque começa já travado.
 */
@Composable
private fun BotaoMicrofone(segurando: Boolean, acoes: AcoesGravacao) {
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val semPermissao = stringResource(R.string.microfone_sem_permissao)
    val descricao = stringResource(R.string.gravar_audio)
    val atuais by rememberUpdatedState(acoes)
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        if (!concedida) escopo.launch { avisos.mostrarErro(semPermissao) }
    }
    val temPermissao = {
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val limiteArrasto = with(LocalDensity.current) { 72.dp.toPx() }
    Box(
        Modifier
            .size(if (segurando) 56.dp else 48.dp)
            .clip(CircleShape)
            .background(if (segurando) ConversaTema.cores.gravandoAudio else MaterialTheme.colorScheme.primary)
            .semantics {
                contentDescription = descricao
                role = Role.Button
                onClick {
                    if (temPermissao()) {
                        atuais.aoIniciar()
                        atuais.aoTravar()
                    } else {
                        pedirPermissao.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val toque = awaitFirstDown()
                    toque.consume()
                    if (!temPermissao()) {
                        pedirPermissao.launch(Manifest.permission.RECORD_AUDIO)
                        return@awaitEachGesture
                    }
                    atuais.aoIniciar()
                    var decidido = false
                    while (true) {
                        val dedo = awaitPointerEvent().changes.firstOrNull { it.id == toque.id } ?: break
                        if (!dedo.pressed) {
                            if (!decidido) {
                                if (dedo.uptimeMillis - toque.uptimeMillis >= SEGURAR_MS) atuais.aoEnviar() else atuais.aoTravar()
                            }
                            dedo.consume()
                            return@awaitEachGesture
                        }
                        if (!decidido) {
                            val deslocamento = dedo.position - toque.position
                            when {
                                deslocamento.x < -limiteArrasto -> {
                                    atuais.aoDescartar()
                                    decidido = true
                                }
                                deslocamento.y < -limiteArrasto -> {
                                    atuais.aoTravar()
                                    decidido = true
                                }
                            }
                        }
                        dedo.consume()
                    }
                    // Gesto cancelado pelo sistema: não perde o que gravou, fica travado.
                    if (!decidido) atuais.aoTravar()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Segurando o microfone: ponto vermelho, tempo e a dica de cancelar. */
@Composable
private fun IndicadorSegurando(estado: EstadoGravacao.Gravando) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PontoGravando()
        Text(tempoGravacao(estado.duracaoMs), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.deslize_para_cancelar),
            style = MaterialTheme.typography.labelMedium,
            color = ConversaTema.cores.textoTerciario,
        )
    }
}

/**
 * Barra de gravação travada (ANX-11, textos do web): Descartar, ponto vermelho + tempo +
 * nível do microfone, Pausar/Continuar, e, pausada, Ouvir com a barra de progresso; Enviar.
 */
@Composable
private fun BarraGravacao(estado: EstadoGravacao, acoes: AcoesGravacao) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = acoes.aoDescartar) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.descartar), tint = ConversaTema.cores.gravandoAudio)
        }
        Row(
            Modifier.weight(1f).height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (estado) {
                is EstadoGravacao.Gravando -> {
                    PontoGravando()
                    Text(tempoGravacao(estado.duracaoMs), style = MaterialTheme.typography.bodyMedium)
                    NiveisMicrofone(estado.niveis, Modifier.weight(1f).height(24.dp))
                }
                is EstadoGravacao.Pausada -> OuvirGravacao(estado, acoes, Modifier.weight(1f))
                EstadoGravacao.Parada -> Unit
            }
        }
        if (estado is EstadoGravacao.Gravando) {
            IconButton(onClick = acoes.aoPausar) {
                Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.pausar_gravacao))
            }
        } else {
            IconButton(onClick = acoes.aoContinuar) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.continuar_gravacao),
                    tint = ConversaTema.cores.gravandoAudio,
                )
            }
        }
        FilledIconButton(onClick = acoes.aoEnviar, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.enviar_audio))
        }
    }
}

/** Pausada: ouvir o que já foi gravado, com a barra de progresso (tocar/arrastar pula). */
@Composable
private fun OuvirGravacao(estado: EstadoGravacao.Pausada, acoes: AcoesGravacao, modifier: Modifier = Modifier) {
    val audio by LocalAudio.current.collectAsStateWithLifecycle()
    val tocandoEsta = audio.chave == estado.chaveOuvir
    val tocando = tocandoEsta && audio.tocando
    val duracao = audio.duracoes[estado.chaveOuvir]?.takeIf { it > 0 } ?: estado.duracaoMs
    val posicao = if (tocandoEsta) audio.posicaoMs else 0
    var arrastando by remember { mutableStateOf<Float?>(null) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        IconButton(onClick = acoes.aoOuvir) {
            if (tocando) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.parar_preview))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.ouvir_gravacao))
            }
        }
        BarraAudio(
            fracao = arrastando ?: (posicao.toFloat() / duracao).coerceIn(0f, 1f),
            habilitada = tocandoEsta,
            corTrilha = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            aoArrastar = { arrastando = it },
            aoSoltar = {
                arrastando?.let { acoes.aoBuscar(estado.chaveOuvir, it) }
                arrastando = null
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            tempoGravacao(if (tocando || posicao > 0) posicao else estado.duracaoMs),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

/** Ponto vermelho piscando enquanto grava. */
@Composable
private fun PontoGravando() {
    val transicao = rememberInfiniteTransition(label = "ponto")
    val opacidade by transicao.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "opacidade",
    )
    Box(Modifier.size(10.dp).alpha(opacidade).background(ConversaTema.cores.gravandoAudio, CircleShape))
}

/** Barrinhas com os últimos níveis do microfone (a mais nova à direita). */
@Composable
private fun NiveisMicrofone(niveis: List<Float>, modifier: Modifier = Modifier) {
    val cor = ConversaTema.cores.gravandoAudio
    Canvas(modifier) {
        val largura = 3.dp.toPx()
        val espaco = 2.dp.toPx()
        val quantas = ((size.width + espaco) / (largura + espaco)).toInt().coerceAtLeast(1)
        niveis.takeLast(quantas).reversed().forEachIndexed { i, nivel ->
            val altura = (size.height * (0.15f + 0.85f * nivel)).coerceAtMost(size.height)
            val x = size.width - (i + 1) * (largura + espaco) + espaco
            drawRoundRect(cor, Offset(x, (size.height - altura) / 2), Size(largura, altura), CornerRadius(largura / 2))
        }
    }
}

/** `m:ss`, como o web mostra o tempo da gravação. */
private fun tempoGravacao(ms: Long): String {
    val segundos = ms / 1000
    return "%d:%02d".format(segundos / 60, segundos % 60)
}

/**
 * Anexar (ANX-01): galeria (seletor de fotos do sistema, sem permissão), câmera e documento.
 * O acesso ao arquivo é mantido (`takePersistableUriPermission`) porque o envio pode
 * acontecer depois, pelo WorkManager, com o app já fechado.
 */
@Composable
private fun BotaoAnexar(acoes: AcoesChat, aoInserirCodigo: () -> Unit, aoNovaVotacao: (() -> Unit)?) {
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val semCamera = stringResource(R.string.sem_app_de_camera)
    var menu by remember { mutableStateOf(false) }
    var figurinhas by remember { mutableStateOf(false) }
    if (figurinhas) {
        SeletorDeFigurinhas(
            aoEscolher = {
                figurinhas = false
                acoes.aoEnviarFigurinha(it)
            },
            aoFechar = { figurinhas = false },
        )
    }
    // Arquivo da foto: sobrevive à recriação da Activity enquanto a câmera está aberta.
    var fotoPendente by rememberSaveable { mutableStateOf<String?>(null) }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        acoes.aoAdicionarAnexos(manterAcesso(contexto, uris), null)
    }
    val documentos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        acoes.aoAdicionarAnexos(manterAcesso(contexto, uris), null)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { tirou ->
        val arquivo = fotoPendente?.let(::File)
        fotoPendente = null
        if (arquivo == null) return@rememberLauncherForActivityResult
        if (tirou && arquivo.length() > 0) {
            acoes.aoAdicionarAnexos(listOf(uriCompartilhado(contexto, arquivo).toString()), TipoConteudo.IMAGEM)
        } else {
            arquivo.delete()
        }
    }

    Box {
        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.anexar))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.anexar_galeria)) },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                onClick = {
                    menu = false
                    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.anexar_camera)) },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                onClick = {
                    menu = false
                    val pasta = acoes.pastaCamera() ?: return@DropdownMenuItem
                    val arquivo = File(pasta, "foto-${System.currentTimeMillis()}.jpg")
                    fotoPendente = arquivo.path
                    try {
                        camera.launch(uriCompartilhado(contexto, arquivo))
                    } catch (_: ActivityNotFoundException) {
                        fotoPendente = null
                        escopo.launch { avisos.mostrarErro(semCamera) }
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.anexar_documento)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null) },
                onClick = {
                    menu = false
                    documentos.launch(arrayOf("*/*"))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.figurinha)) },
                leadingIcon = { Icon(Icons.Outlined.EmojiEmotions, contentDescription = null) },
                onClick = {
                    menu = false
                    figurinhas = true
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.codigo)) },
                leadingIcon = { Icon(Icons.Outlined.Code, contentDescription = null) },
                onClick = {
                    menu = false
                    aoInserirCodigo()
                },
            )
            // Votação só em grupo (o servidor recusa na direta), como o web.
            aoNovaVotacao?.let { abrir ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.votacao)) },
                    leadingIcon = { Icon(Icons.Outlined.Poll, contentDescription = null) },
                    onClick = {
                        menu = false
                        abrir()
                    },
                )
            }
        }
    }
}

/** URI do `FileProvider` para um arquivo do cache do app (foto da câmera, anexo baixado). */
internal fun uriCompartilhado(contexto: Context, arquivo: File): Uri =
    FileProvider.getUriForFile(contexto, contexto.packageName + ".arquivos", arquivo)

/** Guarda a permissão de leitura de cada URI escolhido. Alguns provedores não oferecem; aí vale a temporária. */
private fun manterAcesso(contexto: Context, uris: List<Uri>): List<String> = uris.map { uri ->
    try {
        contexto.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Sem permissão persistente: o envio ainda funciona enquanto o app estiver aberto.
    }
    uri.toString()
}
