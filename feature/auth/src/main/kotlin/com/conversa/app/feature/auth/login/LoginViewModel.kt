package com.conversa.app.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.autenticacao.RespostaLoginInvalidaException
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.estado.EventosUnicos
import com.conversa.app.core.ui.estado.TextoUi
import com.conversa.app.feature.auth.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val usuario: String = "",
    val senha: String = "",
    val entrando: Boolean = false,
    val erro: TextoUi? = null,
    /** Endereço do servidor atual, mostrado discretamente (com o link para trocar). */
    val servidor: String = "",
)

sealed interface EventoLogin {
    data object Entrou : EventoLogin
}

/**
 * Tela de login (AUT-01). A senha só fica no estado da tela enquanto ela existe;
 * não é salva em lugar nenhum.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(private val autenticacao: AutenticacaoRepositorio, servidor: ServidorRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(LoginUiState(servidor = servidor.atual.value?.base?.host.orEmpty()))
    val estado: StateFlow<LoginUiState> = _estado.asStateFlow()

    val eventos = EventosUnicos<EventoLogin>()

    init {
        viewModelScope.launch {
            val ultimo = autenticacao.ultimoLogin.first().orEmpty()
            _estado.update { if (it.usuario.isEmpty()) it.copy(usuario = ultimo) else it }
        }
        viewModelScope.launch {
            servidor.atual.collect { config -> _estado.update { it.copy(servidor = config?.base?.host.orEmpty()) } }
        }
    }

    /** Usuário vindo do cadastro: tem prioridade sobre o último login. */
    fun preencherUsuario(usuario: String) {
        if (usuario.isNotBlank()) _estado.update { it.copy(usuario = usuario) }
    }

    fun alterarUsuario(texto: String) = _estado.update { it.copy(usuario = texto, erro = null) }

    fun alterarSenha(texto: String) = _estado.update { it.copy(senha = texto, erro = null) }

    fun entrar() {
        val atual = _estado.value
        if (atual.entrando) return
        if (atual.usuario.isBlank() || atual.senha.isEmpty()) {
            _estado.update { it.copy(erro = TextoUi.Recurso(R.string.login_preencha)) }
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(entrando = true, erro = null) }
            autenticacao.entrar(atual.usuario, atual.senha)
                .onSuccess {
                    _estado.update { it.copy(entrando = false, senha = "") }
                    eventos.enviar(EventoLogin.Entrou)
                }
                .onFailure { erro ->
                    val texto = when (erro) {
                        is RespostaLoginInvalidaException -> TextoUi.Recurso(R.string.login_resposta_invalida)
                        else -> TextoUi.Literal(erro.paraErroApi().mensagemAmigavel())
                    }
                    _estado.update { it.copy(entrando = false, erro = texto) }
                }
        }
    }
}
