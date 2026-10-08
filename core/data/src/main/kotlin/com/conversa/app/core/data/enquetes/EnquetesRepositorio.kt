package com.conversa.app.core.data.enquetes

import com.conversa.app.core.model.Enquete
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.CriarEnqueteRequisicao
import com.conversa.app.core.network.dto.EncerrarEnqueteRequisicao
import com.conversa.app.core.network.dto.PrazoEnqueteRequisicao
import com.conversa.app.core.network.dto.VotarEnqueteRequisicao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.nuloExplicito
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive

/**
 * Enquetes (7.12, FC-516): cache em memória por id (as bolhas observam) e leituras
 * simultâneas da mesma enquete deduplicadas, como o store do web. Votar, encerrar e mudar
 * o prazo devolvem a enquete inteira, que substitui a do cache.
 */
@Singleton
class EnquetesRepositorio @Inject constructor(private val api: ConversaApi, @EscopoAplicacao private val escopo: CoroutineScope) {
    private val cache = MutableStateFlow<Map<Long, Enquete>>(emptyMap())
    private val emAndamento = mutableMapOf<Long, Deferred<Result<Enquete>>>()
    private val trava = Mutex()

    fun observar(id: Long): Flow<Enquete?> = cache.map { it[id] }.distinctUntilChanged()

    /** `GET /enquete?id=`: duas bolhas pedindo a mesma enquete ao mesmo tempo fazem uma leitura só. */
    suspend fun carregar(id: Long): Result<Enquete> {
        val pedido = trava.withLock {
            emAndamento.getOrPut(id) {
                escopo.async { guardar(chamarApi { api.enquete(id) }.map { it.paraModelo() }) }
            }
        }
        return try {
            pedido.await()
        } finally {
            trava.withLock { if (emAndamento[id] === pedido) emAndamento.remove(id) }
        }
    }

    /** WS 62: relê só se a enquete já está em cache (alguma bolha a carregou). */
    suspend fun aoAtualizar(id: Long) {
        if (cache.value.containsKey(id)) carregar(id)
    }

    /** Substitui o meu voto pela lista inteira (vazia tira o voto). */
    suspend fun votar(id: Long, opcoes: List<Long>): Result<Enquete> =
        guardar(chamarApi { api.votarEnquete(VotarEnqueteRequisicao(id, opcoes)) }.map { it.paraModelo() })

    suspend fun encerrar(id: Long): Result<Enquete> =
        guardar(chamarApi { api.encerrarEnquete(EncerrarEnqueteRequisicao(id)) }.map { it.paraModelo() })

    /** Definir ou mudar a data final; nula tira a data (`encerra_em: null` explícito). */
    suspend fun alterarPrazo(id: Long, encerraEm: Instant?): Result<Enquete> {
        val corpo = PrazoEnqueteRequisicao(id, encerraEm?.let { JsonPrimitive(it.toString()) } ?: nuloExplicito)
        return guardar(chamarApi { api.alterarPrazoEnquete(corpo) }.map { it.paraModelo() })
    }

    /** `PUT /enquete` (só grupo): opções preenchidas, sem espaços nas pontas. Devolve o id da enquete. */
    suspend fun criar(conversaId: Long, pergunta: String, opcoes: List<String>, multipla: Boolean, encerraEm: Instant?): Result<Long> {
        val corpo = CriarEnqueteRequisicao(
            conversaId = conversaId,
            pergunta = pergunta.trim(),
            opcoes = opcoes.map { it.trim() }.filter { it.isNotEmpty() },
            multipla = multipla,
            encerraEm = encerraEm?.toString(),
        )
        return chamarApi { api.criarEnquete(corpo) }.map { it.enqueteId }
    }

    private fun guardar(resultado: Result<Enquete>): Result<Enquete> = resultado.onSuccess { enquete ->
        cache.update { it + (enquete.id to enquete) }
    }
}
