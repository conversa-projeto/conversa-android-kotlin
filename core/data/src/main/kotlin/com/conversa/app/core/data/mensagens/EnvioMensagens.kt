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
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.ArquivoGrandeDemaisException
import com.conversa.app.core.data.anexos.FontesArquivo
import com.conversa.app.core.data.anexos.extensaoDe
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.database.dao.EnvioPendenteDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.EnvioPendenteEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.model.PREFIXO_LOCAL
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

/** Anexo da fila de envio: o arquivo no aparelho e, depois do upload, o `identificador`. */
@Serializable
data class AnexoPendente(
    val ordem: Int,
    val uri: String,
    val nome: String,
    val tamanho: Long,
    val mime: String? = null,
    val tipo: Int,
    /** Preenchido assim que o upload termina: se o envio tentar de novo, não sobe outra vez. */
    val identificador: String? = null,
)

/** O que fica em `envio_pendente.payloadJson`: o corpo do `PUT /mensagem` (sem os anexos) e os anexos. */
@Serializable
data class PacoteEnvio(val corpo: EnviarMensagemRequisicao, val anexos: List<AnexoPendente> = emptyList()) {
    /** Conteúdos finais, na ordem: os do corpo + os anexos já enviados. */
    fun conteudos(): List<ConteudoEnvioDto> =
        (corpo.conteudos + anexos.map { ConteudoEnvioDto(it.ordem, it.tipo, it.identificador) }).sortedBy { it.ordem }
}

/** O envio de um anexo não tem volta (sem acesso ao arquivo, grande demais, recusado). */
private class FalhaDefinitiva : Exception()

/**
 * Envio de mensagens (ENV-01, ANX-01, TODO 3.7 e 4.2). A mensagem nunca se perde:
 * 1. vira uma mensagem otimista no Room (id negativo, `enviando`; anexos com
 *    `conteudo = "local:<uri>"`) e uma linha em `envio_pendente` ([PacoteEnvio]);
 * 2. o [EnvioWorker] (com rede, sobrevive a fechar o app) sobe os anexos que faltam e
 *    manda a mensagem, na ordem em que foram criadas;
 * 3. deu certo: troca a otimista pela real. Erro definitivo (4xx, arquivo inacessível
 *    ou grande demais) ou [MAX_TENTATIVAS] erros do servidor: marca `falhou`
 *    ("Reenviar"/"Apagar"). Sem rede não conta tentativa. Erro em qualquer anexo para a
 *    mensagem inteira (como no web).
 */
