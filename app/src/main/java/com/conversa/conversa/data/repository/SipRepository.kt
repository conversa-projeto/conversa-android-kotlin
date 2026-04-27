package com.conversa.conversa.data.repository

import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.SipConfig
import com.conversa.conversa.data.model.SipRequest
import com.conversa.conversa.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Repository da configuracao SIP do usuario (telefonia PSTN opcional).
 *
 * Nao implementa o stack SIP (sip.js equivalente) — apenas expoe os endpoints
 * REST para ler/criar/atualizar credenciais. Um stack SIP completo para Android
 * exige integracao com uma lib como PJSIP, Linphone ou android-sip.
 */
class SipRepository(
    private val api: ConversaApi,
    private val userPreferences: UserPreferences,
) {
    suspend fun obter(): Result<SipConfig?> = runCatching {
        val resp = api.obterSip("Bearer ${token()}")
        when {
            resp.isSuccessful -> resp.body()
            resp.code() == 404 -> null
            else -> error("HTTP ${resp.code()}")
        }
    }

    suspend fun criar(req: SipRequest): Result<SipConfig> = runCatching {
        val resp = api.criarSip("Bearer ${token()}", req)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    suspend fun atualizar(req: SipRequest): Result<SipConfig> = runCatching {
        val resp = api.atualizarSip("Bearer ${token()}", req)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    private suspend fun token() = userPreferences.authToken.first() ?: error("Sem token")
}
