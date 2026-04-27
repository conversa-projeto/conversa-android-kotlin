package com.conversa.conversa.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.RtpTransceiver.RtpTransceiverInit
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebRTCManager — WebRTC nativo em mesh, paridade com conversa-web/src/stores/call.ts.
 * - 1 txPc: publica minha midia via WHIP (path: call-{id}-u-{myId})
 * - N rxPcs: assina midia de cada peer via WHEP (path: call-{id}-u-{peerId})
 * Reconexao automatica em falha de ICE reescreve o peer afetado.
 */
class WebRTCManager(
    private val context: Context,
    private val mediaMtxBase: String,
    private val stunUrl: String,
) {
    companion object { private const val TAG = "WebRTCManager" }

    data class PeerRemoto(
        val usuarioId: Int,
        val pc: PeerConnection,
        var videoTrack: VideoTrack? = null,
        var audioTrack: AudioTrack? = null,
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mutex = Mutex()

    private val eglBase: EglBase by lazy { EglBase.create() }
    val eglBaseContext: EglBase.Context get() = eglBase.eglBaseContext

    private var factory: PeerConnectionFactory? = null
    private val whipWhep = WhipWhepClient()

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var txPc: PeerConnection? = null
    private val rxPcs = mutableMapOf<Int, PeerRemoto>()

    // chamadaId/meuUid atuais (para reconexoes re-executarem WHIP/WHEP)
    private var chamadaIdAtual: Int = 0
    private var meuUsuarioIdAtual: Int = 0

    // Jobs pendentes de reconexao por peerId (rx) ou null (tx)
    private val reconnectJobs = mutableMapOf<String, Job>()

    private val _peers = MutableStateFlow<Map<Int, PeerRemoto>>(emptyMap())
    val peers: StateFlow<Map<Int, PeerRemoto>> = _peers.asStateFlow()

    private val _localVideoTrackFlow = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrackFlow: StateFlow<VideoTrack?> = _localVideoTrackFlow.asStateFlow()

    var onPeerAdicionado: ((PeerRemoto) -> Unit)? = null
    var onPeerRemovido: ((Int) -> Unit)? = null
    var onErro: ((String) -> Unit)? = null

    fun inicializar() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        val encFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encFactory)
            .setVideoDecoderFactory(decFactory)
            .createPeerConnectionFactory()
        Log.d(TAG, "PeerConnectionFactory inicializado")
    }

    suspend fun adquirirMidiaLocal(comVideo: Boolean = false) = mutex.withLock {
        adquirirMidiaLocalInterno(comVideo)
    }

    private fun adquirirMidiaLocalInterno(comVideo: Boolean) {
        val f = factory ?: error("inicializar() nao foi chamado")

        if (localAudioTrack == null) {
            val c = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            }
            audioSource = f.createAudioSource(c)
            localAudioTrack = f.createAudioTrack("audio-local", audioSource).apply { setEnabled(true) }
        }

        if (comVideo && localVideoTrack == null) {
            val enumerator = Camera2Enumerator(context)
            val frontal = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: error("Nenhuma camera disponivel")
            videoCapturer = enumerator.createCapturer(frontal, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("CapturerThread", eglBase.eglBaseContext)
            videoSource = f.createVideoSource(videoCapturer!!.isScreencast)
            videoCapturer!!.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
            videoCapturer!!.startCapture(640, 480, 24)
            localVideoTrack = f.createVideoTrack("video-local", videoSource).apply { setEnabled(true) }
            _localVideoTrackFlow.value = localVideoTrack
        }

        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            isSpeakerphoneOn = false
        }
    }

    suspend fun publicarLocalNaSala(chamadaId: Int, meuUsuarioId: Int) = mutex.withLock {
        publicarLocalInterno(chamadaId, meuUsuarioId)
    }

    private suspend fun publicarLocalInterno(chamadaId: Int, meuUsuarioId: Int) {
        val f = factory ?: error("inicializar() nao foi chamado")
        chamadaIdAtual = chamadaId
        meuUsuarioIdAtual = meuUsuarioId

        try { txPc?.close() } catch (_: Exception) {}
        txPc = null

        val pc = f.createPeerConnection(montarRtcConfig(), txObserver) ?: error("createPeerConnection null")
        localAudioTrack?.let { pc.addTrack(it, listOf("stream-local")) }
        localVideoTrack?.let { pc.addTrack(it, listOf("stream-local")) }

        val offer = pc.createOfferAwait()
        pc.setLocalDescriptionAwait(offer)
        esperarICE(pc, timeoutMs = 3000L)

        val url = "$mediaMtxBase/${pathSala(chamadaId, meuUsuarioId)}/whip"
        Log.d(TAG, "WHIP POST $url")
        val answerSdp = whipWhep.postWhip(url, pc.localDescription!!.description)
        pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))

        txPc = pc
        Log.d(TAG, "Publicacao local estabelecida")
    }

    suspend fun assinarDePeer(chamadaId: Int, peerId: Int) = mutex.withLock {
        assinarDePeerInterno(chamadaId, peerId)
    }

    private suspend fun assinarDePeerInterno(chamadaId: Int, peerId: Int) {
        if (rxPcs.containsKey(peerId)) return
        chamadaIdAtual = chamadaId
        val f = factory ?: error("inicializar() nao foi chamado")

        val pc = f.createPeerConnection(montarRtcConfig(), rxObserver(peerId)) ?: error("createPeerConnection null")
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        pc.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))

        val offer = pc.createOfferAwait()
        pc.setLocalDescriptionAwait(offer)
        esperarICE(pc, timeoutMs = 3000L)

        val url = "$mediaMtxBase/${pathSala(chamadaId, peerId)}/whep"
        Log.d(TAG, "WHEP POST $url")
        // Paridade com web: 40 tentativas @ 1s (video); 12 @ 800ms (audio).
        // O subscritor nao sabe se o peer publicou video, entao usamos parametros de video (limite superior).
        val answerSdp = whipWhep.postWhepWithRetry(
            url = url,
            offerSdp = pc.localDescription!!.description,
            maxAttempts = 40,
            delayMs = 1000L,
        )
        pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))

        val peer = PeerRemoto(peerId, pc)
        rxPcs[peerId] = peer
        _peers.value = rxPcs.toMap()
        onPeerAdicionado?.invoke(peer)
    }

    suspend fun desconectarPeer(peerId: Int) = mutex.withLock {
        reconnectJobs.remove("rx-$peerId")?.cancel()
        val peer = rxPcs.remove(peerId) ?: return@withLock
        try { peer.pc.close() } catch (_: Exception) {}
        _peers.value = rxPcs.toMap()
        onPeerRemovido?.invoke(peerId)
    }

    fun alternarMicrofone(mutado: Boolean) { localAudioTrack?.setEnabled(!mutado) }

    fun alternarCamera(mutada: Boolean) { localVideoTrack?.setEnabled(!mutada) }

    /**
     * Liga/desliga video local. Quando liga e ainda nao ha track, chama getUserMedia,
     * adiciona ao txPc, renegocia (nova offer, re-POST WHIP).
     */
    suspend fun alternarVideo(ativar: Boolean) = mutex.withLock {
        if (!ativar) {
            localVideoTrack?.setEnabled(false)
            return@withLock
        }

        if (localVideoTrack != null) {
            localVideoTrack?.setEnabled(true)
            return@withLock
        }

        adquirirMidiaLocalInterno(comVideo = true)

        val pc = txPc
        val video = localVideoTrack
        if (pc != null && video != null) {
            pc.addTrack(video, listOf("stream-local"))
            val offer = pc.createOfferAwait()
            pc.setLocalDescriptionAwait(offer)
            esperarICE(pc, timeoutMs = 3000L)
            val url = "$mediaMtxBase/${pathSala(chamadaIdAtual, meuUsuarioIdAtual)}/whip"
            Log.d(TAG, "WHIP re-POST (upgrade video) $url")
            val answer = whipWhep.postWhip(url, pc.localDescription!!.description)
            pc.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answer))
        }
    }

    fun trocarCamera() { (videoCapturer as? CameraVideoCapturer)?.switchCamera(null) }

    fun alternarAltoFalante(ligado: Boolean) {
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.apply {
            mode = AudioManager.MODE_IN_COMMUNICATION
            isSpeakerphoneOn = ligado
        }
    }

    suspend fun desligar() = mutex.withLock {
        Log.d(TAG, "Desligando WebRTCManager")

        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()

        try { txPc?.close() } catch (_: Exception) {}
        txPc = null

        rxPcs.values.forEach { try { it.pc.close() } catch (_: Exception) {} }
        rxPcs.clear()
        _peers.value = emptyMap()

        try { videoCapturer?.stopCapture() } catch (_: Exception) {}
        videoCapturer?.dispose(); videoCapturer = null
        surfaceTextureHelper?.dispose(); surfaceTextureHelper = null

        videoSource?.dispose(); videoSource = null
        localVideoTrack = null
        _localVideoTrackFlow.value = null

        audioSource?.dispose(); audioSource = null
        localAudioTrack = null

        chamadaIdAtual = 0
        meuUsuarioIdAtual = 0
    }

    /**
     * Libera tudo — chamar de Service.onDestroy. Usa runBlocking(Dispatchers.IO)
     * para nao bloquear a main thread (onDestroy eh chamado na main).
     */
    fun cleanup() {
        runBlocking(Dispatchers.IO) { desligar() }
        scope.cancel()
        factory?.dispose(); factory = null
        try { eglBase.release() } catch (_: Exception) {}
    }

    // ─── reconexao por falha de ICE ──────────────────────────────────────────

    private fun agendarReconexaoRx(peerId: Int) {
        val key = "rx-$peerId"
        if (reconnectJobs[key]?.isActive == true) return
        reconnectJobs[key] = scope.launch {
            delay(2000L)
            if (!rxPcs.containsKey(peerId)) return@launch
            Log.w(TAG, "Reconectando rx peer $peerId")
            mutex.withLock {
                val existing = rxPcs.remove(peerId)
                try { existing?.pc?.close() } catch (_: Exception) {}
                _peers.value = rxPcs.toMap()
            }
            try {
                assinarDePeer(chamadaIdAtual, peerId)
            } catch (e: Exception) {
                Log.e(TAG, "Falha reconexao rx $peerId: ${e.message}")
            }
        }
    }

    private fun cancelarReconexaoRx(peerId: Int) {
        reconnectJobs.remove("rx-$peerId")?.cancel()
    }

    private fun agendarReconexaoTx() {
        val key = "tx"
        if (reconnectJobs[key]?.isActive == true) return
        if (chamadaIdAtual == 0 || meuUsuarioIdAtual == 0) return
        reconnectJobs[key] = scope.launch {
            delay(2000L)
            Log.w(TAG, "Reconectando tx")
            try {
                publicarLocalNaSala(chamadaIdAtual, meuUsuarioIdAtual)
            } catch (e: Exception) {
                Log.e(TAG, "Falha reconexao tx: ${e.message}")
            }
        }
    }

    private fun cancelarReconexaoTx() {
        reconnectJobs.remove("tx")?.cancel()
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private fun montarRtcConfig(): PeerConnection.RTCConfiguration {
        val servers = listOf(PeerConnection.IceServer.builder(stunUrl).createIceServer())
        return PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
    }

    private fun pathSala(chamadaId: Int, userId: Int): String =
        "call-$chamadaId-u-$userId".replace(Regex("[^a-zA-Z0-9_-]"), "")

    private val txObserver = object : BasePcObserver("tx") {
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            super.onIceConnectionChange(state)
            when (state) {
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.DISCONNECTED -> agendarReconexaoTx()
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> cancelarReconexaoTx()
                else -> {}
            }
        }
    }

    private fun rxObserver(peerId: Int) = object : BasePcObserver("rx-$peerId") {
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            val track = receiver?.track() ?: return
            val peer = rxPcs[peerId] ?: return
            when (track) {
                is VideoTrack -> {
                    peer.videoTrack = track
                    _peers.value = rxPcs.toMap()
                }
                is AudioTrack -> {
                    peer.audioTrack = track
                    track.setEnabled(true)
                }
            }
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            super.onIceConnectionChange(state)
            when (state) {
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.DISCONNECTED -> agendarReconexaoRx(peerId)
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> cancelarReconexaoRx(peerId)
                else -> {}
            }
        }
    }

    private abstract class BasePcObserver(private val tag: String) : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d("WebRTCManager", "$tag iceConn=$state")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        override fun onConnectionChange(state: PeerConnection.PeerConnectionState?) {}
    }

    private suspend fun esperarICE(pc: PeerConnection, timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            while (pc.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
                delay(50)
            }
        }
    }
}

// ─── Extensions async para WebRTC ─────────────────────────────────────────────

private suspend fun PeerConnection.createOfferAwait(
    constraints: MediaConstraints = MediaConstraints(),
): SessionDescription = suspendCancellableCoroutine { cont ->
    createOffer(object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) { cont.resume(sdp) }
        override fun onCreateFailure(err: String?) { cont.resumeWithException(RuntimeException("createOffer: $err")) }
        override fun onSetSuccess() {}
        override fun onSetFailure(err: String?) {}
    }, constraints)
}

private suspend fun PeerConnection.setLocalDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setLocalDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(err: String?) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(err: String?) { cont.resumeWithException(RuntimeException("setLocal: $err")) }
        }, sdp)
    }

private suspend fun PeerConnection.setRemoteDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine<Unit> { cont ->
        setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {}
            override fun onCreateFailure(err: String?) {}
            override fun onSetSuccess() { cont.resume(Unit) }
            override fun onSetFailure(err: String?) { cont.resumeWithException(RuntimeException("setRemote: $err")) }
        }, sdp)
    }
