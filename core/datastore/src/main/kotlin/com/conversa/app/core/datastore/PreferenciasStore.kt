package com.conversa.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferências que sobrevivem ao logout:
 * - endereço do servidor;
 * - id do dispositivo, reenviado no próximo login para reaproveitar o mesmo registro
 *   (contrato §2.1).
 */
class PreferenciasStore(private val dataStore: DataStore<Preferences>) {
    val enderecoServidor: Flow<String?> = dataStore.data.map { it[ENDERECO_SERVIDOR] }

    val dispositivoId: Flow<Long?> = dataStore.data.map { it[DISPOSITIVO_ID] }

    suspend fun salvarEnderecoServidor(endereco: String) {
        dataStore.edit { it[ENDERECO_SERVIDOR] = endereco }
    }

    suspend fun salvarDispositivoId(id: Long) {
        dataStore.edit { it[DISPOSITIVO_ID] = id }
    }

    private companion object {
        val ENDERECO_SERVIDOR = stringPreferencesKey("endereco_servidor")
        val DISPOSITIVO_ID = longPreferencesKey("dispositivo_id")
    }
}
