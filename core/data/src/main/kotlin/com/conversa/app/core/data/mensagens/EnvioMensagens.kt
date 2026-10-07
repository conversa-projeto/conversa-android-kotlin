package com.conversa.app.core.data.mensagens

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.database.dao.EnvioPendenteDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.EnvioPendenteEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ConteudoEnvioDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.ConversaJson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/** Quem acorda o envio em segundo plano (WorkManager no app; falso nos testes). */
interface AgendadorEnvio {
    fun agendar()

    fun cancelar()
}

enum class ResultadoEnvio {
    /** Nada mais na fila (enviado ou marcado como falha). */
    CONCLUIDO,

    /** Sem rede ou servidor fora: o WorkManager tenta de novo depois. */
    TENTAR_DEPOIS,
}

/**
 * Envio de mensagens (ENV-01, TODO 3.7). A mensagem nunca se perde:
 * 1. vira uma mensagem otimista no Room (id negativo, `enviando`) e uma linha em
 *    `envio_pendente` com o corpo do `PUT /mensagem`;
 * 2. o [EnvioWorker] (com rede) manda na ordem em que foram criadas;
 * 3. deu certo: troca a otimista pela real; erro definitivo (4xx) ou [MAX_TENTATIVAS]
 *    erros do servidor: marca `falhou` ("Reenviar"/"Apagar"). Sem rede não conta tentativa.
 */