@Singleton
class EnvioMensagens @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    private val envioDao: EnvioPendenteDao,
    private val sessao: SessaoRepositorio,
    private val conversas: ConversasRepositorio,
    private val agendador: AgendadorEnvio,
    private val anexos: AnexosRepositorio,
    private val fontes: FontesArquivo,
    private val relogio: Clock,
) {
    private val trava = Mutex()

    private val _progresso = MutableStateFlow<Map<Long, Float>>(emptyMap())

    /** Mensagem otimista → fração já enviada dos anexos (0..1), para a barra na bolha. */
    val progresso: StateFlow<Map<Long, Float>> = _progresso.asStateFlow()

    suspend fun enviarTexto(conversaId: Long, texto: String): Long = enviar(conversaId, texto, emptyList())

    /**
     * Cria a mensagem otimista e agenda o envio. Ordem dos conteúdos (como o web):
     * texto primeiro, depois os anexos na ordem em que foram escolhidos. Devolve o id local.
     */
    suspend fun enviar(conversaId: Long, texto: String, anexosLocais: List<AnexoLocal>): Long {
        val atual = sessao.sessao.value ?: error("Sem sessão")
        val agora = relogio.instant()
        val textoLimpo = texto.trim()
        require(textoLimpo.isNotEmpty() || anexosLocais.isNotEmpty()) { "Mensagem vazia" }
        val idLocal = trava.withLock {
            val id = minOf(mensagemDao.menorId() ?: 0, 0) - 1
            val conteudosTexto = if (textoLimpo.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ConteudoEnvioDto(1, TipoConteudo.TEXTO.codigo, textoLimpo),
                )
            }
            val pendentes = anexosLocais.mapIndexed { i, anexo ->
                AnexoPendente(conteudosTexto.size + i + 1, anexo.uri, anexo.nome, anexo.tamanho, anexo.mime, anexo.tipo.codigo)
            }
            val pacote = PacoteEnvio(EnviarMensagemRequisicao(conversaId, conteudosTexto), pendentes)
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
                        conteudos =
                        conteudosTexto.map { ConteudoEntidade(id, it.ordem, null, it.tipo, it.conteudo.orEmpty(), "", "", 0, "") } +
                            pendentes.map {
                                ConteudoEntidade(id, it.ordem, null, it.tipo, PREFIXO_LOCAL + it.uri, it.nome, extensaoDe(it.nome), 0, "")
                            },
                        reacoes = emptyList(),
                    ),
                ),
            )
            envioDao.inserir(
                EnvioPendenteEntidade(
                    conversaId = conversaId,
                    mensagemIdLocal = id,
                    payloadJson = ConversaJson.encodeToString(PacoteEnvio.serializer(), pacote),
                    criadoEm = agora,
                ),
            )
            id
        }
        agendador.agendar()
        return idLocal
    }

    /** Chamado pelo worker: sobe os anexos e manda tudo o que está na fila, em ordem. */
    suspend fun processarPendentes(): ResultadoEnvio = trava.withLock {
        var enviouAlguma = false
        for (pendente in envioDao.todos()) {
            val mensagem = mensagemDao.buscar(pendente.mensagemIdLocal)
            if (mensagem == null) {
                envioDao.remover(pendente.mensagemIdLocal)
                continue
            }
            if (mensagem.mensagem.falhou) continue
            var pacote = ConversaJson.decodeFromString(PacoteEnvio.serializer(), pendente.payloadJson)

            // 1. Anexos que ainda não subiram.
            val falhaAnexo = try {
                pacote = subirAnexos(pendente, pacote)
                null
            } catch (_: FalhaDefinitiva) {
                mensagemDao.marcarEnvio(pendente.mensagemIdLocal, enviando = false, falhou = true)
                continue
            } catch (e: ErroApi) {
                e
            }
            val resultado = if (falhaAnexo != null) {
                Result.failure(falhaAnexo)
            } else {
                chamarApi { api.enviarMensagem(pacote.corpo.copy(conteudos = pacote.conteudos())) }
            }

            // 2. A mensagem.
            val criada = resultado.getOrNull()
            if (criada != null) {
                trocarPelaReal(mensagem, criada.id, pacote)
                envioDao.remover(pendente.mensagemIdLocal)
                _progresso.update { it - pendente.mensagemIdLocal }
                enviouAlguma = true
                continue
            }
            when (val erro = resultado.exceptionOrNull()) {
                is ErroApi.SemConexao -> return@withLock ResultadoEnvio.TENTAR_DEPOIS
                is ErroApi.SessaoExpirada -> return@withLock ResultadoEnvio.CONCLUIDO
                is ErroApi.ServidorIndisponivel, is ErroApi.MuitasTentativas ->
                    if (!contarFalha(pendente)) return@withLock ResultadoEnvio.TENTAR_DEPOIS
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

    /**
     * Sobe cada anexo sem `identificador` e grava o identificador na fila assim que
     * termina (uma nova tentativa não sobe de novo). Lança [FalhaDefinitiva] ou [ErroApi].
     */
    private suspend fun subirAnexos(pendente: EnvioPendenteEntidade, inicial: PacoteEnvio): PacoteEnvio {
        var pacote = inicial
        val total = pacote.anexos.sumOf { it.tamanho }.coerceAtLeast(1)
        var jaEnviados = pacote.anexos.filter { it.identificador != null }.sumOf { it.tamanho }
        for (anexo in pacote.anexos.filter { it.identificador == null }) {
            val fonte = fontes.abrir(anexo.uri)
            if (fonte == null) {
                Timber.w("Anexo sem acesso (permissão do arquivo acabou)")
                throw FalhaDefinitiva()
            }
            val base = jaEnviados
            val enviado = anexos.enviar(fonte, TipoConteudo.de(anexo.tipo)) { enviados, _ ->
                _progresso.update { it + (pendente.mensagemIdLocal to ((base + enviados).toFloat() / total)) }
            }.getOrElse { erro ->
                when {
                    erro is ArquivoGrandeDemaisException -> throw FalhaDefinitiva()
                    erro is ErroApi.Servidor && erro.status < 500 -> throw FalhaDefinitiva()
                    erro is ErroApi -> throw erro
                    else -> throw FalhaDefinitiva()
                }
            }
            jaEnviados += anexo.tamanho
            pacote =
                pacote.copy(
                    anexos = pacote.anexos.map {
                        if (it.ordem ==
                            anexo.ordem
                        ) {
                            it.copy(identificador = enviado.identificador)
                        } else {
                            it
                        }
                    },
                )
            envioDao.atualizarPayload(pendente.mensagemIdLocal, ConversaJson.encodeToString(PacoteEnvio.serializer(), pacote))
        }
        return pacote
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
     * prévias nem seguintes = só ela); se não der, grava a otimista com o id real e os
     * identificadores dos anexos no lugar dos `local:`.
     */
    private suspend fun trocarPelaReal(otimista: MensagemCompleta, idReal: Long, pacote: PacoteEnvio) {
        val identificadores = pacote.anexos.associate { it.ordem to it.identificador }
        val real = chamarApi { api.mensagens(otimista.mensagem.conversaId, mensagemReferencia = idReal) }
            .getOrNull()?.firstOrNull { it.id == idReal }?.paraEntidade()
            ?: otimista.copy(
                mensagem = otimista.mensagem.copy(id = idReal, enviando = false, falhou = false),
                conteudos = otimista.conteudos.map {
                    it.copy(mensagemId = idReal, conteudo = identificadores[it.ordem] ?: it.conteudo)
                },
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
        _progresso.update { it - idLocal }
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
