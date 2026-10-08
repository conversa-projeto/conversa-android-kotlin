package com.conversa.app.feature.chamada

import android.Manifest
import android.graphics.Rect
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.telecom.CallEndpointCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.StatusParticipante
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.core.webrtc.MidiaLocal
import com.conversa.app.core.webrtc.MidiaWebRtc
import com.conversa.app.core.webrtc.TrilhaRemota
import com.conversa.app.core.webrtc.TrilhasChamada
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@HiltViewModel
class ChamadaViewModel @Inject constructor(
    private val gerenciador: GerenciadorChamadas,
    private val midia: MidiaWebRtc,
    private val telecom: TelecomChamadas,
    sessao: SessaoRepositorio,
    contatos: ContatosRepositorio,
    private val envio: EnvioMensagens,
) : ViewModel() {
    val contatos = contatos.observarOutros()
    val estado = gerenciador.estado
    val trilhas = midia.trilhas
    val emEspera = telecom.emEspera
    val rotas = telecom.rotas
    val rotaAtual = telecom.rotaAtual
    val eu: Long = sessao.sessao.value?.usuarioId ?: 0
    val egl: EglBase.Context get() = midia.contextoEgl

    fun atender(soAssistir: Boolean) = gerenciador.atender(soAssistir)

    fun recusar() = gerenciador.recusar()

    fun desligar() = gerenciador.desligar()

    fun alternarMicrofone() = gerenciador.alternarMicrofone()

    fun alternarCamera() = gerenciador.alternarCamera()

    fun trocarCamera() = midia.trocarCamera()

    fun ligarVideo() = gerenciador.ligarVideo()

    fun responderVideo(transmitir: Boolean) = gerenciador.responderVideo(transmitir)

    fun retomar() = telecom.retomar()

    fun mudarRota(rota: CallEndpointCompat) = telecom.mudarRota(rota)

    fun exibir(modo: ModoExibicao, destaque: Long? = null) = gerenciador.exibir(modo, destaque)

    fun adicionar(usuarios: List<Long>) = gerenciador.adicionar(usuarios)

    fun ativarTransmissao(video: Boolean) = gerenciador.ativarTransmissao(video)

    /** Primeira mensagem do chat da chamada: cria o chat (se preciso), põe na fila de envio e abre a conversa. */
    fun enviarNoChat(texto: String, aoAbrir: (Long) -> Unit, aoFalhar: () -> Unit) {
        viewModelScope.launch {
            val conversa = gerenciador.garantirChat() ?: return@launch aoFalhar()
            envio.enviarTexto(conversa, texto)
            aoAbrir(conversa)
        }
    }
}

