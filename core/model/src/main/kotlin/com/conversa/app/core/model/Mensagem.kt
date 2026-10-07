package com.conversa.app.core.model

import java.time.Instant

/** Tipos de conteúdo de mensagem (contrato §10.1). */
enum class TipoConteudo(val codigo: Int) {
    TEXTO(1),
    IMAGEM(2),
    ARQUIVO(3),
    AUDIO(4),
    GRAVACAO_AUDIO(5),
    CHAMADA(6),
    FIGURINHA(7),
    ENQUETE(8),
    DESCONHECIDO(-1),
    ;

    val ehAnexo: Boolean get() = this == IMAGEM || this == ARQUIVO || this == AUDIO || this == GRAVACAO_AUDIO

    companion object {
        fun de(codigo: Int): TipoConteudo = entries.firstOrNull { it.codigo == codigo } ?: DESCONHECIDO
    }
}

/** Situação da transcrição de áudio (contrato §8.6). */
enum class StatusTranscricao(val codigo: Int) {
    NENHUMA(0),
    PROCESSANDO(1),
    CONCLUIDA(2),
    ERRO(3),
    ;

    companion object {
        fun de(codigo: Int): StatusTranscricao = entries.firstOrNull { it.codigo == codigo } ?: NENHUMA
    }
}

data class Conteudo(
    val id: Long?,
    val ordem: Int,
    val tipo: TipoConteudo,
    /** Texto (tipo 1), identificador do anexo (2–5), JSON (6), `pacote/nome` (7) ou id da enquete (8). */
    val conteudo: String,
    val nome: String = "",
    val extensao: String = "",
    val transcricaoStatus: StatusTranscricao = StatusTranscricao.NENHUMA,
    val transcricao: String = "",
)

enum class TipoReferencia(val codigo: Int) {
    RESPOSTA(1),
    ENCAMINHAMENTO(2),
    ;

    companion object {
        fun de(codigo: Int): TipoReferencia = entries.firstOrNull { it.codigo == codigo } ?: RESPOSTA
    }
}

/** Mensagem citada (resposta) ou encaminhada; a cadeia vai até 5 níveis. */
data class MensagemResumida(
    val id: Long,
    val conversaId: Long,
    val remetente: String,
    val inserida: Instant?,
    val excluidaEm: Instant?,
    val conteudos: List<Conteudo>,
    val referencia: ReferenciaMensagem?,
) {
    val oculta: Boolean get() = excluidaEm != null
}

data class ReferenciaMensagem(
    val tipo: TipoReferencia,
    /** Pode faltar se a mensagem referenciada não existe mais. */
    val mensagem: MensagemResumida?,
)

data class UsuarioReacao(val usuarioId: Long, val nome: String, val reagidoEm: Instant?, val avatarUrl: String?)

data class Reacao(
    val emoji: String,
    val quantidade: Int,
    /** O usuário logado reagiu com este emoji. */
    val reagiu: Boolean,
    val usuarios: List<UsuarioReacao>,
)

/** Mensagem como vem de `GET /mensagens` (contrato §10.2). */
data class Mensagem(
    /** Negativo enquanto é uma mensagem otimista ainda não salva no servidor. */
    val id: Long,
    val remetenteId: Long,
    /** Só o primeiro nome do remetente. */
    val remetente: String,
    val conversaId: Long,
    val inserida: Instant,
    val visivelEm: Instant?,
    val excluidaEm: Instant?,
    val referencia: ReferenciaMensagem?,
    val recebida: Boolean,
    val visualizada: Boolean,
    val reproduzida: Boolean,
    val conteudos: List<Conteudo>,
    val reacoes: List<Reacao> = emptyList(),
    /** Otimista, ainda saindo (só no aparelho). */
    val enviando: Boolean = false,
    /** O envio desistiu (erro definitivo): mostra "Reenviar"/"Apagar". */
    val falhou: Boolean = false,
) {
    /** "Mensagem oculta" (antigo excluir): o conteúdo continua vindo, mas não é exibido. */
    val oculta: Boolean get() = excluidaEm != null

    /** Data usada para ordenar: `coalesce(visivel_em, inserida)`. */
    val dataEfetiva: Instant get() = visivelEm ?: inserida
}
