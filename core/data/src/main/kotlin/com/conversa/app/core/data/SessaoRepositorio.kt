package com.conversa.app.core.data

import com.conversa.app.core.datastore.SessaoStore
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.auth.EventosSessao
import com.conversa.app.core.network.auth.TokenProvider
import com.conversa.app.core.network.di.EscopoAplicacao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/** Por que a sessão terminou (para a tela de login mostrar o aviso certo). */
enum class MotivoFimSessao { SAIU, EXPIROU }

/**
 * Sessão do usuário. Guarda o token cifrado ([SessaoStore]) e o mantém em memória
 * para o [TokenProvider] (leitura síncrona nos interceptadores).
 * Um 401 em qualquer requisição autenticada encerra a sessão (contrato §2.4).
 */
@Singleton
class SessaoRepositorio @Inject constructor(
    private val store: SessaoStore,
    eventos: EventosSessao,
    @EscopoAplicacao private val escopo: CoroutineScope,
) : TokenProvider {
    private val _sessao = MutableStateFlow<Sessao?>(null)
    val sessao: StateFlow<Sessao?> = _sessao.asStateFlow()

    private val _carregada = MutableStateFlow(false)
    val carregada: StateFlow<Boolean> = _carregada.asStateFlow()

    private val _fim = MutableSharedFlow<MotivoFimSessao>(extraBufferCapacity = 1)
    val fim: SharedFlow<MotivoFimSessao> = _fim.asSharedFlow()

    @Volatile private var tokenAtual: String? = null

    init {
        escopo.launch {
            store.sessao.collect { gravada ->
                tokenAtual = gravada?.token
                _sessao.value = gravada
                _carregada.value = true
            }
        }
        escopo.launch {
            eventos.sessaoExpirada.collect {
                if (tokenAtual != null) {
                    Timber.i("Sessão expirada (401)")
                    encerrar(MotivoFimSessao.EXPIROU)
                }
            }
        }
    }

    override fun token(): String? = tokenAtual

    suspend fun salvar(sessao: Sessao) {
        tokenAtual = sessao.token
        _sessao.value = sessao
        store.salvar(sessao)
    }

    suspend fun encerrar(motivo: MotivoFimSessao = MotivoFimSessao.SAIU) {
        tokenAtual = null
        _sessao.value = null
        store.limpar()
        _fim.tryEmit(motivo)
    }
}
