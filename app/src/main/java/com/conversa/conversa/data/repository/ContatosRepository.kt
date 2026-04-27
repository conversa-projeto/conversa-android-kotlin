package com.conversa.conversa.data.repository

import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.socket.SocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Gerencia contatos e presenca online.
 * Reage ao evento WebSocket StatusUsuario (tipo 60) para atualizar estado em tempo real.
 */
class ContatosRepository(
    private val api: ConversaApi,
    private val socketManager: SocketManager,
    private val userPreferences: UserPreferences,
) {
    companion object { private const val TAG = "ContatosRepository" }

    private val _usuariosOnline = MutableStateFlow<Set<Int>>(emptySet())
    val usuariosOnlineFlow: StateFlow<Set<Int>> = _usuariosOnline.asStateFlow()

    init {
        socketManager.onStatusUsuario = { usuarioId, online ->
            _usuariosOnline.value = if (online) _usuariosOnline.value + usuarioId
                                    else _usuariosOnline.value - usuarioId
        }
    }

    suspend fun listarContatos(): Result<List<Contato>> = runCatching {
        val token = token()
        val resp = api.listarContatos("Bearer $token")
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: emptyList()
    }

    suspend fun sincronizarOnline(): Result<Set<Int>> = runCatching {
        val token = token()
        val resp = api.listarContatosOnline("Bearer $token")
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        val set = (resp.body() ?: emptyList()).toSet()
        _usuariosOnline.value = set
        set
    }.onFailure { Log.w(TAG, "sincronizarOnline falhou: ${it.message}") }

    suspend fun adicionar(relacionamentoId: Int): Result<Unit> = runCatching {
        val token = token()
        val resp = api.adicionarContato("Bearer $token", relacionamentoId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    suspend fun remover(contatoId: Int): Result<Unit> = runCatching {
        val token = token()
        val resp = api.removerContato("Bearer $token", contatoId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    fun estaOnline(usuarioId: Int): Boolean = _usuariosOnline.value.contains(usuarioId)

    private suspend fun token(): String =
        userPreferences.authToken.first() ?: error("Sem token")
}
