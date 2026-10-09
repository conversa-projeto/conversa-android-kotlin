package com.conversa.app.feature.config

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.model.ErroSenha
import com.conversa.app.core.model.dadosDoPerfilValidos
import com.conversa.app.core.model.validarTrocaDeSenha
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Um aviso embaixo de um bloco: o texto do app ([recurso]) ou o que o servidor disse ([texto]). */
@Immutable
data class Aviso(val ok: Boolean, @StringRes val recurso: Int? = null, val texto: String? = null)

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
    val fotoUrl: String? = null,
)

/** Renovar a URL da foto no máximo a cada 30 s (como o `renovarAvatarExpirado` do web). */
private val INTERVALO_RENOVAR_FOTO: Duration = Duration.ofSeconds(30)

/**
 * Perfil (8.3, FC-802, AUT-06/07/08), como o `ProfileSettingsModal.vue`: a foto (trocar e
 * remover), nome e e-mail, e a senha. As mensagens são as do web; o erro do servidor aparece
 * como veio (ex.: "Senha atual incorreta!").
 */
@HiltViewModel
class PerfilViewModel @Inject constructor(
    private val perfil: PerfilRepositorio,
    private val preparador: PreparadorFotoDePerfil,
    private val relogio: Clock,
) : ViewModel() {
    private val andamento = MutableStateFlow(Andamento())
    private var renovadaEm: Instant? = null

    val estado: StateFlow<PerfilUiState> = combine(perfil.sessao, andamento) { sessao, a ->
        PerfilUiState(
            nome = sessao?.nome.orEmpty(),
            email = sessao?.email.orEmpty(),
            temFoto = sessao?.avatarIdentificador != null,
            fotoUrl = a.fotoUrl.takeIf { sessao?.avatarIdentificador != null },
            enviandoFoto = a.enviandoFoto,
            avisoFoto = a.avisoFoto,
            salvandoDados = a.salvandoDados,
            avisoDados = a.avisoDados,
            salvandoSenha = a.salvandoSenha,
            avisoSenha = a.avisoSenha,
            senhasTrocadas = a.senhasTrocadas,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerfilUiState())

    init {
        // A URL assinada da foto acompanha o identificador da sessão.
        viewModelScope.launch {
            perfil.sessao.map { it?.avatarIdentificador }.distinctUntilChanged().collect { carregarUrl(it) }
        }
    }

    private suspend fun carregarUrl(identificador: String?) {
        val url = identificador?.let { perfil.urlDaFoto(it).getOrNull() }
        andamento.update { it.copy(fotoUrl = url) }
    }

    /** A imagem não carregou (URL vencida): busca outra, no máximo a cada 30 s. */
    fun fotoFalhou() {
        val identificador = perfil.sessao.value?.avatarIdentificador ?: return
        val agora = relogio.instant()
        val ultima = renovadaEm
        if (ultima != null && Duration.between(ultima, agora) < INTERVALO_RENOVAR_FOTO) return
        renovadaEm = agora
        perfil.esquecerUrl(identificador)
        viewModelScope.launch { carregarUrl(identificador) }
    }

    fun trocarFoto(uri: Uri) {
        if (andamento.value.enviandoFoto) return
        andamento.update { it.copy(enviandoFoto = true, avisoFoto = null) }
        viewModelScope.launch {
            val jpeg = preparador.preparar(uri)
            val aviso = if (jpeg == null) {
                Aviso(ok = false, recurso = R.string.perfil_foto_ilegivel)
            } else {
                perfil.trocarFoto(jpeg).exceptionOrNull()?.let { erroDoServidor(it, R.string.perfil_foto_falhou) }
            }
            andamento.update { it.copy(enviandoFoto = false, avisoFoto = aviso) }
        }
    }

    fun removerFoto() {
        if (andamento.value.enviandoFoto) return
        andamento.update { it.copy(enviandoFoto = true, avisoFoto = null) }
        viewModelScope.launch {
            val aviso = perfil.removerFoto().exceptionOrNull()?.let { erroDoServidor(it, R.string.perfil_remover_falhou) }
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
                onFailure = { erroDoServidor(it, R.string.perfil_dados_falhou) },
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
                    andamento.update { it.copy(salvandoSenha = false, avisoSenha = erroDoServidor(falha, R.string.perfil_senha_falhou)) }
                },
            )
        }
    }

    /** A mensagem do servidor quando ele explica (400/403/409…); senão a do app, ou a de rede. */
    private fun erroDoServidor(falha: Throwable, @StringRes padrao: Int): Aviso = when (val erro = falha.paraErroApi()) {
        is ErroApi.Servidor -> if (erro.status < 500 && erro.detalhe.isNotBlank()) {
            Aviso(ok = false, texto = erro.detalhe)
        } else {
            Aviso(ok = false, recurso = padrao)
        }
        else -> Aviso(ok = false, texto = erro.mensagemAmigavel())
    }
}