@Composable
fun TelaChamadaRota(
    aoFechar: () -> Unit,
    atenderAoAbrir: Boolean = false,
    aoAtenderAoAbrir: () -> Unit = {},
    emPip: Boolean = false,
    aoPodePip: (Boolean) -> Unit = {},
    aoMinimizar: () -> Unit = aoFechar,
    aoAreaDoVideo: (Rect) -> Unit = {},
    aoAbrirChat: (Long) -> Unit = {},
    viewModel: ChamadaViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val trilhas by viewModel.trilhas.collectAsStateWithLifecycle()
    val emEspera by viewModel.emEspera.collectAsStateWithLifecycle()
    val rotas by viewModel.rotas.collectAsStateWithLifecycle()
    val contatos by viewModel.contatos.collectAsStateWithLifecycle(emptyList())
    var adicionando by remember { mutableStateOf(false) }
    var escrevendoNoChat by remember { mutableStateOf(false) }
    val rotaAtual by viewModel.rotaAtual.collectAsStateWithLifecycle()
    // Celular no ouvido: a tela apaga (só com o áudio no fone do aparelho, 6.8).
    SensorDeProximidade(
        ativo = rotaAtual?.type == CallEndpointCompat.TYPE_EARPIECE &&
            estado.fase in setOf(FaseChamada.CHAMANDO, FaseChamada.CONECTANDO, FaseChamada.ATIVA),
    )
    val contexto = LocalContext.current
    LaunchedEffect(estado.fase) { if (estado.fase == FaseChamada.INATIVO) aoFechar() }
    // Voltar minimiza: a chamada continua (TODO 6.9).
    BackHandler(onBack = aoMinimizar)
    // Vídeo em andamento: sair do app vira picture-in-picture (6.12).
    val podePip = estado.fase == FaseChamada.ATIVA && estado.tipo == TipoChamada.VIDEO
    LaunchedEffect(podePip) { aoPodePip(podePip) }

    // Permissões na hora de atender ou de ligar a câmera; sem elas segue como der.
    var depoisDasPermissoes by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissoes = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        depoisDasPermissoes?.invoke()
        depoisDasPermissoes = null
    }
    val pedirEDepois: (Boolean, () -> Unit) -> Unit = { comCamera, acao ->
        val faltam = listOfNotNull(
            Manifest.permission.RECORD_AUDIO.takeUnless { contexto.tem(it) },
            Manifest.permission.CAMERA.takeIf { comCamera && !contexto.tem(it) },
        )
        if (faltam.isEmpty()) {
            acao()
        } else {
            depoisDasPermissoes = acao
            permissoes.launch(faltam.toTypedArray())
        }
    }

    // "Atender" da notificação: atende quando a chamada já está tocando aqui.
    LaunchedEffect(atenderAoAbrir, estado.tocando) {
        if (atenderAoAbrir && estado.tocando) {
            aoAtenderAoAbrir()
            pedirEDepois(estado.tipo == TipoChamada.VIDEO) { viewModel.atender(false) }
        }
    }

    Surface(Modifier.fillMaxSize(), color = ConversaTema.cores.chamadaFundo) {
        if (emPip) {
            TelaPip(estado, trilhas, viewModel.egl)
        } else if (estado.fase == FaseChamada.RECEBENDO) {
            TelaRecebendo(
                estado = estado,
                aoAtender = { soAssistir ->
                    pedirEDepois(estado.tipo == TipoChamada.VIDEO && !soAssistir) { viewModel.atender(soAssistir) }
                },
                aoRecusar = viewModel::recusar,
            )
        } else {
            TelaEmChamada(
                estado = estado,
                emEspera = emEspera,
                rotas = Rotas(rotas, rotaAtual),
                trilhas = trilhas,
                eu = viewModel.eu,
                egl = viewModel.egl,
                acoes = AcoesEmChamada(
                    aoMicrofone = viewModel::alternarMicrofone,
                    aoCamera = viewModel::alternarCamera,
                    aoTrocarCamera = viewModel::trocarCamera,
                    aoAtivarVideo = { pedirEDepois(true) { viewModel.ligarVideo() } },
                    aoSair = viewModel::desligar,
                    aoRetomar = viewModel::retomar,
                    aoMudarRota = viewModel::mudarRota,
                    aoExibir = viewModel::exibir,
                    aoAreaDoVideo = aoAreaDoVideo,
                    aoAdicionar = { adicionando = true },
                    aoAtivarTransmissao = { video -> pedirEDepois(video) { viewModel.ativarTransmissao(video) } },
                    // Chat já existe: a conversa completa; senão, o campo da primeira mensagem (como o web).
                    aoChat = { estado.conversaChatId?.let(aoAbrirChat) ?: run { escrevendoNoChat = true } },
                ),
            )
        }
    }
    if (escrevendoNoChat) {
        val avisos = LocalAvisos.current
        val escopo = rememberCoroutineScope()
        val falhou = stringResource(R.string.erro_ao_enviar)
        PrimeiraMensagemDoChat(
            aoEnviar = { texto ->
                escrevendoNoChat = false
                viewModel.enviarNoChat(texto, aoAbrir = aoAbrirChat, aoFalhar = { escopo.launch { avisos.showSnackbar(falhou) } })
            },
            aoFechar = { escrevendoNoChat = false },
        )
    }
    if (adicionando) {
        // Fora da chamada: quem não está entre os participantes (qualquer status), como o web.
        val naChamada = estado.dados?.participantes.orEmpty().mapTo(mutableSetOf()) { it.usuarioId } + viewModel.eu
        DialogoAdicionar(
            contatos = contatos.filter { it.id !in naChamada },
            aoConfirmar = { ids ->
                adicionando = false
                viewModel.adicionar(ids)
            },
            aoCancelar = { adicionando = false },
        )
    }
    estado.pedidoVideo?.let { pedido ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.ativou_o_video, pedido.nome.ifBlank { stringResource(R.string.alguem) })) },
            confirmButton = {
                TextButton(onClick = { pedirEDepois(true) { viewModel.responderVideo(transmitir = true) } }) {
                    Text(stringResource(R.string.transmitir_tambem))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.responderVideo(transmitir = false) }) { Text(stringResource(R.string.apenas_assistir)) }
            },
        )
    }
}

