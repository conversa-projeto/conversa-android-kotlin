package com.conversa.app.feature.auth.servidor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ResultadoTesteServidor
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.ui.estado.EventosUnicos
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensagens da tela (textos ficam em strings.xml; aqui só o tipo). */
enum class AvisoServidor {
    ENDERECO_INVALIDO,
    CONEXAO_OK,
    NAO_EH_CONVERSA,
    CERTIFICADO_INVALIDO,
    HOST_NAO_ENCONTRADO,
    INDISPONIVEL,
    SEM_CONEXAO,
}

data class ServidorUiState(
    val endereco: String = "",
    val testando: Boolean = false,
    val salvando: Boolean = false,
    val aviso: AvisoServidor? = null,
    /** Endereço como será usado (normalizado), para mostrar abaixo do campo. */
    val enderecoNormalizado: String? = null,
)

sealed interface EventoServidor {
    data object Salvo : EventoServidor
}

/**
 * Tela "Servidor" (AND-01): a pessoa digita o endereço, testa e salva.
 * Salvar sempre testa antes; só grava se o endereço responde como um servidor Conversa.
 */
@HiltViewModel
class ServidorViewModel @Inject constructor(private val repositorio: ServidorRepositorio) : ViewModel() {
    private val _estado = MutableStateFlow(ServidorUiState())
    val estado: StateFlow<ServidorUiState> = _estado.asStateFlow()

    val eventos = EventosUnicos<EventoServidor>()

    init {
        viewModelScope.launch {
            repositorio.carregado.first { it }
            val atual = repositorio.atual.value?.base?.toString().orEmpty()
            if (_estado.value.endereco.isEmpty() && atual.isNotEmpty()) alterarEndereco(atual)
        }
    }

    fun alterarEndereco(texto: String) {
        _estado.update {
            it.copy(endereco = texto, aviso = null, enderecoNormalizado = ServerConfig.aPartirDe(texto)?.base?.toString())
        }
    }

    fun testar() {
        val config = configOuAviso() ?: return
        viewModelScope.launch {
            _estado.update { it.copy(testando = true, aviso = null) }
            val resultado = repositorio.testar(config)
            _estado.update { it.copy(testando = false, aviso = resultado.paraAviso()) }
        }
    }

    fun salvar() {
        val config = configOuAviso() ?: return
        viewModelScope.launch {
            _estado.update { it.copy(salvando = true, aviso = null) }
            val resultado = repositorio.testar(config)
            if (resultado == ResultadoTesteServidor.Ok) {
                repositorio.salvar(config)
                _estado.update { it.copy(salvando = false) }
                eventos.enviar(EventoServidor.Salvo)
            } else {
                _estado.update { it.copy(salvando = false, aviso = resultado.paraAviso()) }
            }
        }
    }

    private fun configOuAviso(): ServerConfig? {
        val config = ServerConfig.aPartirDe(_estado.value.endereco)
        if (config == null) _estado.update { it.copy(aviso = AvisoServidor.ENDERECO_INVALIDO) }
        return config
    }
}

internal fun ResultadoTesteServidor.paraAviso(): AvisoServidor = when (this) {
    ResultadoTesteServidor.Ok -> AvisoServidor.CONEXAO_OK
    ResultadoTesteServidor.NaoEhConversa -> AvisoServidor.NAO_EH_CONVERSA
    ResultadoTesteServidor.CertificadoInvalido -> AvisoServidor.CERTIFICADO_INVALIDO
    ResultadoTesteServidor.HostNaoEncontrado -> AvisoServidor.HOST_NAO_ENCONTRADO
    ResultadoTesteServidor.Indisponivel -> AvisoServidor.INDISPONIVEL
    is ResultadoTesteServidor.SemConexao -> AvisoServidor.SEM_CONEXAO
}
