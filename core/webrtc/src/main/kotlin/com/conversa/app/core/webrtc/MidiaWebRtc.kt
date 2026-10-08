package com.conversa.app.core.webrtc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.conversa.app.core.model.ServidoresIce
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.JavaI420Buffer
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import timber.log.Timber

/** O que a tela da chamada desenha: o vídeo local (PiP) e o de cada participante. */
data class TrilhasChamada(
    val videoLocal: VideoTrack? = null,
    val remotos: Map<Long, TrilhaRemota> = emptyMap(),
    /** Eu falando agora (anel verde, 6.13). */
    val falandoLocal: Boolean = false,
)

data class TrilhaRemota(
    val video: VideoTrack? = null,
    val conectado: Boolean = false,
    /** A conexão caiu ou o WHEP não respondeu: faixa vermelha no tile (CHA-11). */
    val falhou: Boolean = false,
    /** Falando agora: anel verde (6.13). */
    val falando: Boolean = false,
)

/**
 * [MidiaChamada] com o libwebrtc e o MediaMTX (contrato §9.9, plano §3.5):
 * - publicação: uma `PeerConnection` só de envio (Opus a 32 kbps, câmera 360p a 15 fps), H264 → VP9 → resto;
 * - uma assinatura por participante: `PeerConnection` só de recepção, registrada antes da
 *   resposta (#10); WHEP 404 tenta de novo (vídeo 40×1 s, áudio 12×0,8 s);
 * - conexão que falha (ICE) refaz só aquela `PeerConnection`;
 * - ICE de `GET /api/ice` a cada conexão; "forçar relay" → só TURN.
 *
 * Os objetos do WebRTC só são mexidos numa vez única ([vez]); a rede não segura a
 * vez (#18). [encerrar] troca a sessão: o que estava em andamento para sem mexer em
 * nada, e a desmontagem libera tudo (#37).
 */
