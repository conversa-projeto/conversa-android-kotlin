package com.conversa.app.core.data.ia

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.database.dao.ResumoDao
import com.conversa.app.core.database.entidades.ResumoEntidade
import com.conversa.app.core.model.PeriodoResumo
import com.conversa.app.core.model.ResumoConversa
import com.conversa.app.core.model.ResumoSalvo
import com.conversa.app.core.model.StatusResumo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.PedirResumoRequisicao
import com.conversa.app.core.network.dto.ResumoDto
import com.conversa.app.core.network.dto.paraDto
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.network.json.ConversaJson
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Resumo da conversa por IA (CON-14, contrato §13.5): o servidor resume em segundo plano e o app
 * consulta até ficar pronto. O último de cada conversa fica no Room (por usuário), como o web.
 */
@Singleton
class ResumosRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val dao: ResumoDao,
    private val sessao: SessaoRepositorio,
    private val relogio: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observar(conversaId: Long): Flow<ResumoSalvo?> = sessao.sessao.flatMapLatest { atual ->
        if (atual == null) {
            flowOf(null)
        } else {
            dao.observar(atual.usuarioId, conversaId).map { linha ->
                linha?.let {
                    runCatching { ResumoSalvo(ConversaJson.decodeFromString(ResumoDto.serializer(), it.json).paraModelo(), it.geradoEm) }
                        .getOrNull()
                }
            }
        }
    }

    /** `POST /conversa/resumo`: guarda com a hora do pedido (a linha "Resumo de dd/MM às HH:MM"). */
    suspend fun pedir(conversaId: Long, periodo: PeriodoResumo): Result<ResumoConversa> =
        chamarApi { api.pedirResumo(PedirResumoRequisicao(conversaId, periodo.codigo)) }
            .map { it.paraModelo() }
            .onSuccess { guardar(it, relogio.instant()) }

    /**
     * `GET /conversa/resumo?id=`. Sem rede, fica como estava (a próxima consulta tenta de novo).
     * Outra falha (ex.: a API reiniciou e o pedido se perdeu, 404) vira erro, com o motivo, como o web.
     */
    suspend fun consultar(salvo: ResumoSalvo): Result<ResumoConversa> =
        chamarApi { api.consultarResumo(salvo.resumo.id) }
            .map { it.paraModelo() }
            .onSuccess { guardar(it, salvo.geradoEm) }
            .onFailure { erro ->
                val classificado = erro.paraErroApi()
                if (classificado !is ErroApi.SemConexao) {
                    guardar(salvo.resumo.copy(status = StatusResumo.ERRO, erro = classificado.mensagemAmigavel()), salvo.geradoEm)
                }
            }

    private suspend fun guardar(resumo: ResumoConversa, geradoEm: Instant) {
        val usuario = sessao.sessao.value?.usuarioId ?: return
        val json = ConversaJson.encodeToString(ResumoDto.serializer(), resumo.paraDto())
        dao.salvar(ResumoEntidade(usuario, resumo.conversaId, json, geradoEm))
    }
}
