package com.conversa.app.core.model

import java.time.Instant

enum class TipoConversa(val codigo: Int) {
    DIRETA(1),
    GRUPO(2),
    ;

    companion object {
        fun de(codigo: Int): TipoConversa = entries.firstOrNull { it.codigo == codigo } ?: DIRETA
    }
}

/** Item da lista de conversas (`GET /conversas`, contrato §11.2). */
data class Conversa(
    val id: Long,
    val tipo: TipoConversa,
    val descricao: String?,
    /** Nome do outro participante (só em conversa direta). */
    val nome: String?,
    /** Id do outro participante (só em conversa direta). */
    val destinatarioId: Long?,
    /** Id da última mensagem (0 se não há mensagens). */
    val ultimaMensagemId: Long,
    val ultimaMensagemEm: Instant?,
    /** Texto cru do primeiro conteúdo da última mensagem (ver `PreviaConversa`). */
    val ultimaMensagemTexto: String?,
    val naoLidas: Int,
    /** Posição entre as fixadas (1 = primeira); `null` se não está fixada. */
    val fixadaOrdem: Int?,
    val arquivadaEm: Instant?,
    /** URL assinada (expira em 600 s); só em conversa direta. */
    val avatarUrl: String?,
    /** Grupo: emoji no lugar da inicial (com imagem, vale a imagem). */
    val emoji: String? = null,
    /** Chat de uma ligação: fica na seção "Chamadas (N)" se não está fixado (CON-01). */
    val chamada: Boolean = false,
) {
    /** Mesma regra do web: descrição, senão nome, senão "Conversa #id". */
    val titulo: String
        get() = descricao?.takeIf { it.isNotBlank() } ?: nome?.takeIf { it.isNotBlank() } ?: "Conversa #$id"

    val fixada: Boolean get() = fixadaOrdem != null
    val arquivada: Boolean get() = arquivadaEm != null
}

/** Contato (`GET /usuario/contatos`): hoje o servidor devolve todos os usuários. */
data class Contato(
    val id: Long,
    val nome: String,
    val login: String,
    val email: String?,
    val telefone: String?,
    val avatarUrl: String? = null,
    /** Bot de IA (CON-16): conversa por mensagens comuns, mas nunca atende nem confirma. */
    val bot: Boolean = false,
    /** "Usuário desde" (servidor `ac710eb`); nulo em contato antigo. */
    val criadoEm: Instant? = null,
)

/** Membro de uma conversa (`GET /conversa/usuarios`). */
data class MembroConversa(
    /** Id do vínculo `conversa_usuario` (usado para remover/sair). */
    val vinculoId: Long,
    val usuarioId: Long,
    val nome: String,
    val avatarUrl: String?,
)
