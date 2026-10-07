package com.conversa.app.core.data.tempoReal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.realtime.EstadoConexao
import com.conversa.app.core.network.realtime.RealtimeClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App visível (alguma Activity em primeiro plano), pelo `ProcessLifecycleOwner`. */
@Singleton
class MonitorPrimeiroPlano @Inject constructor() {
    private val _emPrimeiroPlano = MutableStateFlow(false)
    val emPrimeiroPlano: StateFlow<Boolean> = _emPrimeiroPlano.asStateFlow()

    /** Chamar uma vez, na thread principal (no `Application.onCreate`). */
    fun iniciar() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    _emPrimeiroPlano.value = true
                }

                override fun onStop(owner: LifecycleOwner) {
                    _emPrimeiroPlano.value = false
                }
            },
        )
    }
}

/**
 * Decide quando o WebSocket fica ligado: com sessão **e** (app em primeiro plano
 * **ou** chamada ativa). Em segundo plano desliga depois de [TOLERANCIA_MS]; aí o
 * usuário aparece offline e o servidor volta a mandar push (contrato §15.1).
 * Sem sessão (logout ou 401), desliga na hora.
 * Sem serviço em primeiro plano permanente (problema #15 do legado).
 *
 * Enquanto deveria estar ligado mas não está conectado:
 * - a cada [PERIODO_ATUALIZACAO_MS] busca mensagens novas e chamadas pendentes (como o web);
 * - depois de [ESPERA_AVISO_MS], [semTempoReal] vira `true` para a tela mostrar a faixa (GER-02).
 */
@Singleton
class ConexaoTempoReal @Inject constructor(
    private val cliente: RealtimeClient,
    private val sessao: SessaoRepositorio,
    private val servidor: ServidorRepositorio,
    private val primeiroPlano: MonitorPrimeiroPlano,
    private val sincronizacao: SyncManager,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    /** Ligado pelo gerenciador de chamadas (etapa 6) enquanto houver chamada. */
    val chamadaAtiva = MutableStateFlow(false)

    private val _ligada = MutableStateFlow(false)

    /** O socket deveria estar aberto agora (sessão + app visível ou chamada). */
    val ligada: StateFlow<Boolean> = _ligada.asStateFlow()

    private val _semTempoReal = MutableStateFlow(false)

    /** Deveria estar conectado e não está há mais de [ESPERA_AVISO_MS]: mostrar a faixa. */
    val semTempoReal: StateFlow<Boolean> = _semTempoReal.asStateFlow()

    private var iniciado = false
    private var ultimoServidor: String? = null

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch {
            combine(
                sessao.sessao,
                servidor.atual,
                primeiroPlano.emPrimeiroPlano,
                chamadaAtiva,
            ) { sessaoAtual, config, visivel, emChamada ->
                Desejado(
                    token = sessaoAtual?.token?.takeIf { config != null && (visivel || emChamada) },
                    temSessao = sessaoAtual != null,
                    config = config?.base?.toString(),
                )
            }
                .distinctUntilChanged()
                .collectLatest { desejado ->
                    _ligada.value = desejado.token != null
                    if (desejado.token == null) {
                        // Só em segundo plano há tolerância; sem sessão, desliga já.
                        if (desejado.temSessao) delay(TOLERANCIA_MS)
                        withContext(Dispatchers.IO) { cliente.desconectar() }
                        ultimoServidor = null
                    } else {
                        withContext(Dispatchers.IO) {
                            val trocouServidor = ultimoServidor != null && ultimoServidor != desejado.config
                            cliente.conectar(desejado.token)
                            // Mesmo token, servidor novo: o conectar() não reabre sozinho.
                            if (trocouServidor) cliente.reconectarAgora()
                        }
                        ultimoServidor = desejado.config
                    }
                }
        }
        escopo.launch {
            combine(_ligada, cliente.estado) { ligada, estado -> ligada && estado != EstadoConexao.CONECTADO }
                .distinctUntilChanged()
                .collectLatest { foraDoAr ->
                    _semTempoReal.value = false
                    if (!foraDoAr) return@collectLatest
                    launch {
                        delay(ESPERA_AVISO_MS)
                        _semTempoReal.value = true
                    }
                    while (true) {
                        delay(PERIODO_ATUALIZACAO_MS)
                        sincronizacao.atualizacaoPeriodica()
                    }
                }
        }
    }

    /** Botão "Tentar agora" da faixa. */
    fun tentarAgora() {
        escopo.launch(Dispatchers.IO) { cliente.reconectarAgora() }
    }

    private data class Desejado(val token: String?, val temSessao: Boolean, val config: String?)

    companion object {
        const val TOLERANCIA_MS = 10_000L

        /** Igual ao web: faixa depois de 5 s desconectado. */
        const val ESPERA_AVISO_MS = 5_000L

        /** Igual ao web: polling de 8 s só com o socket fora. */
        const val PERIODO_ATUALIZACAO_MS = 8_000L
    }
}
