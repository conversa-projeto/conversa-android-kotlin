package com.conversa.app.feature.config

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.perfil.PerfilRepositorio
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.model.PreferenciaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ConfiguracoesUiState(
    val nome: String = "",
    val email: String? = null,
    val fotoUrl: String? = null,
    val servidor: String = "",
    val tema: PreferenciaTema = PreferenciaTema.PADRAO,
    val saindo: Boolean = false,
)

/**
 * Aba Configurações (8.5, FC-804, CFG-01): quem está logado (abre o perfil), as seções e
 * "Sair" (AUT-05). O app volta ao login quando a sessão acaba (`MainViewModel`).
 */
@HiltViewModel
class ConfiguracoesViewModel @Inject constructor(
    perfil: PerfilRepositorio,
    servidor: ServidorRepositorio,
    private val autenticacao: AutenticacaoRepositorio,
    private val preferencias: PreferenciasRepositorio,
    relogio: Clock,
) : ViewModel() {
    private val foto = FotoDaSessao(perfil, relogio, viewModelScope)
    private val saindo = MutableStateFlow(false)

    val estado: StateFlow<ConfiguracoesUiState> = combine(
        perfil.sessao,
        foto.url,
        servidor.atual,
        preferencias.tema,
        saindo,
    ) { sessao, url, config, tema, saindoAgora ->
        ConfiguracoesUiState(
            nome = sessao?.nome.orEmpty(),
            email = sessao?.email,
            fotoUrl = url.takeIf { sessao?.avatarIdentificador != null },
            servidor = config?.base?.toString().orEmpty(),
            tema = tema,
            saindo = saindoAgora,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfiguracoesUiState())

    /** A foto não carregou (URL vencida): busca outra, no máximo a cada 30 s. */
    fun fotoFalhou() = foto.falhou()

    fun alterarTema(tema: PreferenciaTema) {
        viewModelScope.launch { preferencias.alterarTema(tema) }
    }

    fun sair() {
        if (saindo.value) return
        saindo.value = true
        viewModelScope.launch { autenticacao.sair() }
    }
}
