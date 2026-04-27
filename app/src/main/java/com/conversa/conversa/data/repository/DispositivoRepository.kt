package com.conversa.conversa.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.AtualizarDispositivoRequest
import com.conversa.conversa.data.model.DispositivoResponse
import com.conversa.conversa.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

/**
 * Registro de dispositivo e atualizacao do token FCM.
 *
 * Fluxo:
 * 1. No app onCreate (ou login): chamar registrarOuAtualizar() para criar/atualizar
 *    o registro com info do aparelho e FCM token
 * 2. Apos login bem-sucedido: chamar vincularAoUsuario(dispositivoId)
 * 3. Quando FCM regenera token (onNewToken do service): chamar atualizarTokenFcm()
 */
class DispositivoRepository(
    private val context: Context,
    private val api: ConversaApi,
    private val userPreferences: UserPreferences,
) {
    companion object { private const val TAG = "DispositivoRepository" }

    private val androidId: String
        get() = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Exception) { "" }

    /**
     * Envia metadados do dispositivo + token FCM. Se ja existir registro
     * com esse fingerprint, backend faz update; caso contrario cria.
     */
    suspend fun registrarOuAtualizar(tokenFcm: String? = null): Result<DispositivoResponse> = runCatching {
        val req = AtualizarDispositivoRequest(
            id = null,
            nome = "$androidId-${Build.MODEL}".take(50),
            modelo = "${Build.MANUFACTURER} ${Build.MODEL}".take(50),
            versaoSo = "Android ${Build.VERSION.RELEASE}".take(15),
            plataforma = "android",
            tokenFcm = tokenFcm,
        )
        val resp = api.atualizarDispositivo("Bearer ${token()}", req)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        val dispositivo = resp.body() ?: error("Body vazio")
        Log.d(TAG, "Dispositivo registrado: id=${dispositivo.id}")
        dispositivo
    }

    /** Vincula dispositivo ao usuario autenticado */
    suspend fun vincularAoUsuario(dispositivoId: Int): Result<Unit> = runCatching {
        val resp = api.vincularDispositivo("Bearer ${token()}", dispositivoId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    /** Atualiza somente o FCM token (chamado em onNewToken) */
    suspend fun atualizarTokenFcm(novoToken: String): Result<Unit> = runCatching {
        val req = AtualizarDispositivoRequest(tokenFcm = novoToken)
        val resp = api.atualizarDispositivo("Bearer ${token()}", req)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    private suspend fun token() = userPreferences.authToken.first() ?: error("Sem token")
}
