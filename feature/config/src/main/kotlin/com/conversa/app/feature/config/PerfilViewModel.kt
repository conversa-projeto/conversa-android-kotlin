package com.conversa.app.feature.config

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.ErroSenha
import com.conversa.app.core.model.dadosDoPerfilValidos
import com.conversa.app.core.model.validarTrocaDeSenha
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class PerfilUiState(
    val nome: String = "",
    val email: String = "",
    val temFoto: Boolean = false,
    val fotoUrl: String? = null,
    val enviandoFoto: Boolean = false,
    val avisoFoto: Aviso? = null,
    val salvandoDados: Boolean = false,
    val avisoDados: Aviso? = null,
    val salvandoSenha: Boolean = false,
    val avisoSenha: Aviso? = null,
    /** Muda a cada senha alterada: a tela limpa os três campos. */
    val senhasTrocadas: Int = 0,
)

private data class Andamento(
    val enviandoFoto: Boolean = false,
    val avisoFoto: Aviso? = null,
    val salvandoDados: Boolean = false,
    val avisoDados: Aviso? = null,
    val salvandoSenha: Boolean = false,
    val avisoSenha: Aviso? = null,
    val senhasTrocadas: Int = 0,
)

/**
 * Perfil (8.3, FC-802, AUT-06/07/08), como o `ProfileSettingsModal.vue`: a foto (trocar e
 * remover), nome e e-mail, e a senha. As mensagens são as do web; o erro do servidor aparece
 * como veio (ex.: "Senha atual incorreta!").
 */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val perfil: PerfilRepositorio,
    private val preparador: PreparadorFotoDePerfil,
    relogio: Clock,
) : ViewModel() {
    private val andamento = MutableStateFlow(Andamento())
    private val foto = FotoDaSessao(perfil, relogio, viewModelScope)

    val estado: StateFlow<PerfilUiState> = combine(perfil.sessao, andamento, foto.url) { sessao, a, url ->
        PerfilUiState(
            nome = sessao?.nome.orEmpty(),
            email = sessao?.email.orEmpty(),
            temFoto = sessao?.avatarIdentificador != null,
            fotoUrl = url.takeIf { sessao?.avatarIdentificador != null },
            enviandoFoto = a.enviandoFoto,
            avisoFoto = a.avisoFoto,
            salvandoDados = a.salvandoDados,
            avisoDados = a.avisoDados,
            salvandoSenha = a.salvandoSenha,
            avisoSenha = a.avisoSenha,
            senhasTrocadas = a.senhasTrocadas,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilUiState())

    /** A imagem não carregou (URL vencida): busca outra, no máximo a cada 30 s. */
    fun fotoFalhou() = foto.falhou()

    fun trocarFoto(uri: Uri) {
        if (andamento.value.enviandoFoto) return
        andamento.update { it.copy(enviandoFoto = true, avisoFoto = null) }
        viewModelScope.launch {
            val jpeg = preparador.preparar(uri)
            val aviso = if (jpeg == null) {
                Aviso(ok = false, recurso = R.string.perfil_foto_ilegivel)
            } else {
                perfil.trocarFoto(jpeg).exceptionOrNull()?.let { avisoDeErro(it, R.string.perfil_foto_falhou) }
            }
            andamento.update { it.copy(enviandoFoto = false, avisoFoto = aviso) }
        }
    }

    fun removerFoto() {
        if (andamento.value.enviandoFoto) return
        andamento.update { it.copy(enviandoFoto = true, avisoFoto = null) }
        viewModelScope.launch {
            val aviso = perfil.removerFoto().exceptionOrNull()?.let { avisoDeErro(it, R.string.perfil_remover_falhou) }
            andamento.update { it.copy(enviandoFoto = false, avisoFoto = aviso) }
        }
    }

    fun salvarDados(nome: String, email: String) {
        if (!dadosDoPerfilValidos(nome, email)) {
            andamento.update { it.copy(avisoDados = Aviso(ok = false, recurso = R.string.perfil_preencha_dados)) }
            return
        }
        andamento.update { it.copy(salvandoDados = true, avisoDados = null) }
        viewModelScope.launch {
            val aviso = perfil.alterarDados(nome, email).fold(
                onSuccess = { Aviso(ok = true, recurso = R.string.perfil_dados_salvos) },
                onFailure = { avisoDeErro(it, R.string.perfil_dados_falhou) },
            )
            andamento.update { it.copy(salvandoDados = false, avisoDados = aviso) }
        }
    }

    fun salvarSenha(atual: String, nova: String, confirmacao: String) {
        validarTrocaDeSenha(atual, nova, confirmacao)?.let { erro ->
            val recurso = when (erro) {
                ErroSenha.CAMPOS_VAZIOS -> R.string.perfil_senha_campos
                ErroSenha.CURTA -> R.string.perfil_senha_curta
                ErroSenha.NAO_CONFERE -> R.string.perfil_senha_nao_confere
            }
            andamento.update { it.copy(avisoSenha = Aviso(ok = false, recurso = recurso)) }
            return
        }
        andamento.update { it.copy(salvandoSenha = true, avisoSenha = null) }
        viewModelScope.launch {
            perfil.alterarSenha(atual, nova).fold(
                onSuccess = {
                    andamento.update {
                        it.copy(
                            salvandoSenha = false,
                            avisoSenha = Aviso(ok = true, recurso = R.string.perfil_senha_alterada),
                            senhasTrocadas = it.senhasTrocadas + 1,
                        )
                    }
                },
                onFailure = { falha ->
                    andamento.update { it.copy(salvandoSenha = false, avisoSenha = avisoDeErro(falha, R.string.perfil_senha_falhou)) }
                },
            )
        }
    }
}
