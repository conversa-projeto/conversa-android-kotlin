package com.conversa.app.feature.chat

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.conversa.app.core.model.AtividadeConversa
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.RotuloDia
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.rotuloDia
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.componentes.IndicadorDigitando
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.feature.chamada.rememberLigar
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Quantos itens do topo faltam para pedir a página anterior. */
private const val MARGEM_PAGINA = 15

/** Longe do fim = mais de tantos itens acima do mais novo (mostra o botão "ir para o final"). */
private const val LONGE_DO_FIM = 6

@Composable
fun ChatRotaTela(
    aoVoltar: () -> Unit,
    aoMembros: (Long) -> Unit,
    aoAbrirConversa: (conversaId: Long) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val avisos = LocalAvisos.current
    val lista = rememberLazyListState()
    val escopo = rememberCoroutineScope()
    // Botões de voz e vídeo (e "ligar de novo" na bolha da chamada): pede as permissões e liga (6.4).
    val aoLigar: (TipoChamada) -> Unit = rememberLigar(estado.conversa)
    val contexto = LocalContext.current
    val recursos = LocalResources.current
    val semApp = stringResource(R.string.nenhum_app_para_abrir)
    val audioFalhou = stringResource(R.string.audio_falhou)
    val abrirRotulo = stringResource(R.string.abrir)
    var pdfAberto by remember { mutableStateOf<EventoChat.AbrirPdf?>(null) }
    pdfAberto?.let { pdf ->
        VisualizadorPdf(
            arquivo = pdf.arquivo,
            nome = pdf.conteudo.nome.ifBlank { pdf.arquivo.name },
            aoBaixar = { viewModel.baixar(pdf.conteudo) },
            aoAbrirCom = { if (!abrirComOutroApp(contexto, pdf.arquivo, "application/pdf")) escopo.launch { avisos.mostrarErro(semApp) } },
            aoFechar = { pdfAberto = null },
        )
    }
    // Android 9: "Salvar como" (sem permissão de armazenamento); o anexo pedido fica guardado até voltar.
    var salvarComo by remember { mutableStateOf<com.conversa.app.core.model.Conteudo?>(null) }
    val escolherOndeSalvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val conteudo = salvarComo
        salvarComo = null
        if (uri != null && conteudo != null) viewModel.salvarEm(uri.toString(), conteudo)
    }
    // Toque longo na bolha: menu, seletor de emoji, quem reagiu e ocultar (etapa 7).
    val acoesDaMensagem = remember { AcoesAbertas() }
    AcoesDaMensagem(acoesDaMensagem, estado, viewModel)
    val copiado = stringResource(R.string.copiado)
    val naoOcultou = stringResource(R.string.nao_foi_possivel_ocultar)
    val limiteDeReacoes = stringResource(R.string.limite_de_reacoes)
    val microfoneIndisponivel = stringResource(R.string.microfone_indisponivel)
    val gravacaoCurta = stringResource(R.string.gravacao_curta)
    val gravacaoFalhou = stringResource(R.string.gravacao_falhou)
    ColetarEventos(viewModel.eventos.fluxo) { evento ->
        when (evento) {
            EventoChat.MicrofoneIndisponivel -> avisos.mostrarErro(microfoneIndisponivel)
            EventoChat.GravacaoCurta -> avisos.showSnackbar(gravacaoCurta)
            EventoChat.GravacaoFalhou -> avisos.mostrarErro(gravacaoFalhou)
            is EventoChat.Erro -> avisos.mostrarErro(evento.mensagem)
            EventoChat.RolarAoFim -> lista.animateScrollToItem(0)
            is EventoChat.AbrirConversa -> aoAbrirConversa(evento.conversaId)
            is EventoChat.ArquivoGrande -> avisos.mostrarErro(recursos.getString(R.string.arquivo_grande, evento.nome))
            is EventoChat.AbrirArquivo -> if (!abrirComOutroApp(contexto, evento.arquivo, evento.mime)) avisos.mostrarErro(semApp)
            is EventoChat.Baixando -> avisos.showSnackbar(recursos.getString(R.string.baixando, evento.nome))
            is EventoChat.Salvo -> {
                val acao = avisos.showSnackbar(
                    recursos.getString(R.string.salvo_em_downloads, evento.nome),
                    actionLabel = abrirRotulo,
                    withDismissAction = true,
                )
                if (acao == SnackbarResult.ActionPerformed && !abrirUri(contexto, evento.uri, evento.mime)) avisos.mostrarErro(semApp)
            }
            is EventoChat.DownloadFalhou -> avisos.mostrarErro(recursos.getString(R.string.download_falhou, evento.nome))
            is EventoChat.EscolherOndeSalvar -> {
                salvarComo = evento.conteudo
                escolherOndeSalvar.launch(evento.conteudo.nome.ifBlank { evento.conteudo.conteudo })
            }
            is EventoChat.Compartilhar -> compartilharArquivo(contexto, evento.arquivo, evento.mime)
            is EventoChat.AbrirPdf -> pdfAberto = evento
            EventoChat.AudioFalhou -> avisos.mostrarErro(audioFalhou)
            is EventoChat.CopiarTexto -> if (copiar(contexto, ClipData.newPlainText(null, evento.texto))) avisos.showSnackbar(copiado)
            is EventoChat.CopiarImagem -> {
                val clipe = ClipData.newUri(contexto.contentResolver, null, uriCompartilhado(contexto, evento.arquivo))
                if (copiar(contexto, clipe)) avisos.showSnackbar(copiado)
            }
            EventoChat.OcultarFalhou -> avisos.mostrarErro(naoOcultou)
            EventoChat.LimiteDeReacoes -> avisos.mostrarErro(limiteDeReacoes)
            is EventoChat.ReagirFalhou -> avisos.mostrarErro(recursos.getString(R.string.nao_foi_possivel_reagir, evento.motivo))
        }
    }
    // O áudio vai como fluxo: só as bolhas de áudio leem (ver LocalAudio).
    val economizar by viewModel.economizarDados.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalAudio provides viewModel.audio, LocalEconomiaDados provides economizar) {
        ChatTela(
            estado = estado,
            lista = lista,
            acoes = AcoesChat(
                aoVoltar = aoVoltar,
                aoMembros = { aoMembros(viewModel.conversaId) },
                aoLigar = aoLigar,
                aoEnviar = viewModel::enviar,
                aoDigitar = viewModel::aoDigitar,
                aoCarregarAnteriores = viewModel::carregarAnteriores,
                aoVerMensagens = viewModel::marcarLidas,
                bolha = AcoesBolha(
                    aoReenviar = viewModel::reenviar,
                    aoDescartar = viewModel::descartar,
                    aoMencao = viewModel::abrirDireta,
                    aoLigar = aoLigar,
                    aoAbrirArquivo = {
                        if (ehPdf(it)) {
                            viewModel.abrirPdf(it)
                        } else {
                            viewModel.abrirArquivo(
                                it.conteudo,
                                it.nome.ifBlank {
                                    it.conteudo
                                },
                                null,
                            )
                        }
                    },
                    urlDoVideo = viewModel::urlDoVideo,
                    aoBaixar = viewModel::baixar,
                    aoCompartilhar = viewModel::compartilhar,
                    aoAlternarAudio = viewModel::alternarAudio,
                    aoTranscrever = viewModel::transcrever,
                    aoAcompanharTranscricao = viewModel::acompanharTranscricao,
                    aoBuscarAudio = viewModel::buscarAudio,
                    aoMenu = { acoesDaMensagem.menuDe = it },
                    aoReagir = viewModel::reagir,
                    aoVerReacoes = { mensagem, emoji -> acoesDaMensagem.quemReagiu = mensagem.id to emoji },
                    aoVerMaisReacoes = { acoesDaMensagem.maisReacoes = it.id },
                    aoResponder = viewModel::responder,
                ),
                aoIrAoFim = { escopo.launch { lista.animateScrollToItem(0) } },
                aoAdicionarAnexos = viewModel::adicionarAnexos,
                aoRemoverAnexo = viewModel::removerAnexo,
                pastaCamera = viewModel::pastaCamera,
                aoTextoUsado = viewModel::textoUsado,
                aoCampoFocado = viewModel::campoFocado,
                aoCancelarResposta = viewModel::cancelarResposta,
                aoColarAnexos = viewModel::colarAnexos,
                aoVisivel = viewModel::visivel,
                gravacao = AcoesGravacao(
                    estado = viewModel.gravacao,
                    aoIniciar = viewModel::iniciarGravacao,
                    aoTravar = viewModel::travarGravacao,
                    aoPausar = viewModel::pausarGravacao,
                    aoContinuar = viewModel::continuarGravacao,
                    aoOuvir = viewModel::ouvirGravacao,
                    aoEnviar = viewModel::enviarGravacao,
                    aoDescartar = viewModel::descartarGravacao,
                    aoBuscar = viewModel::buscarAudio,
                ),
            ),
        )
    }
}