@Singleton
class EnvioMensagens @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    private val envioDao: EnvioPendenteDao,
    private val sessao: SessaoRepositorio,
    private val conversas: ConversasRepositorio,
    private val agendador: AgendadorEnvio,
    private val relogio: Clock,
) {
    private val trava = Mutex()

    /** Cria a mensagem otimista e agenda o envio. Devolve o id local (negativo). */
    suspend fun enviarTexto(conversaId: Long, texto: String): Long {
        val atual = sessao.sessao.value ?: error("Sem sessão")
        val agora = relogio.instant()
        val idLocal = trava.withLock {
            val id = minOf(mensagemDao.menorId() ?: 0, 0) - 1
            val corpo = EnviarMensagemRequisicao(conversaId, listOf(ConteudoEnvioDto(1, TipoConteudo.TEXTO.codigo, texto)))
            mensagemDao.salvarCompletas(
                listOf(
                    MensagemCompleta(
                        mensagem = MensagemEntidade(
                            id = id,
                            conversaId = conversaId,
                            remetenteId = atual.usuarioId,
                            // O servidor manda só o primeiro nome do remetente.
                            remetente = atual.nome.trim().substringBefore(' '),
                            inserida = agora,
                            visivelEm = null,
                            excluidaEm = null,
                            dataEfetiva = agora,
                            referenciaJson = null,
                            recebida = false,
                            visualizada = false,
                            reproduzida = false,
                            enviando = true,
                        ),
                        conteudos = listOf(ConteudoEntidade(id, 1, null, TipoConteudo.TEXTO.codigo, texto, "", "", 0, "")),
                        reacoes = emptyList(),
                    ),
                ),
            )
            envioDao.inserir(
                EnvioPendenteEntidade(
                    conversaId = conversaId,
                    mensagemIdLocal = id,
                    payloadJson = ConversaJson.encodeToString(EnviarMensagemRequisicao.serializer(), corpo),
                    criadoEm = agora,
                ),
            )
            id
        }
        agendador.agendar()
        return idLocal
    }

    /** Chamado pelo worker: manda tudo o que está na fila, em ordem. */
    suspend fun processarPendentes(): ResultadoEnvio = trava.withLock {
        var enviouAlguma = false
        for (pendente in envioDao.todos()) {
            val mensagem = mensagemDao.buscar(pendente.mensagemIdLocal)
            if (mensagem == null) {
                envioDao.remover(pendente.mensagemIdLocal)
                continue
            }
            if (mensagem.mensagem.falhou) continue
            val corpo = ConversaJson.decodeFromString(EnviarMensagemRequisicao.serializer(), pendente.payloadJson)
            val resultado = chamarApi { api.enviarMensagem(corpo) }
            val criada = resultado.getOrNull()
            if (criada != null) {
                trocarPelaReal(mensagem, criada.id)
                envioDao.remover(pendente.mensagemIdLocal)
                enviouAlguma = true
                continue
            }
            when (val erro = resultado.exceptionOrNull()) {
                is ErroApi.SemConexao -> return@withLock ResultadoEnvio.TENTAR_DEPOIS
                is ErroApi.SessaoExpirada -> return@withLock ResultadoEnvio.CONCLUIDO
                is ErroApi.ServidorIndisponivel, is ErroApi.MuitasTentativas -> if (!contarFalha(
                        pendente,
                    )
                ) {
                    return@withLock ResultadoEnvio.TENTAR_DEPOIS
                }
                is ErroApi.Servidor -> if (erro.status >= 500 && !contarFalha(pendente)) {
                    return@withLock ResultadoEnvio.TENTAR_DEPOIS
                } else if (erro.status < 500) {
                    Timber.w("Envio recusado pelo servidor (%d)", erro.status)
                    mensagemDao.marcarEnvio(pendente.mensagemIdLocal, enviando = false, falhou = true)
                }
                else -> if (!contarFalha(pendente)) return@withLock ResultadoEnvio.TENTAR_DEPOIS
            }
        }
        if (enviouAlguma) conversas.atualizar()
        ResultadoEnvio.CONCLUIDO
    }

    /** Conta uma falha do servidor; na [MAX_TENTATIVAS]ª desiste (marca `falhou`) e devolve `true`. */
    private suspend fun contarFalha(pendente: EnvioPendenteEntidade): Boolean {
        envioDao.contarTentativa(pendente.mensagemIdLocal)
        if (pendente.tentativas + 1 < MAX_TENTATIVAS) return false
        mensagemDao.marcarEnvio(pendente.mensagemIdLocal, enviando = false, falhou = true)
        return true
    }

    /**
     * Troca a otimista pela mensagem real. Busca a real (`mensagemreferencia=id`, sem
     * prévias nem seguintes = só ela); se não der, grava a otimista com o id real.
     */
    private suspend fun trocarPelaReal(otimista: MensagemCompleta, idReal: Long) {
        val real = chamarApi { api.mensagens(otimista.mensagem.conversaId, mensagemReferencia = idReal) }
            .getOrNull()?.firstOrNull { it.id == idReal }?.paraEntidade()
            ?: otimista.copy(
                mensagem = otimista.mensagem.copy(id = idReal, enviando = false, falhou = false),
                conteudos = otimista.conteudos.map { it.copy(mensagemId = idReal) },
            )
        mensagemDao.remover(otimista.mensagem.id)
        mensagemDao.salvarCompletas(listOf(real))
    }

    /** "Reenviar" numa mensagem que falhou. */
    suspend fun reenviar(idLocal: Long) {
        mensagemDao.marcarEnvio(idLocal, enviando = true, falhou = false)
        envioDao.zerarTentativas(idLocal)
        agendador.agendar()
    }

    /** "Apagar" numa mensagem que falhou (ela nunca chegou ao servidor). */
    suspend fun descartar(idLocal: Long) {
        envioDao.remover(idLocal)
        mensagemDao.remover(idLocal)
    }

    /** Na abertura da sessão: se sobrou algo na fila (app morto no meio), agenda. */
    suspend fun retomar() {
        if (envioDao.todos().isNotEmpty()) agendador.agendar()
    }

    companion object {
        const val MAX_TENTATIVAS = 5
    }
}

/** Worker do envio: só chama [EnvioMensagens.processarPendentes]. */
@HiltWorker
class EnvioWorker @AssistedInject constructor(
    @Assisted contexto: Context,
    @Assisted parametros: WorkerParameters,
    private val envio: EnvioMensagens,
) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result = when (envio.processarPendentes()) {
        ResultadoEnvio.CONCLUIDO -> Result.success()
        ResultadoEnvio.TENTAR_DEPOIS -> Result.retry()
    }
}

/** Agenda o [EnvioWorker] com rede. `APPEND_OR_REPLACE`: mensagens novas nunca ficam para trás. */
class AgendadorEnvioWorkManager @Inject constructor(@ApplicationContext private val contexto: Context) : AgendadorEnvio {
    override fun agendar() {
        val pedido = OneTimeWorkRequestBuilder<EnvioWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(NOME, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    override fun cancelar() {
        WorkManager.getInstance(contexto).cancelUniqueWork(NOME)
    }

    private companion object {
        const val NOME = "envio_mensagens"
    }
}
