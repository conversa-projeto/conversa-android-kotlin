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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.resumoDaMensagem
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.tema.ConversaTema
import java.io.File
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
    respondendo: Mensagem? = null,
) {
    // Estado do texto local e síncrono (o cursor não pula); sobrevive a girar a tela.
    val texto = rememberTextFieldState()
    val foco = remember { FocusRequester() }
    // Pedido de foco (chat da chamada recém-criado): uma vez; sem o campo na tela (gravando), só descarta.
    LaunchedEffect(focar) {
        if (focar) {
            runCatching { foco.requestFocus() }
            acoes.aoCampoFocado()
        }
    }
    // Texto que outro app compartilhou (AND-10): entra no campo uma vez, para a pessoa revisar.
    LaunchedEffect(textoCompartilhado) {
        if (textoCompartilhado != null) {
            texto.setTextAndPlaceCursorAtEnd(textoCompartilhado)
            acoes.aoTextoUsado()
        }
    }
    // "Digitando" (ENV-15): cada mudança do texto (o ViewModel limita a um aviso a cada 2,5 s).
    LaunchedEffect(texto) {
        snapshotFlow { texto.text.toString() }.drop(1).collect { acoes.aoDigitar(it) }
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
        if (fila.isNotEmpty() && atual == EstadoGravacao.Parada) FilaAnexos(fila, acoes.aoRemoverAnexo)
        if (comBarra) {
            BarraGravacao(atual, acoes.gravacao)
        } else {
            LinhaDoCampo(texto, fila.isNotEmpty(), atual as? EstadoGravacao.Gravando, acoes, foco)
        }
    }
}

/** Respondendo (7.3): quem escreveu e o resumo, com borda azul à esquerda e o "×" (como o web). */
@Composable
private fun BarraResposta(mensagem: Mensagem, aoCancelar: () -> Unit) {
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
            Text(mensagem.remetente, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
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
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (segurando == null) BotaoAnexar(acoes) else Spacer(Modifier.width(8.dp))
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
                    modifier = Modifier.fillMaxWidth().focusRequester(foco).contentReceiver(receptorDeImagens(acoes.aoColarAnexos)),
                )
            }
        }
        // Com texto ou anexo: Enviar. Vazio: microfone (como o web).
        if (texto.text.isNotBlank() || temAnexos) {
            FilledIconButton(
                onClick = {
                    val enviado = texto.text.toString()
                    // Só limpa se a pessoa não mudou o texto enquanto a mensagem era gravada no Room.
                    acoes.aoEnviar(enviado) { if (texto.text.toString() == enviado) texto.clearText() }
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.enviar))
            }
        } else {
            BotaoMicrofone(segurando != null, acoes.gravacao)
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
private fun BotaoAnexar(acoes: AcoesChat) {
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val semCamera = stringResource(R.string.sem_app_de_camera)
    var menu by remember { mutableStateOf(false) }
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
