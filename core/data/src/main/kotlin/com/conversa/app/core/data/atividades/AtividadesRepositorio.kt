package com.conversa.app.core.data.atividades

import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.AtividadeDao
import com.conversa.app.core.model.ATIVIDADES_POR_PAGINA
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.network.api.ConversaApi
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
 * (`antes` = id da última) e o contador de novas da aba. Com a aba aberta, o que chega pelo
 * WS 61 já entra como visto; os itens continuam marcados como "Nova" enquanto ela está aberta.
 */
@Singleton
class AtividadesRepositorio @Inject constructor(private val api: ConversaApi, private val dao: AtividadeDao) {
    private val _novas = MutableStateFlow(0)

    /** Badge da aba (`GET /atividades/novas`). */
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

    /** A aba abriu: recarrega e marca como vistas (o badge zera). Devolve se chegou ao fim. */
    suspend fun abrir(): Result<Boolean> {
        aberta = true
        val resultado = carregar()
        chamarApi { api.marcarAtividadesVistas() }.onSuccess { _novas.value = 0 }
        return resultado
    }

    fun fechar() {
        aberta = false
    }

    /** WS 61: com a aba aberta, recarrega e marca como vistas; fechada, só o contador. */
    suspend fun aoReceberAviso() {
        if (aberta) abrir() else atualizarNovas()
    }

    /** Saiu da conta (o banco é limpo à parte). */
    fun limpar() {
        _novas.value = 0
        aberta = false
    }
}
