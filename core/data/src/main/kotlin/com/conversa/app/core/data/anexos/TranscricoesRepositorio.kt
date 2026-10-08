package com.conversa.app.core.data.anexos

import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.StatusTranscricao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.IdentificadorDto
import com.conversa.app.core.network.dto.TranscricaoDto
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Transcrição de áudio (TODO 4.8, ANX-12, contrato §8.6). É por anexo: o resultado vai
 * para o Room em todas as mensagens com o mesmo identificador, e a tela lê da mensagem.
 * - [transcrever]: `PUT /anexo/transcricao`; processando → consulta a cada 3 s (como o web);
 * - servidor sem transcritor (400): [desligada] = true e os botões somem até o fim da
 *   sessão (até existir a pendência S10, que diria isso antes de tentar);
 * - erro do transcritor (status 3): o motivo fica em [erros].
 */
@Singleton
class TranscricoesRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _desligada = MutableStateFlow(false)
    val desligada: StateFlow<Boolean> = _desligada.asStateFlow()

    private val _erros = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Identificador → motivo do último erro do transcritor (para mostrar embaixo do botão). */
    val erros: StateFlow<Map<String, String>> = _erros.asStateFlow()

    private val _pedindo = MutableStateFlow<Set<String>>(emptySet())

    /** Pedidos em andamento (o botão fica desligado). */
    val pedindo: StateFlow<Set<String>> = _pedindo.asStateFlow()

    private val acompanhando = mutableSetOf<String>()

    /** "Transcrever" ou "Tentar de novo". */
    suspend fun transcrever(identificador: String): Result<Unit> {
        if (identificador in _pedindo.value) return Result.success(Unit)
        _pedindo.update { it + identificador }
        _erros.update { it - identificador }
        val resultado = chamarApi { api.transcrever(IdentificadorDto(identificador)) }
        _pedindo.update { it - identificador }
        return resultado
            .onSuccess { aplicar(identificador, it) }
            .onFailure { if (it is ErroApi.Servidor && it.status == HTTP_REQUISICAO_INVALIDA) _desligada.value = true }
            .map { }
    }

    /** A mensagem chegou com a transcrição "processando" (outra pessoa pediu): acompanha até terminar. */
    fun acompanhar(identificador: String) {
        if (!synchronized(acompanhando) { acompanhando.add(identificador) }) return
        escopo.launch {
            try {
                repeat(MAX_CONSULTAS) {
                    delay(INTERVALO_CONSULTA_MS)
                    val atual = chamarApi { api.transcricao(identificador) }.getOrNull() ?: return@repeat
                    aplicarNoBanco(identificador, atual)
                    if (atual.status != StatusTranscricao.PROCESSANDO.codigo) return@launch
                }
            } finally {
                synchronized(acompanhando) { acompanhando.remove(identificador) }
            }
        }
    }

    private suspend fun aplicar(identificador: String, resultado: TranscricaoDto) {
        aplicarNoBanco(identificador, resultado)
        if (resultado.status == StatusTranscricao.PROCESSANDO.codigo) acompanhar(identificador)
    }

    private suspend fun aplicarNoBanco(identificador: String, resultado: TranscricaoDto) {
        mensagemDao.atualizarTranscricao(identificador, resultado.status, resultado.texto)
        if (resultado.status == StatusTranscricao.ERRO.codigo && resultado.erro.isNotBlank()) {
            _erros.update { it + (identificador to resultado.erro) }
        }
    }

    /** Fim da sessão (outro servidor pode ter transcritor). */
    fun limpar() {
        _desligada.value = false
        _erros.value = emptyMap()
    }

    companion object {
        /** Igual ao web (`INTERVALO_CONSULTA_MS`). */
        const val INTERVALO_CONSULTA_MS = 3_000L

        /** Uma hora de consultas no máximo (o servidor desiste de um job parado em 30 min). */
        const val MAX_CONSULTAS = 1_200
        private const val HTTP_REQUISICAO_INVALIDA = 400
    }
}