/**
 * Põe na área de transferência. Devolve se a tela deve avisar "Copiado!": do Android 13
 * em diante o próprio sistema mostra a confirmação.
 */
private fun copiar(contexto: Context, clipe: ClipData): Boolean {
    contexto.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clipe) ?: return false
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}

/** PDF abre no visualizador do app (FC-411); os outros arquivos, com outro app. */
private fun ehPdf(conteudo: com.conversa.app.core.model.Conteudo): Boolean =
    conteudo.extensao.equals("pdf", ignoreCase = true) || conteudo.nome.endsWith(".pdf", ignoreCase = true)

/** Abre um arquivo salvo em Downloads (URI do MediaStore) com outro app. */
private fun abrirUri(contexto: Context, uri: String, mime: String): Boolean {
    val intencao = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri.toUri(), mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        contexto.startActivity(Intent.createChooser(intencao, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** "Compartilhar" do Android com o anexo baixado (permissão de leitura temporária pelo `FileProvider`). */
private fun compartilharArquivo(contexto: Context, arquivo: File, mime: String?) {
    val tipo = mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(arquivo.extension.lowercase()) ?: "application/octet-stream"
    val intencao = Intent(Intent.ACTION_SEND)
        .setType(tipo)
        .putExtra(Intent.EXTRA_STREAM, uriCompartilhado(contexto, arquivo))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    contexto.startActivity(Intent.createChooser(intencao, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Abre o arquivo baixado com outro app, pelo `FileProvider` (permissão só de leitura e temporária). */
private fun abrirComOutroApp(contexto: Context, arquivo: File, mime: String?): Boolean {
    val tipo = mime ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(arquivo.extension.lowercase()) ?: "application/octet-stream"
    val intencao = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriCompartilhado(contexto, arquivo), tipo)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        contexto.startActivity(Intent.createChooser(intencao, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

class AcoesChat(
    val aoVoltar: () -> Unit = {},
    val aoMembros: () -> Unit = {},
    val aoLigar: (TipoChamada) -> Unit = {},
    val aoEnviar: (String, () -> Unit) -> Unit = { _, _ -> },
    val aoDigitar: (String) -> Unit = {},
    val aoCarregarAnteriores: () -> Unit = {},
    val aoVerMensagens: (List<com.conversa.app.core.model.Mensagem>) -> Unit = {},
    val bolha: AcoesBolha = AcoesBolha(),
    val aoIrAoFim: () -> Unit = {},
    val aoAdicionarAnexos: (List<String>, com.conversa.app.core.model.TipoConteudo?) -> Unit = { _, _ -> },
    val aoRemoverAnexo: (String) -> Unit = {},
    /** Pasta (no cache do app) onde a câmera grava a foto antes de enviar. */
    val pastaCamera: () -> java.io.File? = { null },
    val gravacao: AcoesGravacao = AcoesGravacao(),
    /** O campo já usou o texto compartilhado por outro app. */
    val aoTextoUsado: () -> Unit = {},
    val aoCampoFocado: () -> Unit = {},
    val aoCancelarResposta: () -> Unit = {},
    /** Imagem colada no campo (teclado ou área de transferência). */
    val aoColarAnexos: (List<String>) -> Unit = {},
    /** A conversa ficou visível ou deixou de estar (notificações, NOT-01/03). */
    val aoVisivel: (Boolean) -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTela(estado: ChatUiState, lista: androidx.compose.foundation.lazy.LazyListState, acoes: AcoesChat) {
    // A lista é desenhada de baixo para cima (reverseLayout): o item 0 é a mensagem mais nova.
    val itens = remember(estado.itens) { estado.itens.asReversed() }
    val dono = LocalLifecycleOwner.current

    // Pede a página anterior perto do topo.
    LaunchedEffect(lista, itens.size) {
        snapshotFlow { lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { it >= itens.size - MARGEM_PAGINA }
            .collect { acoes.aoCarregarAnteriores() }
    }
    // App em segundo plano com a gravação aberta: pausa (o Android corta o microfone de app em segundo plano).
    val pausarGravacao by rememberUpdatedState(acoes.gravacao.aoPausar)
    val visivel by rememberUpdatedState(acoes.aoVisivel)
    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> visivel(true)
                Lifecycle.Event.ON_PAUSE -> visivel(false)
                Lifecycle.Event.ON_STOP -> pausarGravacao()
                else -> Unit
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose {
            dono.lifecycle.removeObserver(observador)
            visivel(false)
        }
    }
    // Marca como lidas as mensagens que aparecem na tela, só com o app visível (MSG-04).
    LaunchedEffect(lista, itens) {
        dono.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshotFlow { lista.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { indices ->
                    acoes.aoVerMensagens(indices.mapNotNull { (itens.getOrNull(it) as? ItemChat.Bolha)?.mensagem })
                }
        }
    }

    // Ao abrir: primeira não lida no topo da tela; sem não lidas, o fim (MSG-05).
    var posicionou by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(itens, estado.pronto) {
        // Espera a decisão da "Últimas": o Room pode já ter mensagens antes disso (sincronização).
        if (posicionou || !estado.pronto || itens.isEmpty()) return@LaunchedEffect
        val linha = itens.indexOfFirst { it is ItemChat.NaoLidas }
        if (linha >= 0) lista.scrollToItem(linha, scrollOffset = -40) else lista.scrollToItem(0)
        posicionou = true
    }

    val longeDoFim by remember { derivedStateOf { lista.firstVisibleItemIndex > LONGE_DO_FIM } }
    // Chegou mensagem nova. Perto do fim: acompanha (o LazyColumn mantém na tela o item que
    // estava visível, pela chave, e a nova ficaria escondida embaixo). Longe do fim e de outra
    // pessoa: "Há novas mensagens".
    var haNovas by remember { mutableStateOf(false) }
    val maisNova = (itens.firstOrNull() as? ItemChat.Bolha)?.mensagem
    LaunchedEffect(maisNova?.id) {
        if (maisNova == null || !posicionou) return@LaunchedEffect
        when {
            !longeDoFim -> lista.animateScrollToItem(0)
            maisNova.remetenteId != estado.eu -> haNovas = true
        }
    }
    LaunchedEffect(longeDoFim) { if (!longeDoFim) haNovas = false }

    // Visualizador de imagens (ANX-05): guarda qual imagem foi tocada (mensagem + identificador).
    var abertaMensagem by rememberSaveable { mutableStateOf<Long?>(null) }
    var abertaConteudo by rememberSaveable { mutableStateOf<String?>(null) }
    val acoesBolha = remember(acoes.bolha) {
        acoes.bolha.copy(aoAbrirImagem = { mensagem, conteudo ->
            abertaMensagem = mensagem.id
            abertaConteudo = conteudo.conteudo
        })
    }
    if (abertaMensagem != null) {
        val imagens = remember(estado.itens) { imagensDaConversa(estado.itens) }
        val inicial = imagens.indexOfFirst { it.mensagem.id == abertaMensagem && it.conteudo.conteudo == abertaConteudo }
        val fechar = {
            abertaMensagem = null
            abertaConteudo = null
        }
        // A mensagem pode ter sumido (apagada, ocultada) com o visualizador aberto.
        if (inicial < 0) LaunchedEffect(Unit) { fechar() } else VisualizadorImagens(imagens, inicial, acoesBolha, fechar)
    }

    Scaffold(
        topBar = { Cabecalho(estado, acoes) },
        bottomBar = { Campo(estado.fila, estado.textoParaCampo, acoes, focar = estado.focarCampo, respondendo = estado.respondendo) },
    ) { margens ->
        Box(Modifier.fillMaxSize().padding(margens).background(MaterialTheme.colorScheme.background)) {
            when {
                estado.carregando -> Carregando()
                itens.isEmpty() -> EstadoVazio(
                    titulo = stringResource(R.string.chat_vazio),
                    descricao = stringResource(R.string.chat_vazio_descricao),
                )
                else -> LazyColumn(
                    state = lista,
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(itens, key = { it.chave }, contentType = { it::class }) { item ->
                        when (item) {
                            is ItemChat.Dia -> SeparadorDia(item)
                            ItemChat.NaoLidas -> SeparadorNaoLidas()
                            is ItemChat.Bolha -> LinhaMensagem(
                                mensagem = item.mensagem,
                                propria = item.mensagem.remetenteId == estado.eu,
                                mostrarRemetente = item.mostrarRemetente,
                                acoes = acoesBolha,
                                progresso = estado.progresso[item.mensagem.id],
                            )
                        }
                    }
                    if (estado.carregandoAnteriores) {
                        item(key = "carregando-anteriores") {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.carregando_anteriores),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = haNovas, enter = fadeIn(), exit = fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp).clickable {
                            haNovas = false
                            acoes.aoIrAoFim()
                        },
                    ) {
                        Text(
                            stringResource(R.string.ha_novas_mensagens),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                AnimatedVisibility(visible = longeDoFim, enter = fadeIn(), exit = fadeOut()) {
                    SmallFloatingActionButton(onClick = acoes.aoIrAoFim) {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.ir_para_o_final))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cabecalho(estado: ChatUiState, acoes: AcoesChat) {
    val conversa = estado.conversa
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = acoes.aoVoltar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.voltar))
            }
        },
        title = {
            // Grupo: avatar e nome abrem os dados do grupo, como o painel do web (eaa8bac).
            Row(
                modifier = Modifier.clickable(
                    enabled = estado.grupo,
                    onClickLabel = stringResource(R.string.membros_do_grupo),
                    onClick = acoes.aoMembros,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(conversa?.titulo, conversa?.avatarUrl, tamanho = 38.dp, online = estado.online)
                Column {
                    Text(
                        conversa?.titulo.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val atividade = textoAtividade(estado.atividade)
                    // "Gravando áudio…" em vermelho (como o web e o FMX); digitando na cor primária.
                    val corAtividade = if (gravandoAudio(
                            estado.atividade,
                        )
                    ) {
                        ConversaTema.cores.gravandoAudio
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    when {
                        atividade != null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            IndicadorDigitando(cor = corAtividade)
                            Text(
                                atividade,
                                style = MaterialTheme.typography.labelSmall,
                                color = corAtividade,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        estado.online -> Subtitulo(stringResource(R.string.online))
                        estado.grupo && estado.membros.isNotEmpty() -> Subtitulo(estado.membros)
                    }
                }
            }
        },
        actions = {
            IconButton(onClick = { acoes.aoLigar(TipoChamada.AUDIO) }) {
                Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.chamada_de_voz))
            }
            IconButton(onClick = { acoes.aoLigar(TipoChamada.VIDEO) }) {
                Icon(Icons.Outlined.Videocam, contentDescription = stringResource(R.string.chamada_de_video))
            }
            if (estado.grupo) {
                IconButton(onClick = acoes.aoMembros) {
                    Icon(Icons.Outlined.Group, contentDescription = stringResource(R.string.membros_do_grupo))
                }
            }
        },
    )
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun gravandoAudio(atividade: AtividadeConversa): Boolean = when (atividade) {
    AtividadeConversa.Nenhuma -> false
    is AtividadeConversa.Direta -> atividade.gravando
    is AtividadeConversa.Grupo -> atividade.gravando
}

/** "Digitando…", "Ana está digitando…", "Ana e Beto estão…", "… e outras N pessoas…" (textos do web). */
@Composable
private fun textoAtividade(atividade: AtividadeConversa): String? = when (atividade) {
    AtividadeConversa.Nenhuma -> null
    is AtividadeConversa.Direta -> stringResource(if (atividade.gravando) R.string.gravando else R.string.digitando)
    is AtividadeConversa.Grupo -> {
        val n = atividade.nomes
        val acao1 = stringResource(if (atividade.gravando) R.string.esta_gravando else R.string.esta_digitando)
        val acaoN = stringResource(if (atividade.gravando) R.string.estao_gravando else R.string.estao_digitando)
        when {
            atividade.outros > 0 -> pluralStringResource(
                R.plurals.e_outras_pessoas,
                atividade.outros,
                n[0],
                n[1],
                n[2],
                atividade.outros,
                acaoN,
            )
            n.size == 3 -> stringResource(R.string.tres_nomes, n[0], n[1], n[2], acaoN)
            n.size == 2 -> stringResource(R.string.dois_nomes, n[0], n[1], acaoN)
            else -> stringResource(R.string.um_nome, n[0], acao1)
        }
    }
}

private val FORMATO_DIA = DateTimeFormatter.ofPattern("EEE, dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

@Composable
private fun SeparadorDia(item: ItemChat.Dia) {
    val texto = when (val rotulo = rotuloDia(item.data, Instant.now(), ZoneId.systemDefault())) {
        RotuloDia.Hoje -> stringResource(R.string.hoje)
        RotuloDia.Ontem -> stringResource(R.string.ontem)
        is RotuloDia.Data -> FORMATO_DIA.format(rotulo.data)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .background(ConversaTema.cores.separadorData, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

/** Linha "Últimas" antes da primeira não lida (MSG-03). */
@Composable
private fun SeparadorNaoLidas() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = ConversaTema.cores.separadorNaoLidas)
        Text(
            stringResource(R.string.ultimas),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = ConversaTema.cores.separadorNaoLidas)
    }
}
