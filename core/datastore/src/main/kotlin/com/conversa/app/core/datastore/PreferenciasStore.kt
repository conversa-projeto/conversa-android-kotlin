package com.conversa.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferências que sobrevivem ao logout:
 * - endereço do servidor;
 * - id do dispositivo, reenviado no próximo login para reaproveitar o mesmo registro
 *   (contrato §2.1);
 * - último usuário digitado no login (só o login, nunca a senha), para preencher o campo.
 */
class PreferenciasStore(private val dataStore: DataStore<Preferences>) {
    val enderecoServidor: Flow<String?> = dataStore.data.map { it[ENDERECO_SERVIDOR] }

    val dispositivoId: Flow<Long?> = dataStore.data.map { it[DISPOSITIVO_ID] }

    val ultimoLogin: Flow<String?> = dataStore.data.map { it[ULTIMO_LOGIN] }

    /** Já explicou e pediu a permissão de notificações depois do login (FC-607): não insiste a cada abertura. */
    val pediuNotificacoes: Flow<Boolean> = dataStore.data.map { it[PEDIU_NOTIFICACOES] == true }

    suspend fun salvarEnderecoServidor(endereco: String) {
        dataStore.edit { it[ENDERECO_SERVIDOR] = endereco }
    }

    suspend fun salvarDispositivoId(id: Long) {
        dataStore.edit { it[DISPOSITIVO_ID] = id }
    }

    suspend fun marcarPediuNotificacoes() {
        dataStore.edit { it[PEDIU_NOTIFICACOES] = true }
    }

    suspend fun salvarUltimoLogin(login: String) {
        dataStore.edit { it[ULTIMO_LOGIN] = login }
    }

    private companion object {
        val ENDERECO_SERVIDOR = stringPreferencesKey("endereco_servidor")
        val DISPOSITIVO_ID = longPreferencesKey("dispositivo_id")
        val ULTIMO_LOGIN = stringPreferencesKey("ultimo_login")
        val PEDIU_NOTIFICACOES = booleanPreferencesKey("pediu_notificacoes")
    }
}
