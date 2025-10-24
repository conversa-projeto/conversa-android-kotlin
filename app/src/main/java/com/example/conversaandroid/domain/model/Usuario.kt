package com.example.conversaandroid.domain.model


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Usuario(
    val id: Int,
    val nome: String = "",
    val login: String = "",
    val email: String = "",
    val telefone: String = ""
) : Parcelable {
    fun abreviatura(): String = nome.firstOrNull()?.toString()?.uppercase() ?: "?"
}