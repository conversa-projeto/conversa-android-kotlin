package com.conversa.app.conexao

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.conversa.app.R
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.notificacoes.CanaisNotificacao
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Receber com o app fechado sem FCM (ADR 0002, opção A): com sessão e a chave
 * "Receber com o app fechado" ligada, o WebSocket fica aberto num serviço em primeiro
 * plano ([ServicoConexao]) com notificação fixa. As mensagens (WS 2) viram notificação pelo
 * `NotificadorMensagens` e as chamadas (WS 51) tocam pela `IntegracaoChamada`, como com o app aberto.
 *
 * O Android só deixa iniciar o serviço com o app visível (ou no boot, pelo [InicioDoAparelhoReceiver]):
 * por isso tenta ao ativar e de novo cada vez que o app volta para a frente.
 */
@Singleton
class ConexaoEmSegundoPlano @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val sessao: SessaoRepositorio,
    private val preferencias: PreferenciasRepositorio,
    private val conexao: ConexaoTempoReal,
    private val primeiroPlano: MonitorPrimeiroPlano,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _ativa = MutableStateFlow<Boolean?>(null)

    /** `null` enquanto a sessão e a preferência carregam. */
    val ativa: StateFlow<Boolean?> = _ativa.asStateFlow()

    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch {
            sessao.carregada.first { it }
            combine(sessao.sessao, preferencias.conexaoSegundoPlano) { atual, ligada -> atual != null && ligada }
                .distinctUntilChanged()
                .collect { ligada ->
                    conexao.manterEmSegundoPlano.value = ligada
                    _ativa.value = ligada
                }
        }
        escopo.launch(Dispatchers.Main) {
            combine(_ativa.filterNotNull(), primeiroPlano.emPrimeiroPlano) { ligada, visivel -> ligada to visivel }
                .distinctUntilChanged()
                .collect { (ligada, visivel) ->
                    when {
                        !ligada -> ServicoConexao.parar(contexto)
                        // Em segundo plano o Android recusa; o serviço já iniciado continua.
                        visivel -> ServicoConexao.iniciar(contexto)
                    }
                }
        }
    }

    /** O Android deixa o app usar a rede com a tela apagada (Doze)? */
    fun semRestricaoDeBateria(): Boolean =
        contexto.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(contexto.packageName) != false
}

/**
 * Serviço em primeiro plano da conexão (`specialUse`): só mantém o processo vivo com a
 * notificação fixa; quem segura o WebSocket é o `ConexaoTempoReal`. Para sozinho ao sair
 * da conta ou ao desligar a chave.
 */
@AndroidEntryPoint
class ServicoConexao : Service() {
    @Inject lateinit var controle: ConexaoEmSegundoPlano

    @Inject @EscopoAplicacao
    lateinit var escopo: CoroutineScope

    private var observacao: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Primeiro sobe para primeiro plano (o Android exige em poucos segundos); depois confere se deve ficar.
        if (!promover()) return START_NOT_STICKY
        if (observacao == null) {
            observacao = escopo.launch(Dispatchers.Main) {
                controle.ativa.filterNotNull().first { !it }
                ServiceCompat.stopForeground(this@ServicoConexao, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observacao?.cancel()
        observacao = null
        super.onDestroy()
    }

    @SuppressLint("InlinedApi")
    private fun promover(): Boolean {
        val abrir = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notificacao = NotificationCompat.Builder(this, CanaisNotificacao.CONEXAO)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setContentTitle(getString(R.string.conexao_notificacao_titulo))
            .setContentText(getString(R.string.conexao_notificacao_texto))
            .setContentIntent(abrir)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, ID_NOTIFICACAO, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID_NOTIFICACAO, notificacao)
            }
            true
        } catch (e: RuntimeException) {
            // Iniciado em segundo plano (o Android recusa): tenta de novo quando o app voltar para a frente.
            Timber.w("Serviço da conexão recusado: %s", e.javaClass.simpleName)
            stopSelf()
            false
        }
    }

    companion object {
        private const val ID_NOTIFICACAO = 7100

        fun iniciar(contexto: Context) {
            try {
                ContextCompat.startForegroundService(contexto, Intent(contexto, ServicoConexao::class.java))
            } catch (e: RuntimeException) {
                Timber.w("Não deu para iniciar o serviço da conexão: %s", e.javaClass.simpleName)
            }
        }

        fun parar(contexto: Context) {
            contexto.stopService(Intent(contexto, ServicoConexao::class.java))
        }
    }
}

/**
 * Celular ligado ou app atualizado: sobe o serviço da conexão (o Android permite neste momento).
 * Se não houver sessão ou a chave estiver desligada, o serviço para sozinho logo em seguida.
 */
class InicioDoAparelhoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ServicoConexao.iniciar(context)
        }
    }
}
