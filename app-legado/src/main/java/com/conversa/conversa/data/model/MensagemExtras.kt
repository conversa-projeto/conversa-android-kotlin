package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

// Reacao (emoji) — PUT /mensagem/reacao
data class ReacaoRequest(
    @SerializedName("mensagem_id") val mensagemId: Int,
    val emoji: String,
)

data class ReacaoResponse(
    val id: Int,
    @SerializedName("mensagem_id") val mensagemId: Int,
    @SerializedName("usuario_id") val usuarioId: Int,
    val emoji: String,
    val acao: String, // "add" ou "remove"
)

// Referencia de mensagem (responder/encaminhar)
data class MensagemReferencia(
    val id: Int? = null,
    val tipo: Int, // 1=Responder, 2=Encaminhar
    @SerializedName("origem_mensagem_id") val origemMensagemId: Int? = null,
    @SerializedName("destino_mensagem_id") val destinoMensagemId: Int,
) {
    companion object {
        const val TIPO_RESPONDER = 1
        const val TIPO_ENCAMINHAR = 2
    }
}

// Request extendido de envio de mensagem com referencia
data class EnviarMensagemComReferenciaRequest(
    @SerializedName("conversa_id") val conversaId: Int,
    val conteudos: List<ConteudoRequest>,
    val referencia: MensagemReferencia? = null,
    @SerializedName("visivel_em") val visivelEm: String? = null, // ISO-8601 para agendamento
)

// Sync incremental — GET /mensagens/novas?desde=<timestamp>
data class MensagemNovaItem(
    @SerializedName("conversa_id") val conversaId: Int,
    @SerializedName("mensagem_id") val mensagemId: Int,
    val ate: String, // cursor timestamp para proximo fetch
)

// Status detalhado de uma mensagem — GET /mensagem/status
data class MensagemStatusResponse(
    @SerializedName("mensagem_id") val mensagemId: Int,
    @SerializedName("usuario_id") val usuarioId: Int,
    val recebida: String?, // timestamp ou null
    val visualizada: String?,
    val reproduzida: String?,
)

// Busca de mensagens — GET /pesquisar
data class PesquisaResultado(
    @SerializedName("mensagem_id") val mensagemId: Int,
    @SerializedName("conversa_id") val conversaId: Int,
    @SerializedName("conversa_descricao") val conversaDescricao: String?,
    @SerializedName("usuario_id") val usuarioId: Int,
    @SerializedName("usuario_nome") val usuarioNome: String,
    val inserida: String,
    val trecho: String, // snippet com match destacado
)

// Broadcast de digitando/gravando — POST /conversa/digitando ou /conversa/gravando
data class DigitandoRequest(
    // Backend lê `id` (POST /conversa/digitando e /gravando: GetValue<Integer>('id')), igual ao web.
    @SerializedName("id") val conversaId: Int,
)

// Contato (adicao/remocao) — PUT /usuario/contato?relacionamento_id=... | DELETE /usuario/contato?id=...
data class AdicionarContatoRequest(
    @SerializedName("relacionamento_id") val relacionamentoId: Int,
)

// GET /contatos/online — array de IDs
// nao precisa DTO, retorno List<Int>