/** Chamada recebida: quem liga, o tipo e os botões grandes e tocáveis (#7). */
@Composable
private fun TelaRecebendo(estado: EstadoChamada, aoAtender: (soAssistir: Boolean) -> Unit, aoRecusar: () -> Unit) {
    val nome = estado.dados?.participantes?.firstOrNull { it.usuarioId == estado.remetenteId }?.nome
        ?.takeIf { it.isNotBlank() } ?: stringResource(R.string.alguem)
    val cores = ConversaTema.cores
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(nome, null, tamanho = 112.dp)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.chamada_recebida), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.esta_ligando, nome),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(if (estado.tipo == TipoChamada.VIDEO) R.string.video_e_audio else R.string.somente_audio),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
                BotaoRedondo(
                    Icons.Filled.CallEnd,
                    stringResource(R.string.recusar),
                    cores.botaoRecusar,
                    Color.White,
                    72,
                    onClick = aoRecusar,
                )
                BotaoRedondo(Icons.Filled.Call, stringResource(R.string.atender), cores.botaoAtender, Color.White, 72, onClick = {
                    aoAtender(false)
                })
            }
            if (estado.tipo == TipoChamada.VIDEO) {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { aoAtender(true) }) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.atender_so_assistindo))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class AcoesEmChamada(
    val aoMicrofone: () -> Unit,
    val aoCamera: () -> Unit,
    val aoTrocarCamera: () -> Unit,
    val aoAtivarVideo: () -> Unit,
    val aoSair: () -> Unit,
    val aoRetomar: () -> Unit,
    val aoMudarRota: (CallEndpointCompat) -> Unit,
    val aoExibir: (ModoExibicao, Long?) -> Unit,
    /** Onde o vídeo está na tela: o picture-in-picture "encolhe" a partir daí. */
    val aoAreaDoVideo: (Rect) -> Unit,
    val aoAdicionar: () -> Unit,
    /** Somente recepção: "Ativar microfone" (false) / "Ativar câmera" (true). */
    val aoAtivarTransmissao: (Boolean) -> Unit,
    val aoChat: () -> Unit,
)

/** Rotas de áudio do Telecom: as disponíveis e a de agora. */
private data class Rotas(val disponiveis: List<CallEndpointCompat>, val atual: CallEndpointCompat?)

