package com.conversa.app.core.network.dto

import com.conversa.app.core.model.Pasta
import com.conversa.app.core.model.StatusSugestoes
import com.conversa.app.core.model.SugestaoPasta
import com.conversa.app.core.model.SugestoesPastas
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Pastas de mensagens da conversa direta (contrato §10.17, migração 056).

/** `PastaResposta`: o mesmo em `GET /pastas`, `PUT /pasta` e `PATCH /pasta`. */
@Serializable
data class PastaDto(
    val id: Long,
    @SerialName("conversa_id") val conversaId: Long,
    @SerialName("dono_id") val donoId: Long,
    val nome: String,
    val cor: String = "",
    val compartilhada: Boolean = false,
    val mensagens: Int = 0,
)

fun PastaDto.paraModelo() = Pasta(id, conversaId, donoId, nome, cor, compartilhada, mensagens)

@Serializable
data class CriarPastaRequisicao(
    @SerialName("conversa_id") val conversaId: Long,
    val nome: String,
    val cor: String,
    val compartilhada: Boolean,
)

/** Só o que vem muda (`cor: ""` tira a cor); os nulos não vão (`explicitNulls = false`). */
@Serializable
data class AlterarPastaRequisicao(val id: Long, val nome: String? = null, val cor: String? = null, val compartilhada: Boolean? = null)

/** Pôr ou tirar mensagens (1 a 500 ids); no tirar, vai como corpo do DELETE. */
@Serializable
data class MensagensDaPastaRequisicao(@SerialName("pasta_id") val pastaId: Long, val mensagens: List<Long>)

@Serializable
data class PorNaPastaDto(@SerialName("pasta_id") val pastaId: Long, val incluidas: Int = 0)

@Serializable
data class TirarDaPastaDto(@SerialName("pasta_id") val pastaId: Long, val tiradas: Int = 0)

/** `POST /pastas/sugerir` (🆕 `b104db8`): [ignorar] são as que a pessoa já dispensou (até 2000; nunca `null`). */
@Serializable
data class PedirSugestoesPastasRequisicao(@SerialName("conversa_id") val conversaId: Long, val ignorar: List<Long> = emptyList())

@Serializable
data class SugestaoPastaDto(
    @SerialName("mensagem_id") val mensagemId: Long,
    @SerialName("pasta_id") val pastaId: Long,
    val autor: String = "",
    val texto: String = "",
)

/** O mesmo objeto no `POST /pastas/sugerir` e no `GET /pastas/sugestao` (contrato §10.17.1). */
@Serializable
data class SugestoesPastasDto(
    val id: String,
    @SerialName("conversa_id") val conversaId: Long = 0,
    val status: String = "",
    val analisadas: Int = 0,
    val sugestoes: List<SugestaoPastaDto> = emptyList(),
    val erro: String = "",
)

fun SugestoesPastasDto.paraModelo() = SugestoesPastas(
    id = id,
    status = when (status) {
        "processando" -> StatusSugestoes.PROCESSANDO
        "concluido" -> StatusSugestoes.CONCLUIDO
        else -> StatusSugestoes.ERRO
    },
    analisadas = analisadas,
    sugestoes = sugestoes.map { SugestaoPasta(it.mensagemId, it.pastaId, it.autor, it.texto) },
    erro = erro,
)
