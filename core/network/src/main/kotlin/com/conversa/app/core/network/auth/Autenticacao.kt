package com.conversa.app.core.network.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Dá o token JWT da sessão atual (ou `null` sem sessão). Leitura síncrona, em memória. */
fun interface TokenProvider {
    fun token(): String?
}

/**
 * Avisos de sessão para o app inteiro. Qualquer 401 numa requisição autenticada
 * vira [sessaoExpirada]; a camada de dados escuta e encerra a sessão (contrato §2.4).
 */
@Singleton
class EventosSessao @Inject constructor() {
    private val _sessaoExpirada = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sessaoExpirada: SharedFlow<Unit> = _sessaoExpirada.asSharedFlow()

    fun notificarSessaoExpirada() {
        _sessaoExpirada.tryEmit(Unit)
    }
}
