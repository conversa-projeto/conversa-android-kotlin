package com.conversa.app.core.data.chamadas

import com.conversa.app.core.model.Chamada
import com.conversa.app.core.model.ServidoresIce
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.IniciarChamadaRequisicao
import com.conversa.app.core.network.dto.RecusarChamadaRequisicao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ações de chamada no servidor (contrato §9.2). Todas lançam `ErroApi` na falha.
 * Interface para o gerenciador de chamadas ser testado com um falso.
 */
interface ChamadasRemotas {
    /** [usuarios] precisa incluir quem liga (senão o servidor responde 404 nas ações seguintes). */
    suspend fun iniciar(tipo: TipoChamada, usuarios: List<Long>, conversaId: Long?): Chamada

    suspend fun dados(chamadaId: Long): Chamada

    suspend fun entrar(chamadaId: Long)

    /** [naoAtendeu]: o app recusou sozinho (tocou até o fim, ocupado, pendente antiga) → chamada perdida. */
    suspend fun recusar(chamadaId: Long, naoAtendeu: Boolean)

    suspend fun sair(chamadaId: Long)

    suspend fun cancelar(chamadaId: Long)

    /** WS 56 aos outros: liguei o vídeo numa chamada de áudio. */
    suspend fun anunciarVideo(chamadaId: Long)

    suspend fun ice(): ServidoresIce
}

@Singleton
class ChamadasRepositorio @Inject constructor(private val api: ConversaApi) : ChamadasRemotas {
    override suspend fun iniciar(tipo: TipoChamada, usuarios: List<Long>, conversaId: Long?): Chamada = chamarApi {
        api.iniciarChamada(IniciarChamadaRequisicao(tipo.codigo, conversaId?.takeIf { it > 0 }, usuarios.distinct().map(::IdDto)))
    }.getOrThrow().paraModelo()

    override suspend fun dados(chamadaId: Long): Chamada = chamarApi { api.dadosChamada(chamadaId) }.getOrThrow().paraModelo()

    override suspend fun entrar(chamadaId: Long) {
        chamarApi { api.entrarChamada(IdDto(chamadaId)) }.getOrThrow()
    }

    override suspend fun recusar(chamadaId: Long, naoAtendeu: Boolean) {
        chamarApi { api.recusarChamada(RecusarChamadaRequisicao(chamadaId, naoAtendeu.takeIf { it })) }.getOrThrow()
    }

    override suspend fun sair(chamadaId: Long) {
        chamarApi { api.sairChamada(IdDto(chamadaId)) }.getOrThrow()
    }

    override suspend fun cancelar(chamadaId: Long) {
        chamarApi { api.cancelarChamada(IdDto(chamadaId)) }.getOrThrow()
    }

    override suspend fun anunciarVideo(chamadaId: Long) {
        chamarApi { api.ativarVideo(IdDto(chamadaId)) }.getOrThrow()
    }

    override suspend fun ice(): ServidoresIce = chamarApi { api.ice() }.getOrThrow().paraModelo()
}
