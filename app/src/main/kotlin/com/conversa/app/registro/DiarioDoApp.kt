package com.conversa.app.registro

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.mensagens.AgendadorEnvioWorkManager
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.registro.ArquivosDoRegistro
import com.conversa.app.core.registro.Registro
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * O que o app registra sobre si mesmo e o aparelho, além do que cada parte já registra
 * (requisições, tempo real, envio, sincronização, chamadas):
 * - na abertura: versão, aparelho, memória, espaço, energia, notificações, permissões e por que o
 *   processo anterior terminou ([HistoricoDoProcesso]);
 * - o caminho: primeiro e segundo plano, telas (Activity), memória pedida pelo sistema, tela
 *   acesa/apagada, Doze, economia de energia;
 * - a rede: qual é a padrão, se tem internet confirmada, se caiu ou foi bloqueada, DNS privado e proxy;
 * - o estado: servidor, tempo real (deveria estar ligado? está?), conexão em segundo plano e o
 *   trabalho do envio no WorkManager;
 * - a thread principal parada por mais de [LIMITE_TRAVA_MS] (com a pilha dela), com o app na frente.
 * Ao voltar para a frente, registra o que mudou nas permissões, nas notificações e na energia.
 */
@Singleton
class DiarioDoApp @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val primeiroPlano: MonitorPrimeiroPlano,
    private val servidor: ServidorRepositorio,
    private val tempoReal: RealtimeClient,
    private val conexao: ConexaoTempoReal,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val retrato = RetratoDoAparelho(contexto)
    private var permissoes: Map<String, Boolean> = emptyMap()
    private var notificacoes = ""
    private var energia = ""

    fun iniciar(aplicacao: Application) {
        conferirMudancas(abertura = true)
        observarEstado()
        observarRede()
        observarEnergiaETela()
        observarCiclo(aplicacao)
        vigiarThreadPrincipal()
        escopo.launch(Dispatchers.IO) {
            Timber.tag(TAG).i("Espaço: %s", retrato.espaco())
            HistoricoDoProcesso(contexto).registrar()
        }
    }

    /** Permissões, notificações e energia: na abertura, tudo; depois, só o que mudou. */
    private fun conferirMudancas(abertura: Boolean) {
        val agoraPermissoes = retrato.permissoes()
        if (abertura) {
            Timber.tag(TAG_PERMISSOES).i(
                "Permissões: %s",
                agoraPermissoes.entries.joinToString { "${it.key} ${if (it.value) "sim" else "não"}" },
            )
        } else {
            agoraPermissoes.filter { (nome, sim) -> permissoes[nome] != sim }.forEach { (nome, sim) ->
                Timber.tag(TAG_PERMISSOES).i("Permissão de %s: %s", nome, if (sim) "concedida" else "retirada")
            }
        }
        permissoes = agoraPermissoes
        val agoraNotificacoes = retrato.notificacoes()
        if (agoraNotificacoes != notificacoes) Timber.tag(TAG_PERMISSOES).i("Notificações: %s", agoraNotificacoes)
        notificacoes = agoraNotificacoes
        val agoraEnergia = retrato.energia().substringAfter("; ")
        if (agoraEnergia != energia) Timber.tag(TAG_ENERGIA).i("Energia: %s", retrato.energia())
        energia = agoraEnergia
    }

    private fun observarEstado() {
        primeiroPlano.emPrimeiroPlano.drop(1).onEach { visivel ->
            Timber.tag(TAG_CICLO).i(if (visivel) "App em primeiro plano" else "App em segundo plano")
            if (visivel) conferirMudancas(abertura = false)
        }.launchIn(escopo)
        servidor.atual.map { it?.base?.toString() }.distinctUntilChanged().onEach { base ->
            Timber.tag(TAG).i(if (base == null) "Sem servidor configurado" else "Servidor: %s", base)
        }.launchIn(escopo)
        tempoReal.estado.onEach { Timber.tag(TAG_TEMPO_REAL).d("Estado: %s", it) }.launchIn(escopo)
        conexao.ligada.drop(1).onEach { ligada ->
            Timber.tag(TAG_TEMPO_REAL).i(if (ligada) "Deve ficar ligado" else "Pode desligar (sem sessão ou em segundo plano)")
        }.launchIn(escopo)
        conexao.semTempoReal.filter { it }.onEach {
            Timber.tag(TAG_TEMPO_REAL).w(
                "Fora do ar há mais de %d s: faixa na tela e atualização periódica",
                ConexaoTempoReal.ESPERA_AVISO_MS / 1000,
            )
        }.launchIn(escopo)
        WorkManager.getInstance(contexto).getWorkInfosForUniqueWorkFlow(AgendadorEnvioWorkManager.NOME)
            .map { lista -> lista.joinToString { resumoDoTrabalho(it) }.ifEmpty { "nenhum" } }
            .distinctUntilChanged()
            .onEach { Timber.tag(EnvioMensagens.TAG).d("WorkManager: %s", it) }
            .launchIn(escopo)
    }

    private fun resumoDoTrabalho(trabalho: WorkInfo): String = buildString {
        append(trabalho.state.name.lowercase())
        append(", execuções ").append(trabalho.runAttemptCount)
        if (trabalho.state == WorkInfo.State.ENQUEUED && trabalho.nextScheduleTimeMillis != Long.MAX_VALUE) {
            append(
                ", próxima em ",
            ).append(((trabalho.nextScheduleTimeMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)).append(" s")
        }
        if (trabalho.stopReason != WorkInfo.STOP_REASON_NOT_STOPPED) append(", parado pelo motivo ").append(trabalho.stopReason)
    }

    private fun observarRede() {
        val rede = contexto.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            rede.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    private var ultimoResumo = ""
                    private var ultimoEnlace = ""

                    override fun onAvailable(network: Network) {
                        Timber.tag(TAG_REDE).i("Rede padrão disponível (%s)", network)
                    }

                    override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                        val resumo = RetratoDoAparelho.resumoDaRede(capacidades)
                        if (resumo == ultimoResumo) return
                        ultimoResumo = resumo
                        Timber.tag(TAG_REDE).i(
                            "Rede: %s; ↓ %d kbps, ↑ %d kbps",
                            resumo,
                            capacidades.linkDownstreamBandwidthKbps,
                            capacidades.linkUpstreamBandwidthKbps,
                        )
                    }

                    override fun onLinkPropertiesChanged(network: Network, enlace: LinkProperties) {
                        val resumo = RetratoDoAparelho.resumoDoEnlace(enlace)
                        if (resumo == ultimoEnlace) return
                        ultimoEnlace = resumo
                        Timber.tag(TAG_REDE).d("Enlace: %s", resumo)
                    }

                    override fun onBlockedStatusChanged(network: Network, bloqueada: Boolean) {
                        if (bloqueada) {
                            Timber.tag(TAG_REDE).w("A rede está bloqueada para o app (economia de dados ou restrição)")
                        } else {
                            Timber.tag(TAG_REDE).i("A rede está liberada para o app")
                        }
                    }

                    override fun onLost(network: Network) {
                        ultimoResumo = ""
                        Timber.tag(TAG_REDE).w("A rede padrão caiu (%s)", network)
                    }
                },
            )
        }.onFailure { Timber.tag(TAG_REDE).w(it, "Não deu para acompanhar a rede") }
    }

    private fun observarEnergiaETela() {
        val receptor = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val energia = contexto.getSystemService(PowerManager::class.java)
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> Timber.tag(TAG_ENERGIA).d("Tela acesa")
                    Intent.ACTION_SCREEN_OFF -> Timber.tag(TAG_ENERGIA).d("Tela apagada")
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED ->
                        Timber.tag(TAG_ENERGIA).i(if (energia?.isDeviceIdleMode == true) "Entrou no Doze" else "Saiu do Doze")
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        Timber.tag(TAG_ENERGIA).i(
                            "Economia de energia: %s",
                            if (energia?.isPowerSaveMode ==
                                true
                            ) {
                                "ligada"
                            } else {
                                "desligada"
                            },
                        )
                }
            }
        }
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        runCatching { ContextCompat.registerReceiver(contexto, receptor, filtro, ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onFailure { Timber.tag(TAG_ENERGIA).w(it, "Não deu para acompanhar a tela e a energia") }
    }

    private fun observarCiclo(aplicacao: Application) {
        aplicacao.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, estado: Bundle?) {
                    Timber.tag(TAG_CICLO).i(
                        "%s criada%s: %s",
                        activity.javaClass.simpleName,
                        if (estado != null) " de novo (estado guardado)" else "",
                        resumoDoIntent(activity.intent),
                    )
                }

                override fun onActivityStarted(activity: Activity) = Timber.tag(TAG_CICLO).d("%s visível", activity.javaClass.simpleName)

                override fun onActivityResumed(activity: Activity) = Timber.tag(TAG_CICLO).d("%s na frente", activity.javaClass.simpleName)

                override fun onActivityPaused(activity: Activity) = Timber.tag(TAG_CICLO).d("%s pausada", activity.javaClass.simpleName)

                override fun onActivityStopped(activity: Activity) = Timber.tag(
                    TAG_CICLO,
                ).d("%s fora da tela", activity.javaClass.simpleName)

                override fun onActivitySaveInstanceState(activity: Activity, estado: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    Timber.tag(TAG_CICLO).i(
                        "%s destruída (%s)",
                        activity.javaClass.simpleName,
                        when {
                            activity.isChangingConfigurations -> "mudança de configuração"
                            activity.isFinishing -> "fechada"
                            else -> "pelo sistema"
                        },
                    )
                }
            },
        )
        aplicacao.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onTrimMemory(nivel: Int) {
                    Timber.tag(TAG_CICLO).i("O sistema pediu para liberar memória (nível %d); %s", nivel, retrato.memoria())
                }

                override fun onConfigurationChanged(configuracao: Configuration) {
                    Timber.tag(TAG_CICLO).d(
                        "Configuração mudou: orientação %d, tema escuro %s, escala da fonte %.2f, idioma %s",
                        configuracao.orientation,
                        (configuracao.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
                        configuracao.fontScale,
                        configuracao.locales.toLanguageTags(),
                    )
                }

                @Deprecated("Substituído por onTrimMemory, mas ainda chamado em alguns aparelhos")
                override fun onLowMemory() {
                    Timber.tag(TAG_CICLO).w("Memória do aparelho no fim; %s", retrato.memoria())
                }
            },
        )
    }

    /**
     * Com o app na frente, a cada [INTERVALO_VIGIA_MS] confere se a thread principal responde. Parada
     * por mais de [LIMITE_TRAVA_MS], registra a pilha dela (onde travou) e, quando voltar, quanto durou.
     */
    private fun vigiarThreadPrincipal() {
        val principal = Handler(Looper.getMainLooper())
        thread(name = "vigia", isDaemon = true) {
            while (true) {
                Thread.sleep(INTERVALO_VIGIA_MS)
                if (!primeiroPlano.emPrimeiroPlano.value) continue
                val respondeu = CountDownLatch(1)
                val enviado = SystemClock.uptimeMillis()
                principal.post { respondeu.countDown() }
                if (respondeu.await(LIMITE_TRAVA_MS, TimeUnit.MILLISECONDS)) continue
                val pilha = Looper.getMainLooper().thread.stackTrace.take(MAXIMO_PILHA).joinToString("\n") { "\tat $it" }
                Timber.tag(TAG_TRAVA).w("A thread principal está parada há mais de %d ms:\n%s", LIMITE_TRAVA_MS, pilha)
                respondeu.await()
                Timber.tag(TAG_TRAVA).w("A thread principal voltou depois de %d ms", SystemClock.uptimeMillis() - enviado)
            }
        }
    }

    companion object {
        const val TAG = "App"

        /** A primeira coisa do processo no registro (chamada antes do Hilt, no `Application.onCreate`). */
        fun registrarAbertura(contexto: Context, registro: Registro) {
            val retrato = RetratoDoAparelho(contexto)
            val arquivos = registro.arquivos.lista()
            Timber.tag(TAG).i("===== App aberto: %s =====", retrato.app())
            Timber.tag(TAG).i("Aparelho: %s", retrato.aparelho())
            Timber.tag(TAG).i("Memória: %s", retrato.memoria())
            Timber.tag(TAG).i(
                "Registro: %d arquivo(s) desde %s, guardados por %d dias",
                arquivos.size,
                arquivos.firstOrNull()?.dia ?: "hoje",
                ArquivosDoRegistro.DIAS,
            )
        }

        private const val TAG_CICLO = "Ciclo"
        private const val TAG_REDE = "Rede"
        private const val TAG_ENERGIA = "Energia"
        private const val TAG_PERMISSOES = "Permissoes"
        private const val TAG_TEMPO_REAL = RealtimeClient.TAG
        private const val TAG_TRAVA = "Travamento"
        private const val INTERVALO_VIGIA_MS = 1_000L

        /** O Android mostra "não está respondendo" com 5 s; 2 s já é travada que a pessoa sente. */
        private const val LIMITE_TRAVA_MS = 2_000L
        private const val MAXIMO_PILHA = 30

        /**
         * O que abriu a tela: ação, endereço (`conversa://chat/8`) e o tipo, e os nomes dos extras
         * (nunca os valores: um texto compartilhado iria junto).
         */
        fun resumoDoIntent(intent: Intent?): String {
            if (intent == null) return "sem intent"
            val partes = listOfNotNull(
                intent.action?.substringAfterLast('.'),
                intent.data?.let { "${it.scheme}://${it.host.orEmpty()}${it.path.orEmpty()}" },
                intent.type,
                intent.extras?.keySet()?.takeIf { it.isNotEmpty() }?.joinToString(prefix = "extras: ") { it.substringAfterLast('.') },
            )
            return partes.joinToString("; ").ifEmpty { "sem ação" }
        }
    }
}
