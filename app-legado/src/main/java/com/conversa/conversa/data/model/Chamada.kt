package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

/**
 * Modelos para o sistema de chamadas
 */

// Request para iniciar chamada
data class IniciarChamadaRequest(
    val tipo: Int, // 1 = Simples (1:1), 2 = Grupo
    val usuarios: List<UsuarioIdDto>
)

data class UsuarioIdDto(
    val id: Int
)

// Response com dados da chamada
data class ChamadaResponse(
    val id: Int,
    val iniciada: String?,
    val finalizada: String?,
    val tipo: Int,
    val status: Int, // 1=Pendente, 2=Recusada, 3=EmAndamento, 4=Encerrada, 5=NãoAtendida, 6=Cancelada
    @SerializedName("criado_em") val criadoEm: String,
    @SerializedName("criado_por") val criadoPor: Int,
    val usuarios: List<UsuarioChamada>
)

data class UsuarioChamada(
    @SerializedName("usuario_id") val usuarioId: Int,
    @SerializedName("usuario_nome") val usuarioNome: String,
    val status: Int, // 1=Pendente, 2=Recusado, 3=Entrou, 4=Saiu
    @SerializedName("adicionado_por") val adicionadoPor: Int,
    @SerializedName("adicionado_por_nome") val adicionadoPorNome: String,
    @SerializedName("adicionado_em") val adicionadoEm: String,
    @SerializedName("entrou_em") val entrouEm: String?
)

// Request simples com ID da chamada
data class ChamadaIdRequest(
    val id: Int
)

// Estados da chamada
enum class ChamadaStatus(val valor: Int) {
    PENDENTE(1),
    RECUSADA(2),
    EM_ANDAMENTO(3),
    ENCERRADA(4),
    NAO_ATENDIDA(5),
    CANCELADA(6);
    
    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.valor == value }
    }
}

// Estados do usuário na chamada
enum class UsuarioChamadaStatus(val valor: Int) {
    PENDENTE(1),
    RECUSADO(2),
    ENTROU(3),
    SAIU(4);
    
    companion object {
        fun fromInt(value: Int) = values().firstOrNull { it.valor == value }
    }
}

// Estado local da chamada (no cliente)
enum class EstadoChamadaLocal {
    DESCONHECIDO,
    INICIANDO_CHAMADA,
    RECEBENDO_CHAMADA,
    CHAMADA_EM_ANDAMENTO,
    CHAMADA_FINALIZADA,
    CHAMADA_PERDIDA,
    RECUSADA
}
