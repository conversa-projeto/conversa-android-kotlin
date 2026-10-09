package com.conversa.app.feature.config

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi

/** Um aviso embaixo de um bloco: o texto do app ([recurso]) ou o que o servidor disse ([texto]). */
@Immutable
data class Aviso(val ok: Boolean, @StringRes val recurso: Int? = null, val texto: String? = null)

/**
 * A mensagem do servidor quando ele explica (400/403/409…), como o web mostra
 * ("Senha atual incorreta!", "Ao menos uma pessoa precisa…"); senão a do app, ou a de rede.
 */
internal fun avisoDeErro(falha: Throwable, @StringRes padrao: Int): Aviso = when (val erro = falha.paraErroApi()) {
    is ErroApi.Servidor -> if (erro.status < 500 && erro.detalhe.isNotBlank()) {
        Aviso(ok = false, texto = erro.detalhe)
    } else {
        Aviso(ok = false, recurso = padrao)
    }
    else -> Aviso(ok = false, texto = erro.mensagemAmigavel())
}