@Singleton
class MidiaWebRtc @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val webrtc: FabricaWebRtc,
    private val whip: ClienteWhipWhep,
    private val api: ConversaApi,
    @EscopoAplicacao private val escopoApp: CoroutineScope,
) : MidiaChamada {
    private val vez = Dispatchers.Default.limitedParallelism(1)
    private val raiz = SupervisorJob(escopoApp.coroutineContext[Job])
    private val escopo = CoroutineScope(raiz + vez + CoroutineExceptionHandler { _, e -> Timber.w(e, "Mídia da chamada") })

    private val _trilhas = MutableStateFlow(TrilhasChamada())
    val trilhas: StateFlow<TrilhasChamada> = _trilhas.asStateFlow()

    /** Para os `SurfaceViewRenderer` da tela. */
    val contextoEgl: EglBase.Context get() = webrtc.egl.eglBaseContext

    private val trava = Any()
    private var sessao = 0L

    /** Tarefas da sessão atual (assinaturas, reconexões); canceladas ao encerrar. */
    private var trabalhos: Job = SupervisorJob(raiz)

    // Só mexidos na [vez]:
    private var local: Local? = null
    private var publicacao: Publicacao? = null
    private var republicando = false
    private var medidor: Job? = null
    private var falaLocal = DetectorDeFala()
    private var falandoLocal = false
    private val assinaturas = mutableMapOf<Long, Assinatura>()
    private var chamadaId = 0L
    private var eu = 0L
    private var comVideo = false

    private class Local {
        var fonteAudio: AudioSource? = null
        var audio: AudioTrack? = null
        var capturador: VideoCapturer? = null
        var ajudante: SurfaceTextureHelper? = null
        var fonteVideo: VideoSource? = null
        var video: VideoTrack? = null
        var capturando = false

        /** Câmera desligada: quadros pretos mantêm a trilha viva no MediaMTX. */
        var quadrosPretos: Job? = null

        val resultado: MidiaLocal
            get() = when {
                audio == null -> MidiaLocal.NENHUMA
                video == null -> MidiaLocal.AUDIO
                else -> MidiaLocal.AUDIO_VIDEO
            }
    }

    private class Publicacao(val pc: PeerConnection, val observador: Observador) {
        var recurso: HttpUrl? = null
        var estado = PeerConnection.PeerConnectionState.NEW
    }

    private class Assinatura(val usuario: Long, val comVideo: Boolean) {
        var pc: PeerConnection? = null
        var recurso: HttpUrl? = null
        var video: VideoTrack? = null
        var estado = PeerConnection.PeerConnectionState.NEW
        var falhou = false

        /** A resposta do MediaMTX diz que vem vídeo: sem trilha depois de conectar, refazer. */
        var esperaVideo = false
        var tarefa: Job? = null
        val fala = DetectorDeFala()
        var falando = false
    }

    // --- MidiaChamada ---

    override suspend fun abrirLocal(video: Boolean): MidiaLocal = naVez { abrir(video) }

    override suspend fun publicar(chamadaId: Long, eu: Long) = naVez { s ->
        this@MidiaWebRtc.chamadaId = chamadaId
        this@MidiaWebRtc.eu = eu
        publicarAgora(s)
    }

    override suspend fun sincronizar(chamadaId: Long, participantes: Set<Long>, comVideo: Boolean) = naVez { s ->
        this@MidiaWebRtc.chamadaId = chamadaId
        this@MidiaWebRtc.comVideo = comVideo
        (assinaturas.keys - participantes).toList().forEach(::soltar)
        for (usuario in participantes) {
            val atual = assinaturas[usuario]
            if (atual == null || atual.falhou || (comVideo && !atual.comVideo)) assinar(usuario, s)
        }
    }

    override suspend fun reassinar(usuarioId: Long) = naVez { s -> assinar(usuarioId, s) }

    override fun desconectar(usuarioId: Long) {
        naVezSemEsperar { soltar(usuarioId) }
    }

    override suspend fun ativarVideo(transmitir: Boolean): Boolean = naVez { s ->
        val l = local ?: Local().also { local = it }
        if (l.video == null && permitido(Manifest.permission.CAMERA)) abrirCamera(l)
        if (!transmitir) desligarCamera(l)
        comVideo = true
        publicarTrilhas()
        // Republica com o vídeo e assina todos de novo, agora com vídeo (como o web).
        try {
            publicarAgora(s)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Republicar com vídeo falhou: %s", e.javaClass.simpleName)
        }
        assinaturas.keys.toList().forEach { assinar(it, s) }
        l.video != null
    }

    override fun microfone(ligado: Boolean) {
        naVezSemEsperar { local?.audio?.setEnabled(ligado) }
    }

    override fun camera(ligada: Boolean) {
        naVezSemEsperar { local?.let { if (ligada) ligarCamera(it) else desligarCamera(it) } }
    }

    /** Frontal ↔ traseira. */
    fun trocarCamera() {
        naVezSemEsperar { (local?.capturador as? CameraVideoCapturer)?.switchCamera(null) }
    }

    override fun encerrar() {
        val anteriores = synchronized(trava) {
            sessao++
            trabalhos.also { trabalhos = SupervisorJob(raiz) }
        }
        anteriores.cancel()
        escopo.launch { desmontar() }
    }

    // --- Mídia local ---

    private fun abrir(video: Boolean): MidiaLocal {
        val l = local ?: Local().also { local = it }
        if (l.audio == null && permitido(Manifest.permission.RECORD_AUDIO)) {
            try {
                val fonte = webrtc.fabrica.createAudioSource(MediaConstraints())
                l.fonteAudio = fonte
                l.audio = webrtc.fabrica.createAudioTrack("audio0", fonte)
            } catch (e: RuntimeException) {
                Timber.w("Microfone indisponível: %s", e.javaClass.simpleName)
            }
        }
        if (video && l.video == null && permitido(Manifest.permission.CAMERA)) abrirCamera(l)
        publicarTrilhas()
        return l.resultado
    }

    private fun abrirCamera(l: Local) {
        try {
            val enumerador = if (Camera2Enumerator.isSupported(contexto)) Camera2Enumerator(contexto) else Camera1Enumerator(true)
            val nomes = enumerador.deviceNames
            val nome = nomes.firstOrNull { enumerador.isFrontFacing(it) } ?: nomes.firstOrNull() ?: return
            val capturador = enumerador.createCapturer(nome, null) ?: return
            val ajudante = SurfaceTextureHelper.create("camera", webrtc.egl.eglBaseContext)
            val fonte = webrtc.fabrica.createVideoSource(false)
            capturador.initialize(ajudante, contexto, fonte.capturerObserver)
            capturador.startCapture(LARGURA, ALTURA, QUADROS)
            l.capturador = capturador
            l.ajudante = ajudante
            l.fonteVideo = fonte
            l.video = webrtc.fabrica.createVideoTrack("video0", fonte)
            l.capturando = true
        } catch (e: RuntimeException) {
            Timber.w("Câmera indisponível: %s", e.javaClass.simpleName)
        }
    }

    /**
     * Câmera desligada: a câmera fecha (some o indicador do Android) e a trilha passa a
     * mandar quadros pretos. Sem pacote nenhum o MediaMTX nem registra a trilha de vídeo
     * ao publicar (e depois não dá para ligar a câmera); o web faz o mesmo com a trilha desabilitada.
     */
    private fun desligarCamera(l: Local) {
        l.video?.setEnabled(false)
        if (l.capturando) {
            pararCaptura(l)
            l.capturando = false
        }
        mandarQuadrosPretos(l)
    }

    private fun ligarCamera(l: Local) {
        l.quadrosPretos?.cancel()
        l.quadrosPretos = null
        l.video?.setEnabled(true)
        if (!l.capturando && l.capturador != null) {
            l.capturador?.startCapture(LARGURA, ALTURA, QUADROS)
            l.capturando = true
        }
    }

    private fun mandarQuadrosPretos(l: Local) {
        val fonte = l.fonteVideo ?: return
        if (l.quadrosPretos?.isActive == true) return
        l.quadrosPretos = escopo.launch {
            val preto = JavaI420Buffer.allocate(LARGURA_PRETO, ALTURA_PRETO).apply {
                dataY.let { y -> while (y.hasRemaining()) y.put(PRETO_Y) }
                dataU.let { u -> while (u.hasRemaining()) u.put(PRETO_UV) }
                dataV.let { v -> while (v.hasRemaining()) v.put(PRETO_UV) }
            }
            try {
                while (true) {
                    preto.retain()
                    val quadro = VideoFrame(preto, 0, System.nanoTime())
                    fonte.capturerObserver.onFrameCaptured(quadro)
                    quadro.release()
                    delay(INTERVALO_PRETO_MS)
                }
            } finally {
                preto.release()
            }
        }
    }

    private fun pararCaptura(l: Local) {
        try {
            l.capturador?.stopCapture()
        } catch (_: InterruptedException) {
        }
    }

    private fun permitido(permissao: String) = ContextCompat.checkSelfPermission(contexto, permissao) == PackageManager.PERMISSION_GRANTED

    // --- Publicação (WHIP) ---

    private suspend fun publicarAgora(s: Long) {
        val l = local
        val trilhas = listOfNotNull(l?.audio, l?.video)
        if (trilhas.isEmpty()) throw IOException("Nada para publicar")
        val ice = buscarIce()
        checar(s)
        publicacao?.let(::descartarPublicacao)
        publicacao = null
        var criada: Publicacao? = null
        val observador = Observador(aoMudarConexao = { estado -> escopo.launch { criada?.let { aoMudarPublicacao(it, estado, s) } } })
        val pc = webrtc.fabrica.createPeerConnection(configuracao(ice), observador) ?: throw IOException("PeerConnection")
        val pub = Publicacao(pc, observador).also {
            criada = it
            publicacao = it
        }
        for (trilha in trilhas) {
            val transceptor = pc.addTransceiver(
                trilha,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, listOf(STREAM)),
            )
            if (trilha is VideoTrack) preferirGravaveis(transceptor)
        }
        val oferta = pc.criarOferta()
        checar(s, pub)
        pc.definirLocal(oferta)
        checar(s, pub)
        pub.observador.esperarColeta()
        checar(s, pub)
        val sdp = pc.localDescription?.description ?: throw IOException("Sem oferta")
        val resposta = whip.publicar(caminhoStream(chamadaId, eu), sdp)
        if (s != sessaoAtual() || publicacao !== pub) {
            resposta.recurso?.let(::excluirDepois)
            throw CancellationException("Publicação substituída")
        }
        pub.recurso = resposta.recurso
        pc.definirRemota(SessionDescription(SessionDescription.Type.ANSWER, resposta.resposta))
        checar(s, pub)
        aplicarLimites(pc)
        garantirMedidor(s)
    }

    private fun aoMudarPublicacao(pub: Publicacao, estado: PeerConnection.PeerConnectionState, s: Long) {
        if (s != sessaoAtual() || publicacao !== pub) return
        pub.estado = estado
        when (estado) {
            PeerConnection.PeerConnectionState.FAILED -> republicar(pub, s, 0)
            PeerConnection.PeerConnectionState.DISCONNECTED -> republicar(pub, s, ESPERA_RECONEXAO_MS)
            else -> Unit
        }
    }

    /** A publicação caiu: publica de novo (como o web). "Desconectada" pode voltar sozinha; espera um pouco. */
    private fun republicar(pub: Publicacao, s: Long, espera: Long) {
        noTrabalho(s) {
            delay(espera)
            if (publicacao !== pub || pub.estado == PeerConnection.PeerConnectionState.CONNECTED || republicando) return@noTrabalho
            republicando = true
            try {
                publicarAgora(s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Republicar falhou: %s", e.javaClass.simpleName)
            } finally {
                republicando = false
            }
        }
    }

    private fun preferirGravaveis(transceptor: RtpTransceiver) {
        val codecs = webrtc.fabrica.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs
        val resultado = transceptor.setCodecPreferences(ordenarGravaveis(codecs) { it.mimeType })
        if (resultado.isError) Timber.w("Preferência de codec recusada")
    }

    /** Opus a 32 kbps ("normal" do web) e vídeo a 15 fps (celular). */
    private fun aplicarLimites(pc: PeerConnection) {
        for (transceptor in pc.transceivers) {
            val envio = transceptor.sender
            val trilha = envio.track() ?: continue
            val parametros = envio.parameters
            val codificacao = parametros.encodings.firstOrNull() ?: continue
            if (trilha.kind() ==
                MediaStreamTrack.AUDIO_TRACK_KIND
            ) {
                codificacao.maxBitrateBps = BITRATE_AUDIO
            } else {
                codificacao.maxFramerate = QUADROS
            }
            envio.setParameters(parametros)
        }
    }

    // --- Assinaturas (WHEP) ---

    private fun assinar(usuario: Long, s: Long) {
        assinaturas.remove(usuario)?.let(::descartarAssinatura)
        val a = Assinatura(usuario, comVideo)
        assinaturas[usuario] = a
        a.tarefa = noTrabalho(s) {
            try {
                conectarAssinatura(a, s)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("WHEP falhou: %s", e.javaClass.simpleName)
                if (assinaturas[usuario] === a) {
                    a.falhou = true
                    publicarTrilhas()
                }
            }
        }
        publicarTrilhas()
    }

    private suspend fun conectarAssinatura(a: Assinatura, s: Long) {
        val ice = buscarIce()
        checar(s, a)
        val observador = Observador(
            aoMudarConexao = { estado -> escopo.launch { aoMudarAssinatura(a, estado, s) } },
            aoReceberTrilha = { trilha -> escopo.launch { aoReceberTrilha(a, trilha, s) } },
        )
        val pc = webrtc.fabrica.createPeerConnection(configuracao(ice), observador) ?: throw IOException("PeerConnection")
        // Registrado antes da resposta: as trilhas que chegam já têm onde ficar (#10).
        a.pc = pc
        val recepcao = { RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY) }
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, recepcao())
        if (a.comVideo) pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, recepcao())
        val oferta = pc.criarOferta()
        checar(s, a)
        pc.definirLocal(oferta)
        checar(s, a)
        observador.esperarColeta()
        checar(s, a)
        val sdp = pc.localDescription?.description ?: throw IOException("Sem oferta")
        val (tentativas, intervalo) = if (a.comVideo) TENTATIVAS_VIDEO to INTERVALO_VIDEO_MS else TENTATIVAS_AUDIO to INTERVALO_AUDIO_MS
        var resposta: SessaoWhip? = null
        for (tentativa in 1..tentativas) {
            resposta = try {
                whip.assinar(caminhoStream(chamadaId, a.usuario), sdp)
            } catch (_: IOException) {
                null
            }
            if (s != sessaoAtual() || assinaturas[a.usuario] !== a) {
                resposta?.recurso?.let(::excluirDepois)
                throw CancellationException("Assinatura substituída")
            }
            if (resposta != null) break
            delay(intervalo)
        }
        if (resposta == null) throw StreamAusente()
        a.recurso = resposta.recurso
        a.esperaVideo = a.comVideo && sdpEnviaVideo(resposta.resposta)
        pc.definirRemota(SessionDescription(SessionDescription.Type.ANSWER, resposta.resposta))
        garantirMedidor(s)
    }

    // --- Quem está falando (6.13) ---

    private fun garantirMedidor(s: Long) {
        if (medidor?.isActive == true) return
        medidor = noTrabalho(s) {
            while (true) {
                delay(INTERVALO_FALA_MS)
                medirFala()
            }
        }
    }

    /** Volume de cada um pelas estatísticas do WebRTC (`audioLevel`); só republica quando alguém começa ou para. */
    private suspend fun medirFala() {
        var mudou = false
        for (a in assinaturas.values.toList()) {
            val pc = a.pc ?: continue
            val falando = a.fala.medir(nivelDeAudio(pc, "inbound-rtp"), SystemClock.elapsedRealtime())
            if (falando != a.falando && assinaturas[a.usuario] === a) {
                a.falando = falando
                mudou = true
            }
        }
        val pub = publicacao
        val eu = if (pub != null) falaLocal.medir(nivelDeAudio(pub.pc, "media-source"), SystemClock.elapsedRealtime()) else false
        if (eu != falandoLocal) {
            falandoLocal = eu
            mudou = true
        }
        if (mudou) publicarTrilhas()
    }

    /** Maior `audioLevel` (0 a 1) das estatísticas de áudio do tipo pedido; 0 se não vier em 1 s. */
    private suspend fun nivelDeAudio(pc: PeerConnection, tipo: String): Double = withTimeoutOrNull(ESPERA_ESTATISTICAS_MS) {
        suspendCancellableCoroutine { continuacao ->
            pc.getStats { relatorio ->
                val nivel = relatorio.statsMap.values
                    .filter { it.type == tipo && it.members["kind"] == "audio" }
                    .maxOfOrNull { (it.members["audioLevel"] as? Number)?.toDouble() ?: 0.0 } ?: 0.0
                continuacao.resume(nivel)
            }
        }
    } ?: 0.0

    private fun aoMudarAssinatura(a: Assinatura, estado: PeerConnection.PeerConnectionState, s: Long) {
        if (s != sessaoAtual() || assinaturas[a.usuario] !== a) return
        a.estado = estado
        when (estado) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                a.falhou = false
                publicarTrilhas()
                // Como o web: conectou e o vídeo não chegou em 5 s → refazer.
                if (a.esperaVideo) {
                    noTrabalho(s) {
                        delay(ESPERA_VIDEO_MS)
                        if (assinaturas[a.usuario] === a && a.video == null) assinar(a.usuario, s)
                    }
                }
            }
            // ICE falhou: refaz só esta conexão.
            PeerConnection.PeerConnectionState.FAILED -> refazer(a, s)
            PeerConnection.PeerConnectionState.DISCONNECTED -> refazer(a, s)
            else -> Unit
        }
    }

    private fun refazer(a: Assinatura, s: Long) {
        noTrabalho(s) {
            delay(ESPERA_RECONEXAO_MS)
            if (assinaturas[a.usuario] !== a || a.estado == PeerConnection.PeerConnectionState.CONNECTED) return@noTrabalho
            a.falhou = true
            publicarTrilhas()
            assinar(a.usuario, s)
        }
    }

    private fun aoReceberTrilha(a: Assinatura, trilha: MediaStreamTrack, s: Long) {
        if (s != sessaoAtual() || assinaturas[a.usuario] !== a) return
        // O áudio remoto toca sozinho (módulo de áudio do WebRTC); o vídeo vai para a tela,
        // mas só se o MediaMTX manda vídeo: quem publica só áudio fica com o avatar, não um quadro preto.
        if (trilha is VideoTrack && a.esperaVideo) {
            a.video = trilha
            publicarTrilhas()
        }
    }

    private fun soltar(usuario: Long) {
        assinaturas.remove(usuario)?.let(::descartarAssinatura)
        publicarTrilhas()
    }

    // --- Encerrar ---

    private fun desmontar() {
        _trilhas.value = TrilhasChamada()
        assinaturas.values.forEach(::descartarAssinatura)
        assinaturas.clear()
        publicacao?.let(::descartarPublicacao)
        publicacao = null
        local?.let(::liberarLocal)
        local = null
        comVideo = false
        republicando = false
        medidor = null
        falaLocal = DetectorDeFala()
        falandoLocal = false
    }

    private fun descartarAssinatura(a: Assinatura) {
        a.tarefa?.cancel()
        a.recurso?.let(::excluirDepois)
        a.video = null
        a.pc?.dispose()
        a.pc = null
    }

    private fun descartarPublicacao(p: Publicacao) {
        p.recurso?.let(::excluirDepois)
        p.pc.dispose()
    }

    /** Depois do `dispose` das conexões: trilhas, fontes, câmera (#37). */
    private fun liberarLocal(l: Local) {
        l.quadrosPretos?.cancel()
        if (l.capturando) pararCaptura(l)
        l.capturador?.dispose()
        l.video?.dispose()
        l.fonteVideo?.dispose()
        l.ajudante?.dispose()
        l.audio?.dispose()
        l.fonteAudio?.dispose()
    }

    /** `DELETE` fora da vez e fora da sessão: termina mesmo depois de encerrar. */
    private fun excluirDepois(recurso: HttpUrl) {
        escopoApp.launch { whip.encerrar(recurso) }
    }

    // --- Utilidades ---

    private suspend fun buscarIce(): ServidoresIce =
        chamarApi { api.ice() }.map { it.paraModelo() }.getOrElse { ServidoresIce(emptyList(), somenteRelay = false) }

    private fun configuracao(ice: ServidoresIce): PeerConnection.RTCConfiguration {
        val servidores = ice.servidores.filter { it.urls.isNotEmpty() }.map { servidor ->
            PeerConnection.IceServer.builder(servidor.urls).apply {
                servidor.usuario?.let(::setUsername)
                servidor.credencial?.let(::setPassword)
            }.createIceServer()
        }
        return PeerConnection.RTCConfiguration(servidores).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            // O MediaMTX só é alcançável pelo coturn (contrato §9.5).
            if (ice.somenteRelay && servidores.isNotEmpty()) iceTransportsType = PeerConnection.IceTransportsType.RELAY
        }
    }

    private fun publicarTrilhas() {
        _trilhas.value = TrilhasChamada(
            videoLocal = local?.video,
            remotos = assinaturas.mapValues { (_, a) ->
                TrilhaRemota(a.video, a.estado == PeerConnection.PeerConnectionState.CONNECTED, a.falhou, a.falando)
            },
            falandoLocal = falandoLocal,
        )
    }

    private fun sessaoAtual() = synchronized(trava) { sessao }

    private fun checar(s: Long) {
        if (s != sessaoAtual()) throw CancellationException("Chamada encerrada")
    }

    private fun checar(s: Long, pub: Publicacao) {
        checar(s)
        if (publicacao !== pub) throw CancellationException("Publicação substituída")
    }

    private fun checar(s: Long, a: Assinatura) {
        checar(s)
        if (assinaturas[a.usuario] !== a) throw CancellationException("Assinatura substituída")
    }

    /** Roda na [vez], como tarefa da sessão de agora: encerrar cancela. */
    private suspend fun <T> naVez(bloco: suspend CoroutineScope.(Long) -> T): T {
        val (s, dono) = synchronized(trava) { sessao to trabalhos }
        return escopo.async(dono) {
            checar(s)
            bloco(s)
        }.await()
    }

    private fun naVezSemEsperar(bloco: () -> Unit) {
        val s = sessaoAtual()
        escopo.launch { if (s == sessaoAtual()) bloco() }
    }

    private fun noTrabalho(s: Long, bloco: suspend CoroutineScope.() -> Unit): Job? {
        val (atual, dono) = synchronized(trava) { sessao to trabalhos }
        if (atual != s) return null
        return escopo.launch(dono, block = bloco)
    }

    private companion object {
        const val STREAM = "conversa"

        // Padrão do web para celular: 360p a 15 fps; áudio "normal" a 32 kbps.
        const val LARGURA = 640
        const val ALTURA = 360
        const val QUADROS = 15
        const val BITRATE_AUDIO = 32_000

        // Quadro preto (câmera desligada): pequeno e 2 por segundo bastam.
        const val LARGURA_PRETO = 320
        const val ALTURA_PRETO = 180
        const val INTERVALO_PRETO_MS = 500L
        const val PRETO_Y: Byte = 16
        const val PRETO_UV: Byte = -128

        const val TENTATIVAS_VIDEO = 40
        const val INTERVALO_VIDEO_MS = 1_000L
        const val TENTATIVAS_AUDIO = 12
        const val INTERVALO_AUDIO_MS = 800L
        const val ESPERA_RECONEXAO_MS = 2_000L
        const val ESPERA_VIDEO_MS = 5_000L

        // O web mede a cada 100 ms; aqui as estatísticas custam mais, e 150 ms com a espera de 400 ms não pisca.
        const val INTERVALO_FALA_MS = 150L
        const val ESPERA_ESTATISTICAS_MS = 1_000L
    }
}

/**
 * A resposta do MediaMTX manda vídeo? (`m=video` com porta ≠ 0 e sem `recvonly`/`inactive`).
 * Quem publica só áudio não manda: aí não adianta esperar a trilha de vídeo.
 */
internal fun sdpEnviaVideo(sdp: String): Boolean {
    var dentroDoVideo = false
    var envia = false
    for (linha in sdp.lineSequence().map { it.trim() }) {
        if (linha.startsWith("m=")) {
            if (dentroDoVideo && envia) return true
            val partes = linha.removePrefix("m=").split(' ')
            dentroDoVideo = partes.firstOrNull() == "video" && partes.getOrNull(1) != "0"
            envia = dentroDoVideo
        } else if (dentroDoVideo && (linha == "a=recvonly" || linha == "a=inactive")) {
            envia = false
        }
    }
    return dentroDoVideo && envia
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WebRtcModulo {
    @Binds
    abstract fun midiaChamada(midia: MidiaWebRtc): MidiaChamada
}
