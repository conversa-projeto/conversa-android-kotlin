package com.conversa.app.core.data.ia

import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.data.sistema.RecursosRepositorio
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.PedirSugestaoRequisicao
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Sugestão de texto da IA ao digitar (ENV-25, contrato §13.6). Liga só com a IA do servidor
 * (GER-08) e a preferência do aparelho ("Sugerir a continuação ao digitar", padrão ligada).
 */
@Singleton
class SugestoesRepositorio @Inject constructor(
    private val api: ConversaApi,
    recursos: RecursosRepositorio,
    private val preferencias: PreferenciasRepositorio,
) {
    /** A IA do servidor está ligada (a chave da Privacidade só aparece assim). */
    val disponivel: Flow<Boolean> = combine(recursos.recursos, preferencias.sugestoesIa) { servidor, _ -> servidor?.ia == true }

    val ligadas: Flow<Boolean> = combine(recursos.recursos, preferencias.sugestoesIa) { servidor, pessoa -> servidor?.ia == true && pessoa }

    val preferencia: Flow<Boolean> = preferencias.sugestoesIa

    suspend fun alterar(ligadas: Boolean) = preferencias.alterarSugestoesIa(ligadas)

    /**
     * A continuação de [texto] (o que vem depois do cursor); vazia se a IA não sugeriu ou falhou
     * (a falha não é erro, como no web). Cancelar a corrotina cancela o pedido.
     */
    suspend fun sugerir(conversaId: Long, texto: String): String =
        chamarApi { api.sugestao(PedirSugestaoRequisicao(conversaId, texto)) }.map { it.sugestao }.getOrDefault("")
}
