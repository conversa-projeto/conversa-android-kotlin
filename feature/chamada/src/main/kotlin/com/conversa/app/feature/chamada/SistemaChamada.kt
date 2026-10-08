package com.conversa.app.feature.chamada

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.DisconnectCause
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.notificacoes.CanaisNotificacao
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.media.PlayerAudio
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.webrtc.MidiaChamada
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Liga a chamada ao sistema (TODO 6.3, 6.5): observa o [GerenciadorChamadas] e cuida
 * do toque, da notificação de chamada recebida, do serviço em primeiro plano, do
 * Telecom e de parar o áudio de mensagem. O gerenciador não sabe de nada disso.
 */
@Singleton
class IntegracaoChamada @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val gerenciador: GerenciadorChamadas,
    private val tom: TomDeChamada,
    private val notificacoes: NotificacoesChamada,
    private val telecom: TelecomChamadas,
    private val player: PlayerAudio,
    private val primeiroPlano: MonitorPrimeiroPlano,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        telecom.iniciar()
        escopo.launch(Dispatchers.Main) {
            var antes = EstadoChamada()
            gerenciador.estado.collect { agora ->
                reagir(antes, agora)
                antes = agora
            }
        }
        // A notificação de chamada recebida só aparece com o app fora da frente; na frente, a tela já abre sozinha.
        escopo.launch(Dispatchers.Main) {
            combine(gerenciador.estado, primeiroPlano.emPrimeiroPlano) { estado, naFrente -> estado.takeIf { it.tocando && !naFrente } }
                .distinctUntilChanged { a, b -> a?.chamadaId == b?.chamadaId && a?.dados == b?.dados }
                .collect { estado -> if (estado != null) notificacoes.mostrarRecebida(estado) else notificacoes.cancelarRecebida() }
        }
    }

    private fun reagir(antes: EstadoChamada, agora: EstadoChamada) {
        // O toque só para ao atender, recusar, encerrar remoto ou no tempo (#7): tudo isso tira a fase de "tocando".
        if (agora.tocando) tom.tocar() else tom.pararToque()
        if (agora.fase == FaseChamada.CHAMANDO) tom.tocarChamando() else tom.pararChamando()

        val emCurso = setOf(FaseChamada.CHAMANDO, FaseChamada.CONECTANDO, FaseChamada.ATIVA)
        if (agora.fase in emCurso && antes.fase !in emCurso) ServicoChamada.iniciar(contexto)
        // Nenhum áudio de mensagem durante a chamada (6.8).
        if (antes.fase == FaseChamada.INATIVO && agora.fase != FaseChamada.INATIVO) player.parar()

        // No Telecom quando já há nome: a efetuada quando o servidor criou a chamada; a recebida quando começa a tocar.
        val comecouEfetuada = agora.fase == FaseChamada.CHAMANDO && agora.dados != null && antes.dados == null
        val comecouRecebida = agora.tocando && !antes.tocando
        if (comecouEfetuada || comecouRecebida) {
            telecom.registrar(notificacoes.nomeDaChamada(agora), entrada = comecouRecebida, video = agora.tipo == TipoChamada.VIDEO)
        }
        if (agora.fase == FaseChamada.CONECTANDO && antes.fase == FaseChamada.RECEBENDO) telecom.atender(agora.tipo == TipoChamada.VIDEO)
        if (agora.fase == FaseChamada.ATIVA && antes.fase != FaseChamada.ATIVA) telecom.ativar()
        if (agora.tipo == TipoChamada.VIDEO &&
            antes.tipo == TipoChamada.AUDIO &&
            agora.fase == FaseChamada.ATIVA
        ) {
            telecom.preferirAltoFalante()
        }
        if (agora.fase == FaseChamada.INATIVO && antes.fase != FaseChamada.INATIVO) telecom.encerrar()
    }
}

/** Toque da chamada recebida (som e vibração conforme o modo da campainha) e o "chamando" de quem liga. */
@Singleton
class TomDeChamada @Inject constructor(@ApplicationContext private val contexto: Context) {
    private var toque: Ringtone? = null
    private var vibrador: Vibrator? = null
    private var chamando: ToneGenerator? = null

