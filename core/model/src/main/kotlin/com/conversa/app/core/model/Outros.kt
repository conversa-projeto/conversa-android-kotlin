package com.conversa.app.core.model

import java.time.Instant

/** Tipos de atividade (contrato §12). */
enum class TipoAtividade(val codigo: Int) {
    REACAO(1),
    RESPOSTA(2),
    MENCAO(3),
    CHAMADA_PERDIDA(4),
    DESCONHECIDO(-1),
    ;

    companion object {
        fun de(codigo: Int): TipoAtividade = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}

data class Atividade(
    val id: Long,
    val tipo: TipoAtividade,
    val criadoEm: Instant?,
    val nova: Boolean,
    val autorId: Long?,
    val autorNome: String?,
    val autorAvatarUrl: String?,
    val conversaId: Long?,
    val conversaTipo: TipoConversa?,
    val conversaDescricao: String?,
    val mensagemId: Long?,
    val conteudoTipo: TipoConteudo?,
    val texto: String?,
    val chamadaId: Long?,
    val chamadaTipo: TipoChamada?,
    val emoji: String?,
)

/** Votação em grupo (`GET /enquete`, contrato §10.13). */
data class Enquete(
    val id: Long,
    val conversaId: Long,
    val mensagemId: Long?,
    val pergunta: String,
    val multipla: Boolean,
    val criadoPor: Long?,
    val opcoes: List<OpcaoEnquete>,
    /** Pessoas distintas que votaram (na múltipla, cada uma conta uma vez). */
    val totalVotantes: Int,
    /** Ids das opções em que o usuário logado votou. */
    val meusVotos: List<Long>,
)

data class OpcaoEnquete(val id: Long, val texto: String, val votantes: List<Votante>)

data class Votante(val id: Long, val nome: String)
