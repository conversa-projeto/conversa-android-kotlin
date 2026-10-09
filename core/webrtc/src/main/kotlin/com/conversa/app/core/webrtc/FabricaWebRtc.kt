package com.conversa.app.core.webrtc

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/**
 * O que o módulo de áudio do aparelho faz (8.5): eco e ruído pelo hardware, quando houver,
 * e microfone e saída em estéreo (qualidade "Música"). Só se escolhe ao criar a fábrica.
 */
data class AudioDoAparelho(val eco: Boolean = true, val ruido: Boolean = true, val estereo: Boolean = false)

/**
 * `PeerConnectionFactory` e `EglBase` únicos por processo, criados na primeira chamada
 * (plano §3.5). A fábrica é refeita entre chamadas quando o [AudioDoAparelho] pedido muda.
 */
@Singleton
class FabricaWebRtc @Inject constructor(@ApplicationContext private val contexto: Context) {
    val egl: EglBase by lazy { EglBase.create() }

    private val inicializada by lazy {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(contexto).createInitializationOptions())
    }
    private var criada: Pair<AudioDoAparelho, PeerConnectionFactory>? = null
    private var pedido = AudioDoAparelho()

    val fabrica: PeerConnectionFactory
        get() = synchronized(this) { criada?.second ?: criar(pedido) }

    /**
     * O áudio do aparelho da próxima chamada. Só com nada vivo (nem trilha nem conexão):
     * se mudou, a fábrica atual é descartada e a próxima [fabrica] sai com o pedido.
     */
    fun prepararAudio(audio: AudioDoAparelho) = synchronized(this) {
        pedido = audio
        val atual = criada ?: return@synchronized
        if (atual.first != audio) {
            atual.second.dispose()
            criada = null
        }
    }

    private fun criar(audio: AudioDoAparelho): PeerConnectionFactory {
        inicializada
        val modulo = JavaAudioDeviceModule.builder(contexto)
            .setUseHardwareAcousticEchoCanceler(audio.eco)
            .setUseHardwareNoiseSuppressor(audio.ruido)
            .setUseStereoInput(audio.estereo)
            .setUseStereoOutput(audio.estereo)
            .createAudioDeviceModule()
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(modulo)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
            // A fábrica guarda a própria referência ao módulo de áudio.
            .also { modulo.release() }
            .also { criada = audio to it }
    }
}

/** H264 e VP9 primeiro: o MediaMTX não grava VP8, o padrão do WebRTC (contrato §9.10). O resto fica na ordem. */
internal fun <T> ordenarGravaveis(codecs: List<T>, mime: (T) -> String): List<T> = codecs.sortedBy { codec ->
    GRAVAVEIS.indexOfFirst { it.equals(mime(codec), ignoreCase = true) }.let { if (it < 0) GRAVAVEIS.size else it }
}

private val GRAVAVEIS = listOf("video/H264", "video/VP9")

/** Observa uma `PeerConnection`: coleta de ICE, estado da conexão e trilhas recebidas. */
internal class Observador(
    private val aoMudarConexao: (PeerConnection.PeerConnectionState) -> Unit = {},
    private val aoReceberTrilha: (MediaStreamTrack) -> Unit = {},
) : PeerConnection.Observer {
    private val coleta = MutableStateFlow(PeerConnection.IceGatheringState.NEW)

    /** Sem trickle ICE: espera a coleta terminar, até [ESPERA_ICE_MS] (a alocação do TURN sobre TLS passa de 3 s). */
    suspend fun esperarColeta() {
        withTimeoutOrNull(ESPERA_ICE_MS) { coleta.first { it == PeerConnection.IceGatheringState.COMPLETE } }
    }

    override fun onIceGatheringChange(estado: PeerConnection.IceGatheringState) {
        coleta.value = estado
    }

    override fun onConnectionChange(estado: PeerConnection.PeerConnectionState) = aoMudarConexao(estado)

    override fun onTrack(transceiver: RtpTransceiver) {
        transceiver.receiver.track()?.let(aoReceberTrilha)
    }

    override fun onSignalingChange(estado: PeerConnection.SignalingState) = Unit

    override fun onIceConnectionChange(estado: PeerConnection.IceConnectionState) = Unit

    override fun onIceConnectionReceivingChange(recebendo: Boolean) = Unit

    override fun onIceCandidate(candidato: IceCandidate) = Unit

    override fun onIceCandidatesRemoved(candidatos: Array<out IceCandidate>) = Unit

    override fun onAddStream(stream: MediaStream) = Unit

    override fun onRemoveStream(stream: MediaStream) = Unit

    override fun onDataChannel(canal: DataChannel) = Unit

    override fun onRenegotiationNeeded() = Unit

    companion object {
        const val ESPERA_ICE_MS = 5_000L
    }
}

internal suspend fun PeerConnection.criarOferta(): SessionDescription = suspendCancellableCoroutine { continuacao ->
    createOffer(
        object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = continuacao.resume(sdp)

            override fun onCreateFailure(erro: String?) = continuacao.resumeWithException(IllegalStateException("createOffer: $erro"))

            override fun onSetSuccess() = Unit

            override fun onSetFailure(erro: String?) = Unit
        },
        MediaConstraints(),
    )
}

internal suspend fun PeerConnection.definirLocal(sdp: SessionDescription) = definir { setLocalDescription(it, sdp) }

internal suspend fun PeerConnection.definirRemota(sdp: SessionDescription) = definir { setRemoteDescription(it, sdp) }

private suspend fun definir(acao: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { continuacao ->
    acao(
        object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = Unit

            override fun onCreateFailure(erro: String?) = Unit

            override fun onSetSuccess() = continuacao.resume(Unit)

            override fun onSetFailure(erro: String?) = continuacao.resumeWithException(IllegalStateException("setDescription: $erro"))
        },
    )
}
