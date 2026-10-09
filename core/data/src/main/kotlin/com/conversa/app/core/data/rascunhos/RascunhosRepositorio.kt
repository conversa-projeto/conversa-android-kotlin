package com.conversa.app.core.data.rascunhos

import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.database.dao.RascunhoDao
import com.conversa.app.core.database.entidades.RascunhoEntidade
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.json.ConversaJson
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Rascunho do campo de uma conversa (FC-519, ENV-23): o texto com as menções no formato
 * `@[Nome](id)`, a fila de anexos e a resposta ou encaminhamento pendente (tipo + id da mensagem).
 */
data class Rascunho(
    val texto: String = "",
    val anexos: List<AnexoLocal> = emptyList(),
    val referencia: Pair<TipoReferencia, Long>? = null,
) {
    val vazio: Boolean get() = texto.isBlank() && anexos.isEmpty() && referencia == null
}

@Serializable
private data class AnexoRascunhoDto(val uri: String, val nome: String, val tamanho: Long, val mime: String?, val tipo: Int)

private val serializadorAnexos = ListSerializer(AnexoRascunhoDto.serializer())

/**
 * Um rascunho por conversa no Room (o banco é do usuário logado e é limpo ao sair), como o
 * IndexedDB do web: restaurado ao abrir a conversa, apagado ao enviar.
 */
@Singleton
class RascunhosRepositorio @Inject constructor(
    private val dao: RascunhoDao,
    @EscopoAplicacao private val escopo: CoroutineScope,
    private val relogio: Clock,
) {
    private val trava = Mutex()
    private val versoes = mutableMapOf<Long, Long>()

    suspend fun ler(conversaId: Long): Rascunho? = dao.buscar(conversaId)?.let { salvo ->
        val anexos = runCatching { ConversaJson.decodeFromString(serializadorAnexos, salvo.anexosJson) }.getOrDefault(emptyList())
        Rascunho(
            texto = salvo.texto,
            anexos = anexos.map { AnexoLocal(it.uri, it.nome, it.tamanho, it.mime, TipoConteudo.de(it.tipo)) },
            referencia = salvo.referenciaTipo?.let { tipo -> salvo.referenciaMensagemId?.let { TipoReferencia.de(tipo) to it } },
        )
    }

    /**
     * Grava no escopo do app (sair da conversa não perde o último); vazio apaga. Gravações
     * seguidas da mesma conversa valem na ordem: uma mais antiga que chegue atrasada é ignorada.
     */
    fun guardar(conversaId: Long, rascunho: Rascunho): Job {
        val versao = synchronized(versoes) { (versoes[conversaId] ?: 0L).plus(1).also { versoes[conversaId] = it } }
        return escopo.launch {
            trava.withLock {
                if (synchronized(versoes) { versoes[conversaId] } != versao) return@withLock
                if (rascunho.vazio) {
                    dao.apagar(conversaId)
                } else {
                    dao.salvar(
                        RascunhoEntidade(
                            conversaId = conversaId,
                            texto = rascunho.texto,
                            anexosJson = ConversaJson.encodeToString(
                                serializadorAnexos,
                                rascunho.anexos.map { AnexoRascunhoDto(it.uri, it.nome, it.tamanho, it.mime, it.tipo.codigo) },
                            ),
                            referenciaTipo = rascunho.referencia?.first?.codigo,
                            referenciaMensagemId = rascunho.referencia?.second,
                            atualizadoEm = relogio.instant(),
                        ),
                    )
                }
            }
        }
    }
}
