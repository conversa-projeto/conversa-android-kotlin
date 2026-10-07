package com.conversa.app.core.data.contatos

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.ContatoDao
import com.conversa.app.core.model.Contato
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Contatos (CON-11). Hoje `GET /usuario/contatos` devolve **todos** os usuários do
 * sistema, inclusive o próprio e sem foto (contrato §5.1); a foto vem da conversa
 * direta (`avatarDoContato`).
 */
@Singleton
class ContatosRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val dao: ContatoDao,
    private val sessao: SessaoRepositorio,
) {
    /** Todos menos o próprio usuário, por nome. */
    fun observarOutros(): Flow<List<Contato>> = combine(dao.observarTodos(), sessao.sessao) { lista, atual ->
        lista.filter { it.id != atual?.usuarioId }.map { it.paraModelo() }
    }

    suspend fun atualizar(): Result<Unit> = chamarApi { api.contatos() }.map { lista ->
        dao.substituirTodos(lista.map { it.paraEntidade() })
    }
}
