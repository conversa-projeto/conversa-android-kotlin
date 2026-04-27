package com.conversa.conversa.data.model

import com.google.gson.annotations.SerializedName

// Configuracao SIP do usuario (GET /sip)
data class SipConfig(
    val id: Int,
    @SerializedName("usuario_id") val usuarioId: Int,
    @SerializedName("sip_user") val sipUser: String,
    @SerializedName("auth_user") val authUser: String?,
    @SerializedName("sip_password") val sipPassword: String,
    @SerializedName("display_name") val displayName: String?,
    val domain: String,
    @SerializedName("ws_server") val wsServer: String,
    val ativo: Boolean,
)

// PUT/PATCH /sip
data class SipRequest(
    @SerializedName("sip_user") val sipUser: String,
    @SerializedName("auth_user") val authUser: String? = null,
    @SerializedName("sip_password") val sipPassword: String,
    @SerializedName("display_name") val displayName: String? = null,
    val domain: String,
    @SerializedName("ws_server") val wsServer: String,
    val ativo: Boolean = true,
)