@Composable
private fun TelaEmChamada(
    estado: EstadoChamada,
    emEspera: Boolean,
    rotas: Rotas,
    trilhas: TrilhasChamada,
    eu: Long,
    egl: EglBase.Context,
    acoes: AcoesEmChamada,
) {
    val nomes = estado.dados?.participantes.orEmpty().associate { it.usuarioId to it.nome }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Cabecalho(estado)
        if (trilhas.remotos.size >= 2) {
            // Trocar de modo mantém quem está em destaque; sem destaque, começa pelo primeiro (como o web).
            val destaque = estado.exibicao.destaque?.takeIf { it in trilhas.remotos } ?: trilhas.remotos.keys.first()
            SeletorDeExibicao(estado.exibicao.modo) { modo -> acoes.aoExibir(modo, destaque) }
        }
        if (emEspera) {
            // Em espera por outra chamada (celular): ninguém nos ouve até retomar.
            Row(
                Modifier.fillMaxWidth().background(ConversaTema.cores.avisoConexao).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.em_espera), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = acoes.aoRetomar) { Text(stringResource(R.string.retomar)) }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { area ->
                val r = area.boundsInWindow()
                acoes.aoAreaDoVideo(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
            },
        ) {
            val remotos = trilhas.remotos
            if (remotos.isEmpty()) {
                // Chamando (ou ninguém transmitindo ainda): quem foi chamado.
                val outros = estado.dados?.participantes.orEmpty()
                    .filter { it.usuarioId != eu && it.status != StatusParticipante.RECUSOU }
                val nome = outros.joinToString(", ") { it.nome }.ifBlank { stringResource(R.string.alguem) }
                Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(outros.singleOrNull()?.nome ?: nome, null, tamanho = 112.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(nome, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                }
            } else {
                Participantes(estado.exibicao, remotos, nomes, egl, acoes.aoExibir)
            }
            val videoLocal = trilhas.videoLocal
            if (videoLocal != null && estado.cameraLigada) {
                // Miniatura local, com o nome "Você" (TODO 6.9).
                Box(
                    Modifier.align(Alignment.TopEnd).padding(12.dp).size(width = 104.dp, height = 144.dp).clip(RoundedCornerShape(12.dp))
                        .anelDeFala(trilhas.falandoLocal),
                ) {
                    VideoDaTrilha(videoLocal, egl, espelhar = true, sobreposto = true, modifier = Modifier.fillMaxSize())
                    Text(
                        stringResource(R.string.voce),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                            .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp),
                    )
                }
            }
        }
        BarraDeControles(estado, rotas, acoes)
    }
}

