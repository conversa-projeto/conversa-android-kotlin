package com.conversa.app.core.model

/**
 * Sessão do usuário logado. O token JWT vale 12 h e não tem renovação no
 * servidor (contrato §2.2): ao receber 401, a sessão acaba e o usuário entra de novo.
 * A senha nunca é guardada.
 */
data class Sessao(
    val token: String,
    val usuarioId: Long,
    val nome: String,
    val email: String? = null,
    val telefone: String? = null,
    val avatarIdentificador: String? = null,
    val dispositivoId: Long? = null,
)
