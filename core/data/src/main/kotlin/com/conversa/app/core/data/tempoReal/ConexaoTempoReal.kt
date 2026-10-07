package com.conversa.app.core.data.tempoReal

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.network.di.EscopoAplicacao
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
 * Sem serviço em primeiro plano permanente (problema #15 do legado).
 */
@Singleton
class ConexaoTempoReal @Inject constructor(
    private val cliente: RealtimeClient,
    private val sessao: SessaoRepositorio,
    private val servidor: ServidorRepositorio,
    private val primeiroPlano: MonitorPrimeiroPlano,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    /** Ligado pelo gerenciador de chamadas (etapa 6) enquanto houver chamada. */
    val chamadaAtiva = MutableStateFlow(false)

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
                Desejado(token = sessaoAtual?.token?.takeIf { config != null && (visivel || emChamada) }, config = config?.base?.toString())
            }
                .distinctUntilChanged()
                .collectLatest { desejado ->
                    if (desejado.token == null) {
                        delay(TOLERANCIA_MS)
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
    }

    private data class Desejado(val token: String?, val config: String?)

    companion object {
        const val TOLERANCIA_MS = 10_000L
    }
}