@Composable
private fun Cabecalho(estado: EstadoChamada) {
    val cores = ConversaTema.cores
    val status = when (estado.fase) {
        FaseChamada.CHAMANDO -> stringResource(R.string.chamando)
        FaseChamada.CONECTANDO -> stringResource(R.string.conectando)
        FaseChamada.ENCERRANDO -> stringResource(R.string.encerrando)
        else -> stringResource(R.string.em_chamada)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ativa = estado.fase == FaseChamada.ATIVA
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (ativa) cores.chamadaEmAndamento else cores.avisoConexao))
        Text(status, style = MaterialTheme.typography.labelLarge)
        estado.ativaDesde?.takeIf { ativa }?.let { Duracao(it) }
        Text(
            stringResource(if (estado.tipo == TipoChamada.VIDEO) R.string.video else R.string.audio),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.clip(
                RoundedCornerShape(50),
            ).background(cores.chamadaBarraInferior).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        val pessoas = estado.dados?.participantes?.count { it.status == StatusParticipante.ENTROU } ?: 0
        if (pessoas > 2) Text(pluralStringResource(R.plurals.pessoas, pessoas, pessoas), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun Duracao(desde: Instant, cor: Color = MaterialTheme.colorScheme.primary) {
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(desde) {
        while (true) {
            agora = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val segundos = Duration.between(desde, Instant.ofEpochMilli(agora)).seconds.coerceAtLeast(0)
    Text(formatarDuracao(segundos), style = MaterialTheme.typography.labelLarge, color = cor)
}

/** Como o web: `mm:ss`, ou `hh:mm:ss` passando de uma hora. */
internal fun formatarDuracao(segundos: Long): String {
    val h = segundos / 3600
    val m = (segundos % 3600) / 60
    val s = segundos % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/**
 * Os outros participantes no modo escolhido (6.11). Quem estava em destaque e saiu
 * (ou ainda não conectou) cai na grade até voltar; o modo fica guardado.
 */
@Composable
private fun Participantes(
    exibicao: Exibicao,
    remotos: Map<Long, TrilhaRemota>,
    nomes: Map<Long, String>,
    egl: EglBase.Context,
    aoExibir: (ModoExibicao, Long?) -> Unit,
) {
    val destaque = exibicao.destaque?.takeIf { it in remotos }
    when {
        exibicao.modo == ModoExibicao.GRADE || destaque == null ->
            GradeDeParticipantes(remotos, nomes, egl, aoTocar = { aoExibir(ModoExibicao.DESTAQUE, it) })
        exibicao.modo == ModoExibicao.DESTAQUE -> Column(
            Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Toque no destaque volta à grade; toque num pequeno, ele vira o destaque.
            key(destaque) {
                Participante(nomes[destaque].orEmpty(), remotos.getValue(destaque), egl, Modifier.weight(1f).fillMaxWidth(), aoTocar = {
                    aoExibir(ModoExibicao.GRADE, null)
                })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((usuario, trilha) in remotos) {
                    if (usuario == destaque) continue
                    key(usuario) {
                        Participante(
                            nomes[usuario].orEmpty(),
                            trilha,
                            egl,
                            Modifier.size(width = 96.dp, height = 128.dp),
                            tamanhoAvatar = 48.dp,
                            aoTocar = { aoExibir(ModoExibicao.DESTAQUE, usuario) },
                        )
                    }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize().padding(8.dp)) {
            key(destaque) {
                Participante(nomes[destaque].orEmpty(), remotos.getValue(destaque), egl, Modifier.fillMaxSize(), aoTocar = {
                    aoExibir(ModoExibicao.GRADE, null)
                })
            }
            if (remotos.size > 1) {
                // Setas para trocar quem aparece (como no web).
                val lista = remotos.keys.toList()
                val indice = lista.indexOf(destaque)
                Seta(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    stringResource(R.string.participante_anterior),
                    Modifier.align(Alignment.CenterStart),
                ) {
                    aoExibir(ModoExibicao.UNICA, lista[(indice - 1 + lista.size) % lista.size])
                }
                Seta(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    stringResource(R.string.proximo_participante),
                    Modifier.align(Alignment.CenterEnd),
                ) {
                    aoExibir(ModoExibicao.UNICA, lista[(indice + 1) % lista.size])
                }
            }
        }
    }
}

@Composable
private fun Seta(icone: ImageVector, descricao: String, modifier: Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier.padding(4.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f))) {
        Icon(icone, contentDescription = descricao, tint = Color.White)
    }
}

/** Grade, Destaque, Tela única (rótulos do web). */
@Composable
private fun SeletorDeExibicao(atual: ModoExibicao, aoEscolher: (ModoExibicao) -> Unit) {
    val opcoes = listOf(
        Triple(ModoExibicao.GRADE, Icons.Filled.GridView, R.string.modo_grade),
        Triple(ModoExibicao.DESTAQUE, Icons.Filled.ViewAgenda, R.string.modo_destaque),
        Triple(ModoExibicao.UNICA, Icons.Filled.Fullscreen, R.string.modo_tela_unica),
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((modo, icone, rotulo) in opcoes) {
            FilterChip(
                selected = atual == modo,
                onClick = { aoEscolher(modo) },
                label = { Text(stringResource(rotulo)) },
                leadingIcon = { Icon(icone, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

/** 1 participante ocupa tudo; 2 ficam um sobre o outro; a partir de 3, duas colunas. */
@Composable
private fun GradeDeParticipantes(
    remotos: Map<Long, TrilhaRemota>,
    nomes: Map<Long, String>,
    egl: EglBase.Context,
    aoTocar: (Long) -> Unit,
) {
    val linhas = remotos.entries.toList().let { lista -> if (lista.size <= 2) lista.map { listOf(it) } else lista.chunked(2) }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (linha in linhas) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((usuario, trilha) in linha) {
                    key(usuario) {
                        Participante(nomes[usuario].orEmpty(), trilha, egl, Modifier.weight(1f).fillMaxSize(), aoTocar = {
                            aoTocar(usuario)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun Participante(
    nome: String,
    trilha: TrilhaRemota,
    egl: EglBase.Context,
    modifier: Modifier,
    tamanhoAvatar: Dp = 80.dp,
    aoTocar: () -> Unit = {},
) {
    val cores = ConversaTema.cores
    // Anel verde enquanto fala (6.13); por cima do vídeo.
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(cores.chamadaBarraInferior).anelDeFala(trilha.falando)) {
        val video = trilha.video
        if (video != null) {
            VideoDaTrilha(video, egl, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(nome, null, tamanho = tamanhoAvatar)
                if (!trilha.conectado && !trilha.falhou) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.conectando), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            nome,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
        // Por cima do vídeo (o SurfaceView não repassa o toque). Por cobrir o nome, leva o nome para o TalkBack.
        Box(Modifier.matchParentSize().semantics { contentDescription = nome }.clickable(onClick = aoTocar))
        if (trilha.falhou) {
            // Faixa vermelha de erro de conexão (CHA-11).
            Text(
                stringResource(R.string.reconectando),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(cores.chamadaEncerrar).padding(4.dp),
            )
        }
    }
}

/**
 * Desenha uma trilha de vídeo. `key(trilha)` recria o renderer quando a trilha muda e
 * o `onRelease` solta o renderer da trilha antes de liberar (#39); a trilha pode já ter
 * sido descartada pela mídia (fim da chamada), por isso o `removeSink` é protegido.
 */
@Composable
private fun VideoDaTrilha(
    trilha: VideoTrack,
    egl: EglBase.Context,
    modifier: Modifier = Modifier,
    espelhar: Boolean = false,
    sobreposto: Boolean = false,
) {
    key(trilha) {
        AndroidView(
            factory = { contexto ->
                SurfaceViewRenderer(contexto).apply {
                    init(egl, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                    setMirror(espelhar)
                    // Miniatura local por cima do vídeo do outro.
                    if (sobreposto) setZOrderMediaOverlay(true)
                    try {
                        trilha.addSink(this)
                    } catch (_: IllegalStateException) {
                    }
                }
            },
            onRelease = { renderer ->
                try {
                    trilha.removeSink(renderer)
                } catch (_: IllegalStateException) {
                }
                renderer.release()
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun BarraDeControles(estado: EstadoChamada, rotas: Rotas, acoes: AcoesEmChamada) {
    val cores = ConversaTema.cores
    val ligado = stringResource(R.string.ligado)
    val desligado = stringResource(R.string.desligado)
    // Rola na horizontal quando os botões não cabem (tela estreita, chamada de vídeo).
    Row(
        Modifier.fillMaxWidth().background(cores.chamadaBarraInferior).horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val emAndamento = estado.fase != FaseChamada.ENCERRANDO
        if (estado.midiaLocal == MidiaLocal.NENHUMA && estado.fase == FaseChamada.ATIVA) {
            // Entrou só recebendo (sem microfone nem câmera): pode começar a transmitir (6.13).
            BotaoRedondo(Icons.Filled.Mic, stringResource(R.string.ativar_microfone), cores.chamadaBotao, cores.chamadaIconeBotao) {
                acoes.aoAtivarTransmissao(false)
            }
            if (estado.tipo == TipoChamada.VIDEO) {
                BotaoRedondo(Icons.Filled.Videocam, stringResource(R.string.ativar_camera), cores.chamadaBotao, cores.chamadaIconeBotao) {
                    acoes.aoAtivarTransmissao(true)
                }
            }
        }
        if (estado.midiaLocal != MidiaLocal.NENHUMA) {
            // Microfone e câmera ficam vermelhos quando desligados (TODO 6.9).
            BotaoAlternar(
                if (estado.microfoneLigado) Icons.Filled.Mic else Icons.Filled.MicOff,
                stringResource(R.string.microfone),
                if (estado.microfoneLigado) ligado else desligado,
                desligadoVermelho = !estado.microfoneLigado,
                onClick = acoes.aoMicrofone,
            )
        }
        if (estado.midiaLocal == MidiaLocal.AUDIO_VIDEO) {
            BotaoAlternar(
                if (estado.cameraLigada) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                stringResource(R.string.camera),
                if (estado.cameraLigada) ligado else desligado,
                desligadoVermelho = !estado.cameraLigada,
                onClick = acoes.aoCamera,
            )
            if (estado.cameraLigada) {
                BotaoRedondo(
                    Icons.Filled.Cameraswitch,
                    stringResource(R.string.trocar_camera),
                    cores.chamadaBotao,
                    cores.chamadaIconeBotao,
                    onClick = acoes.aoTrocarCamera,
                )
            }
        } else if (estado.tipo == TipoChamada.AUDIO && estado.fase == FaseChamada.ATIVA) {
            BotaoRedondo(
                Icons.Filled.Videocam,
                stringResource(R.string.ativar_video),
                cores.chamadaBotao,
                cores.chamadaIconeBotao,
                onClick = acoes.aoAtivarVideo,
            )
        }
        BotaoRota(rotas, acoes.aoMudarRota)
        if (estado.fase == FaseChamada.ATIVA) {
            BotaoRedondo(
                Icons.Filled.PersonAdd,
                stringResource(R.string.adicionar_usuario),
                cores.chamadaBotao,
                cores.chamadaIconeBotao,
                onClick = acoes.aoAdicionar,
            )
            BotaoRedondo(
                Icons.AutoMirrored.Filled.Chat,
                stringResource(R.string.chat_da_chamada),
                cores.chamadaBotao,
                cores.chamadaIconeBotao,
                onClick = acoes.aoChat,
            )
        }
        BotaoRedondo(
            Icons.Filled.CallEnd,
            stringResource(R.string.sair_da_chamada),
            cores.chamadaEncerrar,
            Color.White,
            enabled = emAndamento,
            onClick = acoes.aoSair,
        )
    }
}

@Composable
private fun BotaoAlternar(icone: ImageVector, descricao: String, estadoTexto: String, desligadoVermelho: Boolean, onClick: () -> Unit) {
    val cores = ConversaTema.cores
    BotaoRedondo(
        icone,
        descricao,
        if (desligadoVermelho) cores.chamadaEncerrar else cores.chamadaBotao,
        if (desligadoVermelho) Color.White else cores.chamadaIconeBotao,
        estadoTexto = estadoTexto,
        onClick = onClick,
    )
}

@Composable
private fun BotaoRedondo(
    icone: ImageVector,
    descricao: String,
    fundo: Color,
    corIcone: Color,
    tamanho: Int = 56,
    enabled: Boolean = true,
    estadoTexto: String? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = fundo,
        modifier = Modifier.size(tamanho.dp).semantics {
            contentDescription = descricao
            role = Role.Button
            if (estadoTexto != null) stateDescription = estadoTexto
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icone, contentDescription = null, tint = corIcone, modifier = Modifier.size((tamanho * 0.45f).dp))
        }
    }
}

/**
 * "Áudio saída" (título do web): com duas rotas (fone do aparelho e alto-falante) alterna
 * direto; com mais (Bluetooth, fone com fio), abre a lista. Sem Telecom não há rotas e o
 * botão não aparece.
 */
@Composable
private fun BotaoRota(rotas: Rotas, aoMudar: (CallEndpointCompat) -> Unit) {
    val atual = rotas.atual ?: return
    val cores = ConversaTema.cores
    var lista by remember { mutableStateOf(false) }
    val textoAtual = rotuloDaRota(atual)
    Box {
        BotaoRedondo(
            iconeDaRota(atual.type),
            stringResource(R.string.audio_saida),
            cores.chamadaBotao,
            cores.chamadaIconeBotao,
            estadoTexto = textoAtual,
            onClick = {
                val outras = rotas.disponiveis.filter { it.identifier != atual.identifier }
                when {
                    outras.size == 1 -> aoMudar(outras.single())
                    outras.size > 1 -> lista = true
                }
            },
        )
        DropdownMenu(expanded = lista, onDismissRequest = { lista = false }) {
            for (rota in rotas.disponiveis) {
                DropdownMenuItem(
                    text = { Text(rotuloDaRota(rota)) },
                    leadingIcon = { Icon(iconeDaRota(rota.type), contentDescription = null) },
                    trailingIcon = { if (rota.identifier == atual.identifier) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        lista = false
                        aoMudar(rota)
                    },
                )
            }
        }
    }
}

@Composable
private fun rotuloDaRota(rota: CallEndpointCompat): String = when (rota.type) {
    CallEndpointCompat.TYPE_EARPIECE -> stringResource(R.string.rota_fone_do_aparelho)
    CallEndpointCompat.TYPE_SPEAKER -> stringResource(R.string.rota_alto_falante)
    CallEndpointCompat.TYPE_WIRED_HEADSET -> stringResource(R.string.rota_fone_com_fio)
    // Bluetooth: o nome do aparelho (ex.: "Fone JBL").
    else -> rota.name.toString().ifBlank { stringResource(R.string.rota_bluetooth) }
}

private fun iconeDaRota(tipo: Int): ImageVector = when (tipo) {
    CallEndpointCompat.TYPE_EARPIECE -> Icons.Filled.PhoneInTalk
    CallEndpointCompat.TYPE_BLUETOOTH -> Icons.Filled.BluetoothAudio
    CallEndpointCompat.TYPE_WIRED_HEADSET -> Icons.Filled.Headset
    else -> Icons.AutoMirrored.Filled.VolumeUp
}

/** Apaga a tela quando o celular encosta no rosto (PROXIMITY_SCREEN_OFF_WAKE_LOCK). */
@Composable
private fun SensorDeProximidade(ativo: Boolean) {
    val contexto = LocalContext.current
    DisposableEffect(ativo) {
        val energia = contexto.getSystemService(PowerManager::class.java)
        val trava = if (ativo && energia?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
            energia.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "conversa:proximidade").apply {
                setReferenceCounted(false)
                acquire(DURACAO_MAXIMA_PROXIMIDADE_MS)
            }
        } else {
            null
        }
        onDispose { if (trava?.isHeld == true) trava.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
    }
}

/** Teto de segurança da trava (o onDispose solta antes). */
private const val DURACAO_MAXIMA_PROXIMIDADE_MS = 4 * 60 * 60 * 1000L

/** Picture-in-picture: só quem está em destaque (ou o primeiro), sem cabeçalho nem controles. */
@Composable
private fun TelaPip(estado: EstadoChamada, trilhas: TrilhasChamada, egl: EglBase.Context) {
    val remotos = trilhas.remotos
    val principal =
        estado.exibicao.destaque?.takeIf { it in remotos } ?: remotos.entries.firstOrNull { it.value.video != null }?.key
            ?: remotos.keys.firstOrNull()
    val nome = estado.dados?.participantes?.firstOrNull { it.usuarioId == principal }?.nome.orEmpty()
    val trilha = principal?.let { remotos[it] }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val video = trilha?.video
        if (video !=
            null
        ) {
            VideoDaTrilha(video, egl, modifier = Modifier.fillMaxSize())
        } else {
            Avatar(nome.ifBlank { null }, null, tamanho = 56.dp)
        }
    }
}

/** "Adicionar à chamada" (texto do web): marca os contatos e adiciona de uma vez. */
@Composable
private fun DialogoAdicionar(contatos: List<Contato>, aoConfirmar: (List<Long>) -> Unit, aoCancelar: () -> Unit) {
    val marcados = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(stringResource(R.string.adicionar_a_chamada)) },
        text = {
            if (contatos.isEmpty()) {
                Text(stringResource(R.string.nenhum_contato_para_adicionar))
            } else {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(contatos, key = { it.id }) { contato ->
                        val marcado = contato.id in marcados
                        Row(
                            Modifier.fillMaxWidth().clickable { if (marcado) marcados.remove(contato.id) else marcados.add(contato.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = marcado, onCheckedChange = null)
                            Spacer(Modifier.size(8.dp))
                            Text(contato.nome, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = marcados.isNotEmpty(), onClick = {
                aoConfirmar(marcados.toList())
            }) { Text(stringResource(R.string.adicionar)) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/**
 * "Chat da chamada" antes de existir o chat (texto do web): só o campo. A primeira mensagem
 * cria o chat (`PUT /chamada/chat`) e abre a conversa completa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrimeiraMensagemDoChat(aoEnviar: (String) -> Unit, aoFechar: () -> Unit) {
    var texto by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding()) {
            Text(stringResource(R.string.chat_da_chamada), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text(stringResource(R.string.mensagem)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                IconButton(enabled = texto.isNotBlank(), onClick = { aoEnviar(texto.trim()) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.enviar))
                }
            }
        }
    }
}

/** Contorno verde de quem está falando (o "anel" do web). */
@Composable
private fun Modifier.anelDeFala(falando: Boolean): Modifier =
    if (falando) border(3.dp, ConversaTema.cores.chamadaEmAndamento, RoundedCornerShape(12.dp)) else this
