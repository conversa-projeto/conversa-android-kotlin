package com.example.conversaandroid.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class TipoConteudo(val value: Int) {
    NENHUM(0),
    TEXTO(1),
    IMAGEM(2),
    ARQUIVO(3),
    MENSAGEM_AUDIO(4);

    companion object {
        fun fromValue(value: Int): TipoConteudo = values().find { it.value == value } ?: NENHUM
    }
}

@Parcelize
data class Conteudo(
    val id: Int = 0,
    val tipo: TipoConteudo = TipoConteudo.NENHUM,
    val ordem: Int = 0,
    val conteudo: String = "",
    val nome: String = "",
    val extensao: String = "",
    val identificador: String? = null
) : Parcelable
