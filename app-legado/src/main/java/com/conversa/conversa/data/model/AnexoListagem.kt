package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

// GET /anexos?conversa=&autor=&direcao=&tipos=&antes=&limite=
data class AnexoListItem(
    val id: Int,
    val identificador: String,
    val tipo: Int, // 2=Imagem, 3=Arquivo, 4=Audio, etc.
    val tamanho: Long,
    val nome: String?,
    val extensao: String?,
    @SerializedName("criado_em") val criadoEm: String,
    @SerializedName("criado_por") val criadoPor: Int,
    @SerializedName("criado_por_nome") val criadoPorNome: String?,
    @SerializedName("conversa_id") val conversaId: Int,
    @SerializedName("mensagem_id") val mensagemId: Int?,
    @SerializedName("url_download") val urlDownload: String?,
)
