package com.conversa.app.core.data.pastas

import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.LIMITE_MENSAGENS_POR_VEZ
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Pasta
import com.conversa.app.core.model.ordenarPastas
import com.conversa.app.core.model.pastasDepois
import com.conversa.app.core.model.pastasDoTexto
import com.conversa.app.core.model.pastasParaTexto
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarPastaRequisicao
import com.conversa.app.core.network.dto.CriarPastaRequisicao
import com.conversa.app.core.network.dto.MensagensDaPastaRequisicao
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.realtime.EventoSocket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Pastas de mensagens da conversa direta (7.14; contrato §10.17; web `stores/pastas.ts`). As pastas
 * de cada conversa aberta ficam na memória, como no web; as marcas (`pastas` de cada mensagem)
 * ficam no Room, junto das mensagens. Pôr, tirar, excluir e o WS 67 mudam as marcas na hora, e a
 * próxima leitura das mensagens traz as do servidor.
 */
@Singleton
class PastasRepositorio @Inject constructor(private val api: ConversaApi, private val mensagemDao: MensagemDao) {
    private val pastas = MutableStateFlow<Map<Long, List<Pasta>>>(emptyMap())
    private val _mensagensMudaram = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** Uma pasta ganhou ou perdeu mensagens (aqui ou pelo WS 67): a pasta aberta relê. */
    val mensagensMudaram: SharedFlow<Long> = _mensagensMudaram.asSharedFlow()

    fun observar(conversaId: Long): Flow<List<Pasta>> = pastas.map { it[conversaId].orEmpty() }.distinctUntilChanged()

    /** `GET /pastas`: guarda e tira das mensagens as marcas das pastas que sumiram (excluídas ou não mais compartilhadas). */
    suspend fun carregar(conversaId: Long): Result<List<Pasta>> = chamarApi { api.pastas(conversaId) }
        .map { lista -> ordenarPastas(lista.map { it.paraModelo() }) }
        .onSuccess { lista ->
            pastas.update { it + (conversaId to lista) }
            limparMarcas(conversaId, lista.map { it.id }.toSet())
        }

    suspend fun criar(conversaId: Long, nome: String, cor: String = "", compartilhada: Boolean = false): Result<Pasta> =
        chamarApi { api.criarPasta(CriarPastaRequisicao(conversaId, nome.trim(), cor, compartilhada)) }
            .map { it.paraModelo() }
            .onSuccess { nova -> mudarLista(conversaId) { lista -> lista.filter { it.id != nova.id } + nova } }

    /** Só o que não é nulo muda (`cor = ""` tira a cor). Só o dono. */
    suspend fun alterar(pasta: Pasta, nome: String? = null, cor: String? = null, compartilhada: Boolean? = null): Result<Pasta> =
        chamarApi { api.alterarPasta(AlterarPastaRequisicao(pasta.id, nome?.trim(), cor, compartilhada)) }
            .map { it.paraModelo() }
            .onSuccess { alterada -> mudarLista(pasta.conversaId) { lista -> lista.map { if (it.id == alterada.id) alterada else it } } }

    /** Exclui a pasta (as mensagens ficam na conversa). Só o dono. */
    suspend fun excluir(pasta: Pasta): Result<Unit> = chamarApi { api.excluirPasta(pasta.id) }
        .map { }
        .onSuccess {
            mudarLista(pasta.conversaId) { lista -> lista.filter { it.id != pasta.id } }
            limparMarcas(pasta.conversaId, pastas.value[pasta.conversaId].orEmpty().map { it.id }.toSet())
        }

    /** Põe as mensagens já salvas (id > 0) na pasta, de 500 em 500. */
    suspend fun por(pasta: Pasta, mensagens: List<Long>): Result<Unit> {
        val ids = mensagens.filter { it > 0 }.distinct()
        if (ids.isEmpty()) return Result.success(Unit)
        return chamarApi { ids.chunked(LIMITE_MENSAGENS_POR_VEZ).forEach { api.porNaPasta(MensagensDaPastaRequisicao(pasta.id, it)) } }
            .onSuccess { depoisDeMudar(pasta, ids, incluir = true) }
    }

    suspend fun tirar(pasta: Pasta, mensagens: List<Long>): Result<Unit> {
        val ids = mensagens.distinct()
        if (ids.isEmpty()) return Result.success(Unit)
        return chamarApi { ids.chunked(LIMITE_MENSAGENS_POR_VEZ).forEach { api.tirarDaPasta(MensagensDaPastaRequisicao(pasta.id, it)) } }
            .onSuccess { depoisDeMudar(pasta, ids, incluir = false) }
    }

    /** `GET /pasta/mensagens`: 50 por página, da mais antiga para a mais nova; [antes] = a mais antiga já carregada. */
    suspend fun mensagens(pastaId: Long, antes: Long = 0): Result<List<Mensagem>> =
        chamarApi { api.mensagensDaPasta(pastaId, antes) }.map { lista -> lista.map { it.paraModelo() } }

    /** WS 67: põe ou tira a marca das mensagens e relê as pastas da conversa, se ela já foi aberta. */
    suspend fun aoAviso(evento: EventoSocket.PastasAtualizadas) {
        val acao = evento.acao
        if (acao != null && evento.mensagens.isNotEmpty()) marcar(evento.mensagens, evento.pastaId, incluir = acao == "incluir")
        if (evento.conversaId in pastas.value) carregar(evento.conversaId)
        if (acao != null) _mensagensMudaram.tryEmit(evento.pastaId)
    }

    /** Reconexão: relê as pastas das conversas já abertas (algum aviso pode ter se perdido). */
    suspend fun recarregarAbertas() {
        pastas.value.keys.forEach { carregar(it) }
    }

    private suspend fun depoisDeMudar(pasta: Pasta, ids: List<Long>, incluir: Boolean) {
        marcar(ids, pasta.id, incluir)
        carregar(pasta.conversaId)
        _mensagensMudaram.tryEmit(pasta.id)
    }

    /** De 500 em 500: o SQLite do Android 9 aceita até 999 variáveis por consulta. */
    private suspend fun marcar(ids: List<Long>, pastaId: Long, incluir: Boolean) {
        ids.chunked(LIMITE_MENSAGENS_POR_VEZ).flatMap { mensagemDao.pastasDas(it) }.forEach { linha ->
            val novas = pastasParaTexto(pastasDepois(pastasDoTexto(linha.pastas), pastaId, incluir))
            if (novas != linha.pastas) mensagemDao.atualizarPastas(linha.id, novas)
        }
    }

    private suspend fun limparMarcas(conversaId: Long, existentes: Set<Long>) {
        mensagemDao.pastasDaConversa(conversaId).forEach { linha ->
            val atuais = pastasDoTexto(linha.pastas)
            val ficam = atuais.filter { it in existentes }
            if (ficam.size != atuais.size) mensagemDao.atualizarPastas(linha.id, pastasParaTexto(ficam))
        }
    }

    private fun mudarLista(conversaId: Long, mudar: (List<Pasta>) -> List<Pasta>) =
        pastas.update { mapa -> mapa + (conversaId to ordenarPastas(mudar(mapa[conversaId].orEmpty()))) }
}
