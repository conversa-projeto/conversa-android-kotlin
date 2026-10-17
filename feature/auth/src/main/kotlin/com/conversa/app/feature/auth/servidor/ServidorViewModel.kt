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
    /** Certificado próprio recusado que a pessoa pode aceitar (impressão SHA-256; só no debug). */
    val certificadoParaConfiar: String? = null,
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
            _estado.update { it.copy(testando = true, aviso = null, certificadoParaConfiar = null) }
            val (_, resultado) = testarComAlternativa(config)
            _estado.update {
                it.copy(testando = false, aviso = resultado.paraAviso(), certificadoParaConfiar = resultado.impressaoParaConfiar())
            }
        }
    }

    fun salvar() {
        val config = configOuAviso() ?: return
        viewModelScope.launch {
            _estado.update { it.copy(salvando = true, aviso = null, certificadoParaConfiar = null) }
            val (usado, resultado) = testarComAlternativa(config)
            if (resultado == ResultadoTesteServidor.Ok) {
                repositorio.salvar(usado, repositorio.certificadoAceito(usado))
                _estado.update { it.copy(salvando = false) }
                eventos.enviar(EventoServidor.Salvo)
            } else {
                _estado.update {
                    it.copy(salvando = false, aviso = resultado.paraAviso(), certificadoParaConfiar = resultado.impressaoParaConfiar())
                }
            }
        }
    }

    /**
     * "Confiar neste certificado" (só no debug): servidor interno com certificado próprio. Testa de
     * novo aceitando só esse certificado e, se responder como Conversa, salva o endereço com ele.
     */
    fun confiarNoCertificado() {
        val impressao = _estado.value.certificadoParaConfiar ?: return
        val config = configOuAviso() ?: return
        viewModelScope.launch {
            _estado.update { it.copy(salvando = true, aviso = null, certificadoParaConfiar = null) }
            val resultado = repositorio.testar(config, impressao)
            if (resultado == ResultadoTesteServidor.Ok) {
                repositorio.salvar(config, impressao)
                _estado.update { it.copy(salvando = false) }
                eventos.enviar(EventoServidor.Salvo)
            } else {
                _estado.update { it.copy(salvando = false, aviso = resultado.paraAviso()) }
            }
        }
    }

    fun naoConfiarNoCertificado() {
        _estado.update { it.copy(certificadoParaConfiar = null) }
    }

    /**
     * Digitou só o nome ou o IP (sem `http://`/`https://`): tenta HTTPS e, se não houver nada
     * na porta, HTTP (servidor interno sem TLS; só o debug aceita). Se o HTTP der certo, o campo passa a mostrá-lo.
     */
    private suspend fun testarComAlternativa(config: ServerConfig): Pair<ServerConfig, ResultadoTesteServidor> {
        val resultado = repositorio.testar(config, repositorio.certificadoAceito(config))
        val texto = _estado.value.endereco.trim()
        // Sem resposta no HTTPS, ou o TLS falhou sem nem chegar um certificado (porta só HTTP, ex.: "IP:8080").
        val semHttps = resultado is ResultadoTesteServidor.SemConexao ||
            (resultado is ResultadoTesteServidor.CertificadoInvalido && resultado.impressao == null)
        if (!semHttps || "://" in texto) return config to resultado
        val http = ServerConfig.aPartirDe("http://$texto") ?: return config to resultado
        if (repositorio.testar(http) != ResultadoTesteServidor.Ok) return config to resultado
        _estado.update { it.copy(endereco = http.base.toString(), enderecoNormalizado = http.base.toString()) }
        return http to ResultadoTesteServidor.Ok
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
    is ResultadoTesteServidor.CertificadoInvalido -> AvisoServidor.CERTIFICADO_INVALIDO
    ResultadoTesteServidor.HostNaoEncontrado -> AvisoServidor.HOST_NAO_ENCONTRADO
    ResultadoTesteServidor.Indisponivel -> AvisoServidor.INDISPONIVEL
    is ResultadoTesteServidor.SemConexao -> AvisoServidor.SEM_CONEXAO
}

internal fun ResultadoTesteServidor.impressaoParaConfiar(): String? = (this as? ResultadoTesteServidor.CertificadoInvalido)?.impressao
