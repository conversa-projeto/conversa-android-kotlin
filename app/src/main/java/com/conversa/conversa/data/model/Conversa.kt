package com.conversa.conversa.data.model

data class Conversa(
    val id: Int,
    val descricao: String,
    val tipo: Int, // 1 = individual, 2 = grupo
    val inserida: String?, // Pode ser null
    val nome: String?, // ⚠️ Pode ser null (grupos sem nome específico)
    val destinatario_id: Int?, // ⚠️ Pode ser null em grupos
    val mensagem_id: Int, // Sempre retorna (coalesce garante 0)
    val ultima_mensagem: String?, // ⚠️ Timestamp, pode ser null
    val ultima_mensagem_texto: String?, // ⚠️ Pode ser null
    val mensagens_sem_visualizar: Int // Sempre retorna (cast garante 0)
)

data class UltimaMensagem(
    val id: Int,
    val texto: String?,
    val remetente: Remetente?,
    val enviado_em: String?,
    val lida: Boolean
)

data class Remetente(
    val id: Int,
    val nome: String
)
