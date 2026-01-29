package com.conversa.conversa.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.conversa.conversa.data.model.ChamadaResponse
import com.conversa.conversa.service.ChamadaService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Helper para observar o estado do ChamadaService de qualquer Activity.
 *
 * Encapsula o binding com ChamadaService e expõe StateFlows para as Activities
 * observarem o estado da chamada e exibirem o CallBanner quando necessário.
 *
 * Uso:
 * ```
 * class MainActivity : AppCompatActivity() {
 *     private lateinit var chamadaObserver: ChamadaServiceObserver
 *
 *     override fun onCreate(...) {
 *         chamadaObserver = ChamadaServiceObserver(this)
 *     }
 *
 *     override fun onResume() {
 *         super.onResume()
 *         chamadaObserver.bind()
 *     }
 *
 *     override fun onPause() {
 *         super.onPause()
 *         chamadaObserver.unbind()
 *     }
 * }
 * ```
 */
class ChamadaServiceObserver(private val context: Context) {

    companion object {
        private const val TAG = "ChamadaServiceObserver"
    }

    private var chamadaService: ChamadaService? = null
    private var bound = false

    // Scope para coletar flows do service
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var estadoCollectorJob: Job? = null
    private var chamadaCollectorJob: Job? = null
    private var timerCollectorJob: Job? = null

    // StateFlows locais que espelham o service
    private val _estadoFlow = MutableStateFlow(ChamadaService.EstadoChamadaService.IDLE)
    val estadoFlow: StateFlow<ChamadaService.EstadoChamadaService> = _estadoFlow.asStateFlow()

    private val _chamadaAtualFlow = MutableStateFlow<ChamadaResponse?>(null)
    val chamadaAtualFlow: StateFlow<ChamadaResponse?> = _chamadaAtualFlow.asStateFlow()

    private val _timerFlow = MutableStateFlow("00:00")
    val timerFlow: StateFlow<String> = _timerFlow.asStateFlow()

    /**
     * Verifica se o banner deve ser exibido.
     * O banner é visível quando há uma chamada em andamento (EM_CHAMADA).
     */
    val shouldShowBanner: Boolean
        get() = _estadoFlow.value == ChamadaService.EstadoChamadaService.EM_CHAMADA

    /**
     * Retorna o nome do contato/grupo da chamada atual.
     * Usado para exibir no CallBanner.
     */
    fun getCallerName(meuUsuarioId: Int): String {
        val chamada = _chamadaAtualFlow.value ?: return "Chamada ativa"

        // Para chamadas em grupo (tipo == 2), usa o nome do grupo se disponível
        if (chamada.tipo == 2 && chamada.usuarios.size > 2) {
            return "Chamada em grupo"
        }

        // Para chamada 1:1, retorna o nome do outro participante
        return chamada.usuarios
            .firstOrNull { it.usuarioId != meuUsuarioId }
            ?.usuarioNome ?: "Chamada ativa"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service conectado")
            val localBinder = binder as ChamadaService.LocalBinder
            chamadaService = localBinder.getService()
            bound = true

            // Inicia coleta dos flows do service
            startCollectingFlows()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service desconectado")
            chamadaService = null
            bound = false
            stopCollectingFlows()
        }
    }

    /**
     * Vincula ao ChamadaService.
     * Deve ser chamado em onResume() da Activity.
     */
    fun bind() {
        if (bound) {
            Log.d(TAG, "Já está vinculado ao service")
            return
        }

        try {
            val intent = Intent(context, ChamadaService::class.java)
            // NÃO usa BIND_AUTO_CREATE - só conecta se o service JÁ estiver rodando
            // Se o service não estiver rodando, bindService retorna false
            // e onServiceConnected nunca será chamado
            val bindResult = context.bindService(intent, connection, 0)
            if (bindResult) {
                Log.d(TAG, "Solicitando binding ao ChamadaService")
            } else {
                Log.d(TAG, "Service não está rodando - banner não será exibido")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao vincular ao ChamadaService", e)
        }
    }

    /**
     * Desvincula do ChamadaService.
     * Deve ser chamado em onPause() da Activity.
     */
    fun unbind() {
        if (!bound) {
            Log.d(TAG, "Não está vinculado ao service")
            return
        }

        try {
            stopCollectingFlows()
            context.unbindService(connection)
            bound = false
            chamadaService = null
            Log.d(TAG, "Desvinculado do ChamadaService")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desvincular do ChamadaService", e)
        }
    }

    private fun startCollectingFlows() {
        val service = chamadaService ?: return

        // Coleta estado da chamada
        estadoCollectorJob = scope.launch {
            service.estadoFlow.collect { estado ->
                Log.d(TAG, "Estado atualizado: $estado")
                _estadoFlow.value = estado
            }
        }

        // Coleta dados da chamada
        chamadaCollectorJob = scope.launch {
            service.chamadaAtualFlow.collect { chamada ->
                Log.d(TAG, "Chamada atualizada: ${chamada?.id}")
                _chamadaAtualFlow.value = chamada
            }
        }

        // Coleta timer
        timerCollectorJob = scope.launch {
            service.timerFlow.collect { timer ->
                _timerFlow.value = timer
            }
        }
    }

    private fun stopCollectingFlows() {
        estadoCollectorJob?.cancel()
        chamadaCollectorJob?.cancel()
        timerCollectorJob?.cancel()
        estadoCollectorJob = null
        chamadaCollectorJob = null
        timerCollectorJob = null

        // Reseta para estado inicial quando desvincula
        _estadoFlow.value = ChamadaService.EstadoChamadaService.IDLE
        _chamadaAtualFlow.value = null
        _timerFlow.value = "00:00"
    }
}
