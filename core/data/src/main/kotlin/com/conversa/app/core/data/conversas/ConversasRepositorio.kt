package com.conversa.app.core.data.conversas

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.diretaCom
import com.conversa.app.core.model.fixadasAoDesafixar
import com.conversa.app.core.model.fixadasAoFixar
import com.conversa.app.core.model.fixadasAoMover
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarConversaRequisicao
import com.conversa.app.core.network.dto.ArquivarRequisicao
import com.conversa.app.core.network.dto.CriarConversaRequisicao
import com.conversa.app.core.network.dto.FixadasDto
import com.conversa.app.core.network.dto.IncluirMembroRequisicao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/** Ação que precisa de sessão foi chamada sem sessão (ex.: logout no meio). */
class SemSessaoException : IllegalStateException("Sem sessão")

/**
 * Conversas do usuário (CON-01…08, CON-13). O Room é a fonte da tela; o servidor
 * atualiza por cima. Fixar e arquivar são otimistas: grava local, chama a API e,
 * se falhar, recarrega a lista do servidor (como o web).
 */
@Singleton
class ConversasRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val dao: ConversaDao,
    private val sessao: SessaoRepositorio,
    private val relogio: Clock,
) {
    fun observarTodas(): Flow<List<Conversa>> = dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }

    fun observar(id: Long): Flow<Conversa?> = dao.observar(id).map { it?.paraModelo() }

    /** `GET /conversas` → Room. */
    suspend fun atualizar(): Result<Unit> = chamarApi { api.conversas() }.map { lista ->
        dao.substituirTodas(lista.map { it.paraEntidade() })
    }

    // --- Fixar (CON-03) ---

    suspend fun fixar(id: Long): Result<Unit> = salvarFixadas(fixadasAoFixar(atuais(), id))

    suspend fun desafixar(id: Long): Result<Unit> = salvarFixadas(fixadasAoDesafixar(atuais(), id))

    /** Sobe (`-1`) ou desce (`+1`) uma posição entre as fixadas. Sem mudança, não chama a API. */
    suspend fun moverFixada(id: Long, deslocamento: Int): Result<Unit> {
        val nova = fixadasAoMover(atuais(), id, deslocamento) ?: return Result.success(Unit)
        return salvarFixadas(nova)
    }

    /** `PATCH /conversa/fixadas` com a lista completa, na ordem. */
    private suspend fun salvarFixadas(ids: List<Long>): Result<Unit> {
        dao.aplicarFixadas(ids)
        return chamarApi { api.ordenarFixadas(FixadasDto(ids)) }.map { }.onFailure { atualizar() }
    }

    // --- Arquivar (CON-04) ---

    /** `PATCH /conversa/arquivada`. Arquivar também desfixa. */
    suspend fun arquivar(id: Long, arquivada: Boolean): Result<Unit> {
        dao.marcarArquivada(id, if (arquivada) relogio.millis() else null)
        return chamarApi { api.arquivarConversa(ArquivarRequisicao(id, arquivada)) }.map { }.onFailure { atualizar() }
    }

    // --- Conversa direta (CON-06) ---

    /**
     * Devolve a conversa direta com o contato, criando se não existe:
     * `PUT /conversa {descricao:"", tipo:1}` → `PUT /conversa/usuario` (eu) →
     * `PUT /conversa/usuario` (contato) → `GET /conversas`.
     * O servidor não deduplica diretas nem faz isso numa transação (pendência S8):
     * se falhar no meio, a conversa fica incompleta no servidor.
     */
    suspend fun obterOuCriarDireta(contatoId: Long): Result<Long> {
        atuais().diretaCom(contatoId)?.let { return Result.success(it.id) }
        val eu = sessao.sessao.value?.usuarioId ?: return Result.failure(SemSessaoException())
        return chamarApi {
            val criada = api.criarConversa(CriarConversaRequisicao(descricao = "", tipo = TipoConversa.DIRETA.codigo))
            try {
                api.incluirMembro(IncluirMembroRequisicao(criada.id, eu))
                if (contatoId != eu) api.incluirMembro(IncluirMembroRequisicao(criada.id, contatoId))
            } catch (e: Exception) {
                Timber.w("Conversa direta %d criada, mas os membros não foram incluídos", criada.id)
                throw e
            }
            criada.id
        }.onSuccess { atualizar() }
    }

    // --- Grupos (CON-07, CON-08) ---

    /** `PUT /conversa {descricao, tipo:2}` → `PUT /conversa/usuario` para cada membro **e para mim**. */
    suspend fun criarGrupo(nome: String, membros: Collection<Long>): Result<Long> {
        val eu = sessao.sessao.value?.usuarioId ?: return Result.failure(SemSessaoException())
        return chamarApi {
            val criada = api.criarConversa(CriarConversaRequisicao(descricao = nome.trim(), tipo = TipoConversa.GRUPO.codigo))
            try {
                coroutineScope {
                    (setOf(eu) + membros).map { usuario ->
                        async { api.incluirMembro(IncluirMembroRequisicao(criada.id, usuario)) }
                    }.awaitAll()
                }
            } catch (e: Exception) {
                Timber.w("Grupo %d criado, mas nem todos os membros foram incluídos", criada.id)
                throw e
            }
            criada.id
        }.onSuccess { atualizar() }
    }

    /** `GET /conversa/usuarios?conversa=` (quem não é membro recebe lista vazia). */
    suspend fun membros(conversaId: Long): Result<List<MembroConversa>> =
        chamarApi { api.membros(conversaId) }.map { lista -> lista.map { it.paraModelo() }.sortedBy { it.nome.lowercase() } }

    /** `PATCH /conversa {id, descricao}`. */
    suspend fun renomear(conversaId: Long, nome: String): Result<Unit> =
        chamarApi { api.alterarConversa(AlterarConversaRequisicao(conversaId, nome.trim())) }.map { }.onSuccess { atualizar() }

    /** `PUT /conversa/usuario {conversa_id, usuario_id}` (já membro = sem erro). */
    suspend fun adicionarMembro(conversaId: Long, usuarioId: Long): Result<Unit> =
        chamarApi { api.incluirMembro(IncluirMembroRequisicao(conversaId, usuarioId)) }.map { }

    /**
     * Sair do grupo: `DELETE /conversa/usuario?id=<meu vínculo>`. O servidor só deixa
     * remover o próprio vínculo (contrato §11.4): remover outra pessoa dá 403.
     */
    suspend fun sair(conversaId: Long, meuVinculoId: Long): Result<Unit> =
        chamarApi { api.excluirMembro(meuVinculoId) }.map {
            dao.remover(conversaId)
            atualizar()
            Unit
        }

    private suspend fun atuais(): List<Conversa> = dao.todas().map { it.paraModelo() }
}
