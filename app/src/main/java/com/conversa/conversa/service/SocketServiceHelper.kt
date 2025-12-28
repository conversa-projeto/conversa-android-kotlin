package com.conversa.conversa.service

import android.util.Log
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ChamadaResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper para buscar dados adicionais das chamadas
 */
object SocketServiceHelper {
    private const val TAG = "SocketServiceHelper"

    suspend fun buscarDadosChamada(chamadaId: Int, token: String): ChamadaResponse? {
        return try {
            Log.d(TAG, "🔍 Buscando dados da chamada $chamadaId...")

            val response = withContext(Dispatchers.IO) {
                RetrofitClient.api.obterDadosChamada("Bearer $token", chamadaId)
            }

            if (response.isSuccessful) {
                val chamada = response.body()
                Log.d(TAG, "✅ Dados obtidos: tipo=${chamada?.tipo}, participantes=${chamada?.usuarios?.size}")
                chamada
            } else {
                Log.e(TAG, "❌ Erro API: ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exceção ao buscar dados", e)
            null
        }
    }

    fun formatarNomeExibicao(chamada: ChamadaResponse?, usuarioNome: String): String {
        if (chamada == null) {
            Log.d(TAG, "⚠️ Chamada null, usando nome do socket: $usuarioNome")
            return usuarioNome.ifEmpty { "Chamada recebida" }
        }

        // Busca o nome de quem criou a chamada (quem está ligando)
        val nomeCriador = chamada.usuarios.find { it.usuarioId == chamada.criadoPor }?.usuarioNome
            ?: usuarioNome

        Log.d(TAG, "📝 Nome formatado: $nomeCriador (tipo=${chamada.tipo}, criadoPor=${chamada.criadoPor})")

        return if (chamada.tipo == 2) {
            "$nomeCriador (Grupo - ${chamada.usuarios.size} pessoas)"
        } else {
            nomeCriador
        }
    }

    fun formatarTextoNotificacao(chamada: ChamadaResponse?): String {
        return if (chamada?.tipo == 2) {
            "Chamada em grupo"
        } else {
            "Chamada de voz"
        }
    }
}
