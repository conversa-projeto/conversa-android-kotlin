package com.example.conversaandroid.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Dispositivo(
    val id: Int,
    val nome: String = "",
    val modelo: String = "",
    val versaoSO: String = "",
    val plataforma: String = "Android",
    val tokenFCM: String? = null,
    val ativo: Boolean = true
) : Parcelable