package com.conversa.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.MotivoFimSessao
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.feature.auth.login.AvisoLogin
import com.conversa.app.navegacao.RotaChat
import com.conversa.app.navegacao.RotaLogin
import com.conversa.app.navegacao.RotaPrincipal
import com.conversa.app.navegacao.RotaServidor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Navegação pedida pelo app (não por um clique): fim da sessão, link para uma conversa. */
sealed interface NavegacaoGlobal {
    data class IrParaLogin(val aviso: AvisoLogin) : NavegacaoGlobal

    data class AbrirConversa(val rota: RotaChat) : NavegacaoGlobal
}

/**
 * Decide a primeira tela (TODO 2.1): sem servidor → "Servidor"; sem sessão → Login;
 * com sessão → Principal. Depois, leva ao login quando a sessão acaba (logout ou
 * 401, AUT-03) e abre conversas vindas de link (`conversa://chat/{id}?mensagem={id}`).
 */
@HiltViewModel
class MainViewModel @Inject constructor(servidor: ServidorRepositorio, private val sessao: SessaoRepositorio) : ViewModel() {
    private val _destinoInicial = MutableStateFlow<Any?>(null)
    val destinoInicial: StateFlow<Any?> = _destinoInicial.asStateFlow()

    val navegacao = EventosUnicos<NavegacaoGlobal>()

    /** Link recebido antes de haver sessão: abre depois do login. */
    private var linkPendente: RotaChat? = null

    init {
        viewModelScope.launch {
            servidor.carregado.first { it }
            sessao.carregada.first { it }
            _destinoInicial.value = when {
                servidor.atual.value == null -> RotaServidor(podeVoltar = false)
                sessao.sessao.value == null -> RotaLogin()
                else -> RotaPrincipal
            }
        }
        viewModelScope.launch {
            sessao.fim.collect { motivo ->
                val aviso = if (motivo == MotivoFimSessao.EXPIROU) AvisoLogin.SESSAO_EXPIRADA else AvisoLogin.NENHUM
                navegacao.enviar(NavegacaoGlobal.IrParaLogin(aviso))
            }
        }
    }

    /** Intent com `conversa://chat/...` (atalho, notificação na etapa 5). */
    fun receberLink(uri: Uri?) {
        val rota = rotaDoLink(uri) ?: return
        viewModelScope.launch {
            sessao.carregada.first { it }
            if (sessao.sessao.value == null) linkPendente = rota else navegacao.enviar(NavegacaoGlobal.AbrirConversa(rota))
        }
    }

    /** Depois do login: abre o link que chegou antes. */
    fun consumirLinkPendente(): RotaChat? = linkPendente.also { linkPendente = null }

    companion object {
        /** `conversa://chat/{conversaId}?mensagem={mensagemId}`. */
        fun rotaDoLink(uri: Uri?): RotaChat? {
            if (uri == null || uri.scheme != "conversa" || uri.host != "chat") return null
            val conversa = uri.pathSegments.firstOrNull()?.toLongOrNull()?.takeIf { it > 0 } ?: return null
            val mensagem = uri.getQueryParameter("mensagem")?.toLongOrNull()?.takeIf { it > 0 } ?: 0
            return RotaChat(conversa, mensagem)
        }
    }
}