    fun tocar() {
        if (toque != null || vibrador != null) return
        val modo = contexto.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (modo == AudioManager.RINGER_MODE_NORMAL) {
            toque = try {
                RingtoneManager.getRingtone(contexto, Settings.System.DEFAULT_RINGTONE_URI)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    isLooping = true
                    play()
                }
            } catch (e: RuntimeException) {
                Timber.w("Toque indisponível: %s", e.javaClass.simpleName)
                null
            }
        }
        if (modo != AudioManager.RINGER_MODE_SILENT) vibrar()
    }

    fun pararToque() {
        toque?.stop()
        toque = null
        vibrador?.cancel()
        vibrador = null
    }

    /** Tom de "chamando" (tu… tu…) enquanto ninguém atende. */
    fun tocarChamando() {
        if (chamando != null) return
        chamando = try {
            ToneGenerator(AudioManager.STREAM_VOICE_CALL, VOLUME_CHAMANDO).apply { startTone(ToneGenerator.TONE_SUP_RINGTONE) }
        } catch (e: RuntimeException) {
            Timber.w("Tom de chamando indisponível: %s", e.javaClass.simpleName)
            null
        }
    }

    fun pararChamando() {
        chamando?.stopTone()
        chamando?.release()
        chamando = null
    }

    private fun vibrar() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contexto.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            contexto.getSystemService(Vibrator::class.java)
        } ?: return
        if (!v.hasVibrator()) return
        val padrao = VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(padrao, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(padrao, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
        }
        vibrador = v
    }

    private companion object {
        const val VOLUME_CHAMANDO = 80
    }
}

/** Notificações CallStyle: a chamada recebida (tela cheia) e a chamada em andamento (do serviço). */
@Singleton
class NotificacoesChamada @Inject constructor(@ApplicationContext private val contexto: Context, private val sessao: SessaoRepositorio) {
    private val gerente = NotificationManagerCompat.from(contexto)

    /** Tocando: quem ligou. Nos outros casos: os outros participantes. */
    fun nomeDaChamada(estado: EstadoChamada): String {
        val eu = sessao.sessao.value?.usuarioId
        val participantes = estado.dados?.participantes.orEmpty()
        val nomes = if (estado.fase == FaseChamada.RECEBENDO) {
            participantes.filter { it.usuarioId == estado.remetenteId }
        } else {
            participantes.filter { it.usuarioId != eu }
        }
        return nomes.joinToString(", ") { it.nome }.ifBlank { contexto.getString(R.string.alguem) }
    }

