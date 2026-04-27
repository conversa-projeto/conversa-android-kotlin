package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

// PATCH /dispositivo — atualiza info e token FCM
data class AtualizarDispositivoRequest(
    val id: Int? = null, // id do dispositivo (se nulo, backend cria baseado em fingerprint)
    val nome: String? = null,
    val modelo: String? = null,
    @SerializedName("versao_so") val versaoSo: String? = null,
    val plataforma: String? = null, // "android" / "ios" / "web" / "windows"
    @SerializedName("token_fcm") val tokenFcm: String? = null,
)

data class DispositivoResponse(
    val id: Int,
    val nome: String?,
    val modelo: String?,
    @SerializedName("versao_so") val versaoSo: String?,
    val plataforma: String?,
    @SerializedName("token_fcm") val tokenFcm: String?,
    @SerializedName("usuario_id") val usuarioId: Int?,
    val ativo: Boolean = true,
)

// PUT /dispositivo/usuario — vincula dispositivo ao usuario
data class VincularDispositivoRequest(
    @SerializedName("dispositivo_id") val dispositivoId: Int,
)
