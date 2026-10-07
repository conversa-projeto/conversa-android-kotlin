package com.conversa.conversa.data.model

/**
 * Eventos pontuais de chamada propagados via SharedFlow
 * Usados para notificar UI de mudanças em tempo real vindas do Socket
 */
sealed class EventoChamada {
    
    data class Recebida(
        val chamadaId: Int,
        val usuarioId: Int,
        val usuarioNome: String
    ) : EventoChamada()
    
    data class UsuarioEntrou(
        val chamadaId: Int,
        val usuarioId: Int
    ) : EventoChamada()
    
    data class UsuarioSaiu(
        val chamadaId: Int,
        val usuarioId: Int
    ) : EventoChamada()
    
    data class UsuarioRecusou(
        val chamadaId: Int,
        val usuarioId: Int
    ) : EventoChamada()
    
    data class Finalizada(
        val chamadaId: Int,
        val usuarioId: Int
    ) : EventoChamada()
}
