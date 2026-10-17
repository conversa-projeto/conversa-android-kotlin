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
 * - último usuário digitado no login (só o login, nunca a senha), para preencher o campo;
 * - o tema escolhido e a qualidade das chamadas (8.5).
 */
class PreferenciasStore(private val dataStore: DataStore<Preferences>) {
    val enderecoServidor: Flow<String?> = dataStore.data.map { it[ENDERECO_SERVIDOR] }

    val dispositivoId: Flow<Long?> = dataStore.data.map { it[DISPOSITIVO_ID] }

    val ultimoLogin: Flow<String?> = dataStore.data.map { it[ULTIMO_LOGIN] }

    /** Já explicou e pediu a permissão de notificações depois do login (FC-607): não insiste a cada abertura. */
    val pediuNotificacoes: Flow<Boolean> = dataStore.data.map { it[PEDIU_NOTIFICACOES] == true }

    /** Já explicou a tela cheia das chamadas (Android 14+, TODO 6.5): uma vez só. */
    val pediuTelaCheia: Flow<Boolean> = dataStore.data.map { it[PEDIU_TELA_CHEIA] == true }

    /** "sistema", "claro" ou "escuro" (`PreferenciaTema.chave`); nulo = nunca escolheu. */
    val tema: Flow<String?> = dataStore.data.map { it[TEMA] }

    suspend fun salvarTema(chave: String) {
        dataStore.edit { it[TEMA] = chave }
    }

    /** Qualidade das chamadas como texto (`ConfigChamada.paraTexto`); nulo = padrão. */
    val configChamada: Flow<String?> = dataStore.data.map { it[CONFIG_CHAMADA] }

    suspend fun salvarConfigChamada(texto: String) {
        dataStore.edit { it[CONFIG_CHAMADA] = texto }
    }

    /** "Sugerir a continuação ao digitar" (ENV-25): ligada até a pessoa desligar, como o web. */
    val sugestoesIa: Flow<Boolean> = dataStore.data.map { it[SUGESTOES_IA] != false }

    suspend fun salvarSugestoesIa(ligadas: Boolean) {
        dataStore.edit { it[SUGESTOES_IA] = ligadas }
    }

    /**
     * "Receber com o app fechado" (ADR 0002, opção A): o WebSocket fica aberto num serviço em
     * primeiro plano. Ligada até a pessoa desligar, porque sem FCM é o único jeito de avisar.
     */
    val conexaoSegundoPlano: Flow<Boolean> = dataStore.data.map { it[CONEXAO_SEGUNDO_PLANO] != false }

    suspend fun salvarConexaoSegundoPlano(ligada: Boolean) {
        dataStore.edit { it[CONEXAO_SEGUNDO_PLANO] = ligada }
    }

    /** Já explicou e pediu a isenção da otimização de bateria: uma vez só. */
    val pediuBateria: Flow<Boolean> = dataStore.data.map { it[PEDIU_BATERIA] == true }

    suspend fun marcarPediuBateria() {
        dataStore.edit { it[PEDIU_BATERIA] = true }
    }

    /** Impressão SHA-256 do certificado próprio do servidor que a pessoa aceitou (só no debug); vale para o endereço salvo. */
    val certificadoServidor: Flow<String?> = dataStore.data.map { it[CERTIFICADO_SERVIDOR] }

    /** Endereço e certificado juntos: trocar de servidor sem certificado aceito apaga o do anterior. */
    suspend fun salvarEnderecoServidor(endereco: String, certificado: String? = null) {
        dataStore.edit {
            it[ENDERECO_SERVIDOR] = endereco
            if (certificado == null) it.remove(CERTIFICADO_SERVIDOR) else it[CERTIFICADO_SERVIDOR] = certificado
        }
    }

    suspend fun salvarDispositivoId(id: Long) {
        dataStore.edit { it[DISPOSITIVO_ID] = id }
    }

    suspend fun marcarPediuNotificacoes() {
        dataStore.edit { it[PEDIU_NOTIFICACOES] = true }
    }

    suspend fun marcarPediuTelaCheia() {
        dataStore.edit { it[PEDIU_TELA_CHEIA] = true }
    }

    suspend fun salvarUltimoLogin(login: String) {
        dataStore.edit { it[ULTIMO_LOGIN] = login }
    }

    private companion object {
        val ENDERECO_SERVIDOR = stringPreferencesKey("endereco_servidor")
        val CERTIFICADO_SERVIDOR = stringPreferencesKey("certificado_servidor")
        val DISPOSITIVO_ID = longPreferencesKey("dispositivo_id")
        val ULTIMO_LOGIN = stringPreferencesKey("ultimo_login")
        val PEDIU_NOTIFICACOES = booleanPreferencesKey("pediu_notificacoes")
        val PEDIU_TELA_CHEIA = booleanPreferencesKey("pediu_tela_cheia")
        val TEMA = stringPreferencesKey("tema")
        val CONFIG_CHAMADA = stringPreferencesKey("config_chamada")
        val SUGESTOES_IA = booleanPreferencesKey("sugestoes_ia")
        val CONEXAO_SEGUNDO_PLANO = booleanPreferencesKey("conexao_segundo_plano")
        val PEDIU_BATERIA = booleanPreferencesKey("pediu_bateria")
    }
}