    fun mostrarRecebida(estado: EstadoChamada) {
        if (!gerente.areNotificationsEnabled()) return
        val nome = nomeDaChamada(estado)
        val telaCheia = PendingIntent.getActivity(contexto, PEDIDO_TELA, ChamadaActivity.intencao(contexto), FLAGS)
        // Atender abre a tela direto (nunca broadcast → activity, #3 do legado).
        val atender = PendingIntent.getActivity(
            contexto,
            PEDIDO_ATENDER,
            ChamadaActivity.intencao(contexto).setAction(ChamadaActivity.ACAO_ATENDER),
            FLAGS,
        )
        val recusar = PendingIntent.getBroadcast(
            contexto,
            PEDIDO_RECUSAR,
            AcoesChamadaReceiver.intencao(contexto, AcoesChamadaReceiver.ACAO_RECUSAR),
            FLAGS,
        )
        val notificacao = NotificationCompat.Builder(contexto, CanaisNotificacao.CHAMADAS_RECEBIDAS)
            .setSmallIcon(R.drawable.ic_chamada)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(pessoa(nome), recusar, atender).setIsVideo(
                    estado.tipo == TipoChamada.VIDEO,
                ),
            )
            .setContentText(contexto.getString(if (estado.tipo == TipoChamada.VIDEO) R.string.video_e_audio else R.string.somente_audio))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(telaCheia, true)
            .setContentIntent(telaCheia)
            .setOngoing(true)
            .build()
        try {
            gerente.notify(ID_RECEBIDA, notificacao)
        } catch (e: SecurityException) {
            Timber.w("Sem permissão para notificar")
        }
    }

    fun cancelarRecebida() = gerente.cancel(ID_RECEBIDA)

    /** Chamando ou em chamada, com o cronômetro nativo e "Desligar" (do serviço em primeiro plano). */
    fun emAndamento(estado: EstadoChamada): Notification {
        val nomes = nomeDaChamada(estado)
        val abrir = PendingIntent.getActivity(contexto, PEDIDO_TELA, ChamadaActivity.intencao(contexto), FLAGS)
        val desligar = PendingIntent.getBroadcast(
            contexto,
            PEDIDO_DESLIGAR,
            AcoesChamadaReceiver.intencao(contexto, AcoesChamadaReceiver.ACAO_DESLIGAR),
            FLAGS,
        )
        val status = contexto.getString(
            when (estado.fase) {
                FaseChamada.CHAMANDO -> R.string.chamando
                FaseChamada.CONECTANDO -> R.string.conectando
                FaseChamada.ENCERRANDO -> R.string.encerrando
                else -> R.string.em_chamada
            },
        )
        return NotificationCompat.Builder(contexto, CanaisNotificacao.CHAMADA_ATIVA)
            .setSmallIcon(R.drawable.ic_chamada)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(pessoa(nomes), desligar).setIsVideo(estado.tipo == TipoChamada.VIDEO))
            .setContentText(status)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(abrir)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                estado.ativaDesde?.let {
                    setUsesChronometer(true)
                    setWhen(it.toEpochMilli())
                    setShowWhen(true)
                }
            }
            .build()
    }

    private fun pessoa(nome: String) = Person.Builder().setName(nome).setImportant(true).build()

    companion object {
        const val ID_RECEBIDA = 7001
        const val ID_EM_ANDAMENTO = 7002
        private const val PEDIDO_TELA = 1
        private const val PEDIDO_ATENDER = 2
        private const val PEDIDO_RECUSAR = 3
        private const val PEDIDO_DESLIGAR = 4
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

/**
 * A chamada no Telecom (Core-Telecom, TODO 6.3): atender e desligar pelo fone Bluetooth,
 * pelo carro ou pelo relógio; chamada GSM concorrente põe a nossa em espera; as rotas
 * de áudio (fone, alto-falante, Bluetooth) vêm daqui (6.8). Sem o Telecom (falhou o
 * registro), a chamada segue igual, só sem essas integrações.
 */
@Singleton
class TelecomChamadas @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val gerenciador: GerenciadorChamadas,
    private val midia: MidiaChamada,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private sealed interface Comando {
        data class Atender(val video: Boolean) : Comando

        data object Ativar : Comando

        data object Encerrar : Comando

        data object AltoFalante : Comando

        data class Rota(val rota: CallEndpointCompat) : Comando
    }

    private val gerente by lazy { CallsManager(contexto) }
    private var registrado = false
    private var chamada: Job? = null
    private var comandos: Channel<Comando>? = null

    private val _rotas = MutableStateFlow<List<CallEndpointCompat>>(emptyList())
    val rotas: StateFlow<List<CallEndpointCompat>> = _rotas.asStateFlow()
    private val _rotaAtual = MutableStateFlow<CallEndpointCompat?>(null)
    val rotaAtual: StateFlow<CallEndpointCompat?> = _rotaAtual.asStateFlow()

    /** Posta em espera pelo sistema (ex.: atendeu uma chamada de celular). O Telecom não retoma sozinho. */
    private val _emEspera = MutableStateFlow(false)
    val emEspera: StateFlow<Boolean> = _emEspera.asStateFlow()

    fun iniciar() {
        registrado = try {
            gerente.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING)
            true
        } catch (e: RuntimeException) {
            Timber.w("Telecom indisponível: %s", e.javaClass.simpleName)
            false
        }
    }

    fun registrar(nome: String, entrada: Boolean, video: Boolean) {
        if (!registrado) return
        // Chamada nova: a anterior, se ainda está registrada, já acabou.
        chamada?.cancel()
        val fila = Channel<Comando>(Channel.UNLIMITED)
        comandos = fila
        val atributos = CallAttributesCompat(
            displayName = nome,
            address = Uri.fromParts("conversa", "chamada", null),
            direction = if (entrada) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = tipoTelecom(video),
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        chamada = escopo.launch {
            try {
                gerente.addCall(
                    atributos,
                    // Atendida pelo sistema (fone Bluetooth, carro, relógio).
                    onAnswer = { gerenciador.atender() },
                    // Desligada pelo sistema.
                    onDisconnect = { gerenciador.desligar() },
                    onSetActive = { sairDaEspera() },
                    // Em espera (ex.: chamada GSM atendida): ninguém mais nos ouve.
                    onSetInactive = {
                        _emEspera.value = true
                        midia.microfone(false)
                    },
                ) {
                    val escopoDaChamada = this
                    launch { currentCallEndpoint.collect { _rotaAtual.value = it } }
                    launch { availableEndpoints.collect { _rotas.value = it } }
                    // Vídeo começa no alto-falante (ninguém fala com o celular no ouvido olhando a tela).
                    if (video) launch { irParaAltoFalante(availableEndpoints.first { it.isNotEmpty() }, currentCallEndpoint.first()) }
                    launch {
                        for (comando in fila) {
                            when (comando) {
                                is Comando.Atender -> answer(
                                    tipoTelecom(comando.video),
                                )
                                Comando.Ativar -> if (setActive() is CallControlResult.Success) sairDaEspera()
                                is Comando.Rota -> requestEndpointChange(comando.rota)
                                Comando.AltoFalante -> _rotaAtual.value?.let { irParaAltoFalante(_rotas.value, it) }
                                Comando.Encerrar -> {
                                    disconnect(DisconnectCause(DisconnectCause.LOCAL))
                                    break
                                }
                            }
                        }
                        // Acabou (aqui ou pelo sistema): solta os coletores das rotas, senão o addCall não termina.
                        escopoDaChamada.cancel()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w("Telecom recusou a chamada: %s", e.javaClass.simpleName)
            } finally {
                _rotas.value = emptyList()
                _rotaAtual.value = null
                _emEspera.value = false
            }
        }
    }

    fun atender(video: Boolean) {
        comandos?.trySend(Comando.Atender(video))
    }

    fun ativar() {
        comandos?.trySend(Comando.Ativar)
    }

    /** "Retomar" depois da espera. */
    fun retomar() = ativar()

    private fun sairDaEspera() {
        if (!_emEspera.value) return
        _emEspera.value = false
        midia.microfone(gerenciador.estado.value.microfoneLigado)
    }

    fun mudarRota(rota: CallEndpointCompat) {
        comandos?.trySend(Comando.Rota(rota))
    }

    /** Passou para vídeo: sai do fone do aparelho para o alto-falante (Bluetooth e fone com fio ficam). */
    fun preferirAltoFalante() {
        comandos?.trySend(Comando.AltoFalante)
    }

    private suspend fun CallControlScope.irParaAltoFalante(disponiveis: List<CallEndpointCompat>, atual: CallEndpointCompat) {
        if (atual.type != CallEndpointCompat.TYPE_EARPIECE) return
        disponiveis.firstOrNull { it.type == CallEndpointCompat.TYPE_SPEAKER }?.let { requestEndpointChange(it) }
    }

    fun encerrar() {
        comandos?.trySend(Comando.Encerrar)
        comandos?.close()
        comandos = null
    }
}

/**
 * Serviço em primeiro plano da chamada (`phoneCall` + `microphone`, e `camera` com vídeo):
 * mantém microfone e câmera com o app em segundo plano. Só existe durante a chamada.
 */
@AndroidEntryPoint
class ServicoChamada : Service() {
    @Inject lateinit var gerenciador: GerenciadorChamadas

    @Inject lateinit var notificacoes: NotificacoesChamada

    @Inject @EscopoAplicacao
    lateinit var escopo: CoroutineScope

    private var observacao: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val estado = gerenciador.estado.value
        if (estado.fase == FaseChamada.INATIVO) {
            stopSelf()
            return START_NOT_STICKY
        }
        promover(estado)
        if (observacao == null) {
            observacao = escopo.launch(Dispatchers.Main) {
                gerenciador.estado
                    .map { Resumo(it.fase, it.tipo, it.ativaDesde, it.cameraLigada, it.dados?.participantes?.map { p -> p.nome }) to it }
                    .distinctUntilChanged { a, b -> a.first == b.first }
                    .collect { (_, atual) ->
                        if (atual.fase == FaseChamada.INATIVO) {
                            ServiceCompat.stopForeground(this@ServicoChamada, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            promover(atual)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observacao?.cancel()
        observacao = null
        super.onDestroy()
    }

    // Os tipos só existem do Android 10 em diante; antes o ServiceCompat os ignora.
    @SuppressLint("InlinedApi")
    private fun promover(estado: EstadoChamada) {
        var tipos = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        if (tem(Manifest.permission.RECORD_AUDIO)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (estado.cameraLigada && tem(Manifest.permission.CAMERA)) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        try {
            ServiceCompat.startForeground(this, NotificacoesChamada.ID_EM_ANDAMENTO, notificacoes.emAndamento(estado), tipos)
        } catch (e: RuntimeException) {
            // Sem como ficar em primeiro plano (ex.: começou em segundo plano): a chamada segue, só sem o serviço.
            Timber.w("Serviço da chamada recusado: %s", e.javaClass.simpleName)
            stopSelf()
        }
    }

    private data class Resumo(
        val fase: FaseChamada,
        val tipo: TipoChamada,
        val ativaDesde: java.time.Instant?,
        val camera: Boolean,
        val nomes: List<String>?,
    )

    companion object {
        fun iniciar(contexto: Context) {
            try {
                ContextCompat.startForegroundService(contexto, Intent(contexto, ServicoChamada::class.java))
            } catch (e: RuntimeException) {
                Timber.w("Não deu para iniciar o serviço da chamada: %s", e.javaClass.simpleName)
            }
        }
    }
}

/** "Recusar" (chamada recebida) e "Desligar" (em andamento) da notificação. */
@AndroidEntryPoint
class AcoesChamadaReceiver : BroadcastReceiver() {
    @Inject lateinit var gerenciador: GerenciadorChamadas

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACAO_RECUSAR -> gerenciador.recusar()
            ACAO_DESLIGAR -> gerenciador.desligar()
        }
    }

    companion object {
        const val ACAO_RECUSAR = "com.conversa.app.chamada.RECUSAR"
        const val ACAO_DESLIGAR = "com.conversa.app.chamada.DESLIGAR"

        fun intencao(contexto: Context, acao: String) = Intent(contexto, AcoesChamadaReceiver::class.java).setAction(acao)
    }
}

private fun tipoTelecom(video: Boolean) =
    if (video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL
