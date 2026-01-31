package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

/**
 * Representa um registro de chamada no historico
 * Mapeado da consulta SQL da rota /chamadas
 * Datas sao convertidas de UTC para local pelo UtcToLocalDateTimeDeserializer
 */
data class HistoricoChamada(
    @SerializedName("chamada_id") val chamadaId: Int,
    @SerializedName("iniciada") val iniciada: LocalDateTime?,
    @SerializedName("finalizada") val finalizada: LocalDateTime?,
    @SerializedName("conversa_id") val conversaId: Int?,
    @SerializedName("tipo_chamada") val tipoChamada: Int, // 1=Simples, 2=Grupo
    @SerializedName("status_chamada") val statusChamada: Int, // 1-6
    @SerializedName("criado_por_id") val criadoPorId: Int,
    @SerializedName("criado_por") val criadoPor: String,
    @SerializedName("usuario_exibido_id") val usuarioId: Int?,
    @SerializedName("usuario_exibido_nome") val usuarioNome: String?,
    @SerializedName("recusou_em") val recusouEm: LocalDateTime?,
    @SerializedName("entrou_em") val entrouEm: LocalDateTime?,
    @SerializedName("saiu_em") val saiuEm: LocalDateTime?,
    @SerializedName("status_usuario") val statusUsuario: Int, // 1-5
    @SerializedName("adicionado_em") val adicionadoEm: LocalDateTime?,
    @SerializedName("adicionado_por_id") val adicionadoPorId: Int?,
    @SerializedName("adicionado_por") val adicionadoPor: String?,
    @SerializedName("tipo_acao") val tipoAcao: Int // 1=Realizada, 2=Recebida, 3=Desconhecido
)

/**
 * Enum para tipo de acao da chamada
 */
enum class TipoAcaoChamada(val valor: Int) {
    REALIZADA(1),
    RECEBIDA(2),
    DESCONHECIDO(3);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.valor == value } ?: DESCONHECIDO
    }
}

/**
 * Enum para status do usuario na chamada (expandido)
 */
enum class StatusUsuarioHistorico(val valor: Int) {
    PENDENTE(1),
    RECUSOU(2),
    ENTROU(3),
    SAIU(4),
    DESCONECTOU(5);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.valor == value }
    }
}
