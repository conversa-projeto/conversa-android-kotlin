package com.conversa.app.feature.auth.cadastro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.autenticacao.EmailJaCadastradoException
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CadastroUiState(
    val nome: String = "",
    val usuario: String = "",
    val email: String = "",
    val senha: String = "",
    val cadastrando: Boolean = false,
    val erro: TextoUi? = null,
)

sealed interface EventoCadastro {
    /** Conta criada: volta ao login com o usuário preenchido. */
    data class Criada(val usuario: String) : EventoCadastro
}

/**
 * "Crie sua conta" (AUT-02): `PUT /usuario` (rota pública). Os campos são cortados
 * nos limites das colunas (nome 100, login 50, e-mail 100), senão o servidor dá 500.
 */
@HiltViewModel
class CadastroViewModel @Inject constructor(private val autenticacao: AutenticacaoRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(CadastroUiState())
    val estado: StateFlow<CadastroUiState> = _estado.asStateFlow()

    val eventos = EventosUnicos<EventoCadastro>()

    fun alterarNome(texto: String) = _estado.update { it.copy(nome = texto.take(LIMITE_NOME), erro = null) }

    fun alterarUsuario(texto: String) = _estado.update { it.copy(usuario = texto.take(LIMITE_LOGIN), erro = null) }

    fun alterarEmail(texto: String) = _estado.update { it.copy(email = texto.take(LIMITE_EMAIL), erro = null) }

    fun alterarSenha(texto: String) = _estado.update { it.copy(senha = texto, erro = null) }

    fun cadastrar() {
        val atual = _estado.value
        if (atual.cadastrando) return
        val erro = validar(atual)
        if (erro != null) {
            _estado.update { it.copy(erro = TextoUi.Recurso(erro)) }
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(cadastrando = true, erro = null) }
            autenticacao.cadastrar(atual.nome, atual.usuario, atual.email, atual.senha)
                .onSuccess {
                    _estado.update { it.copy(cadastrando = false, senha = "") }
                    eventos.enviar(EventoCadastro.Criada(atual.usuario.trim()))
                }
                .onFailure { falha ->
                    val texto = when (falha) {
                        is EmailJaCadastradoException -> TextoUi.Recurso(R.string.cadastro_email_repetido)
                        else -> TextoUi.Literal(falha.paraErroApi().mensagemAmigavel())
                    }
                    _estado.update { it.copy(cadastrando = false, erro = texto) }
                }
        }
    }

    private fun validar(estado: CadastroUiState): Int? = when {
        estado.nome.isBlank() || estado.usuario.isBlank() || estado.email.isBlank() || estado.senha.isEmpty() ->
            R.string.cadastro_preencha
        !EMAIL.matches(estado.email.trim()) -> R.string.cadastro_email_invalido
        estado.senha.length < SENHA_MINIMA -> R.string.cadastro_senha_curta
        else -> null
    }

    companion object {
        const val LIMITE_NOME = 100
        const val LIMITE_LOGIN = 50
        const val LIMITE_EMAIL = 100

        /** O web usa `minlength=4` no campo de senha do cadastro. */
        const val SENHA_MINIMA = 4
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
