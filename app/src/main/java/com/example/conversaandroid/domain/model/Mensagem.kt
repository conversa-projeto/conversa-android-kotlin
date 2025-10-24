package com.example.conversaandroid.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class LadoMensagem {
    ESQUERDO,  // Mensagem recebida
    DIREITO    // Mensagem enviada
}

@Parcelize
data class Mensagem(
    val id: Int,
    val localId: Long = 0L,
    val conversaId: Int,
    val remetenteId: Int,
    val remetenteNome: String = "",
    val inserida: Long = System.currentTimeMillis(),
    val alterada: Long? = null,
    val recebida: Boolean = false,
    val visualizada: Boolean = false,
    val reproduzida: Boolean = false,
    val conteudos: List<Conteudo> = emptyList()
) : Parcelable {

    fun getLado(usuarioAtualId: Int): LadoMensagem {
        return if (remetenteId == usuarioAtualId) {
            LadoMensagem.DIREITO
        } else {
            LadoMensagem.ESQUERDO
        }
    }

    fun getDescricaoSimples(usuarioAtualId: Int, tipoConversa: TipoConversa): String {
        val prefixo = when {
            remetenteId == usuarioAtualId -> "Você: "
            tipoConversa == TipoConversa.GRUPO -> "$remetenteNome: "
            else -> ""
        }

        return if (conteudos.isNotEmpty()) {
            val conteudo = conteudos.first()
            prefixo + when (conteudo.tipo) {
                TipoConteudo.TEXTO -> conteudo.conteudo
                TipoConteudo.IMAGEM -> "📷 Imagem"
                TipoConteudo.ARQUIVO -> "📎 ${conteudo.nome}"
                TipoConteudo.MENSAGEM_AUDIO -> "🎤 Áudio"
                else -> ""
            }
        } else {
            prefixo
        }
    }
}