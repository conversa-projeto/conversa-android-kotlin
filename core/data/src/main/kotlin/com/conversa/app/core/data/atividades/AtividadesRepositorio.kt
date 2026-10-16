package com.conversa.app.core.data.atividades

import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.AtividadeDao
import com.conversa.app.core.model.ATIVIDADES_POR_PAGINA
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Atividades (8.1, FC-800), como o store do web: a lista (o Room é o cache), a paginação
 * (`antes` = id da última) e o contador da aba. Desde o servidor `ef13c22` cada atividade fica
 * pendente até ser concluída (sozinha ou à mão, [concluir]), e o contador é o de pendentes:
 * abrir a aba não zera nada.
 */
@Singleton
class AtividadesRepositorio @Inject constructor(private val api: ConversaApi, private val dao: AtividadeDao) {
    private val _novas = MutableStateFlow(0)

    /** Badge da aba (`GET /atividades/novas`): as pendentes. */
    val novas: StateFlow<Int> = _novas.asStateFlow()

    @Volatile private var aberta = false

    fun observar(): Flow<List<Atividade>> = dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }

    suspend fun atualizarNovas() {
        chamarApi { api.atividadesNovas() }.onSuccess { _novas.value = it.quantidade }
    }

    /** Primeira página, no lugar do que havia. Devolve se chegou ao fim (veio menos que uma página). */
    suspend fun carregar(): Result<Boolean> = chamarApi { api.atividades(0, ATIVIDADES_POR_PAGINA) }.map { lista ->
        dao.trocar(lista.map { it.paraModelo().paraEntidade() })
        lista.size < ATIVIDADES_POR_PAGINA
    }

    /** As anteriores à [antes] (perto do fim da lista). Devolve se chegou ao fim. */
    suspend fun carregarMais(antes: Long): Result<Boolean> = chamarApi { api.atividades(antes, ATIVIDADES_POR_PAGINA) }.map { lista ->
        dao.salvar(lista.map { it.paraModelo().paraEntidade() })
        lista.size < ATIVIDADES_POR_PAGINA
    }

    /** A aba abriu: relê a lista e o contador. Devolve se chegou ao fim. */
    suspend fun abrir(): Result<Boolean> {
        aberta = true
        val resultado = carregar()
        atualizarNovas()
        return resultado
    }

    /**
     * Conclui à mão (ou ao abrir a chamada perdida), como o web: some a marca na hora e o contador
     * desce; as outras abas e aparelhos recebem o WS 61. Já concluída, nada.
     */
    suspend fun concluir(atividade: Atividade): Result<Unit> {
        if (!atividade.pendente) return Result.success(Unit)
        return chamarApi { api.concluirAtividade(IdDto(atividade.id)) }.map {
            dao.concluir(atividade.id)
            _novas.value = (_novas.value - 1).coerceAtLeast(0)
        }
    }

    fun fechar() {
        aberta = false
    }

    /**
     * WS 61: o contador e, com a aba aberta ou com lista guardada, a primeira página de novo. O
     * aviso também vem quando uma atividade é concluída (leu a resposta ou a menção, confirmou a
     * leitura, viu a reação, concluiu em outro aparelho): a marca de pendente some da lista guardada.
     */
    suspend fun aoReceberAviso() {
        atualizarNovas()
        if (aberta || dao.quantas() > 0) carregar()
    }

    /** Saiu da conta (o banco é limpo à parte). */
    fun limpar() {
        _novas.value = 0
        aberta = false
    }
}
