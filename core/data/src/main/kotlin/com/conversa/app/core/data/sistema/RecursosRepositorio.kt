package com.conversa.app.core.data.sistema

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.model.Recursos
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * O que o servidor tem ligado (GER-08, servidor `769bd7d`): `GET /recursos` → `{transcricao, ia}`,
 * uma vez por sessão, como o `useRecursos` do web. Sem transcritor, "Transcrever" some (FC-419);
 * sem IA, "Resumir conversa" e a sugestão ao digitar não aparecem (8.8).
 * [recursos] nulo = ainda não se sabe (sem rede): a tela segue como antes, e o 400 do transcritor
 * continua desligando o botão.
 */
@Singleton
class RecursosRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val sessao: SessaoRepositorio,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _recursos = MutableStateFlow<Recursos?>(null)
    val recursos: StateFlow<Recursos?> = _recursos.asStateFlow()

    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch {
            sessao.sessao.map { it?.usuarioId }.distinctUntilChanged().collectLatest { usuario ->
                _recursos.value = null
                if (usuario == null) return@collectLatest
                // Tenta até conseguir (a API pode estar subindo), como o início da sessão.
                while (!atualizar()) delay(INTERVALO_MS)
            }
        }
    }

    /** Lê de novo; `false` se não deu (fica o que já se sabia). */
    suspend fun atualizar(): Boolean = chamarApi { api.recursos() }
        .onSuccess {
            Timber.d("Recursos do servidor: transcricao=%s ia=%s", it.transcricao, it.ia)
            _recursos.value = Recursos(transcricao = it.transcricao, ia = it.ia)
        }
        .onFailure { Timber.d("Falha ao ler os recursos: %s", it.javaClass.simpleName) }
        .isSuccess

    companion object {
        const val INTERVALO_MS = 30_000L
    }
}
