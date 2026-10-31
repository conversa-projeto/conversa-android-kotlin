package com.conversa.app.core.data.mensagens

import android.content.Context
import android.os.Build
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
import com.conversa.app.core.data.anexos.MedidorAudio
import com.conversa.app.core.data.anexos.extensaoDe
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraResumidaDto
import com.conversa.app.core.database.dao.EnvioPendenteDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.EnvioPendenteEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.model.DadosAudio
import com.conversa.app.core.model.DestinatarioConfirmacao
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.PREFIXO_LOCAL
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.intensidadeParaTexto
import com.conversa.app.core.model.local
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ConfirmacaoDto
import com.conversa.app.core.network.dto.ConteudoEnvioDto
import com.conversa.app.core.network.dto.DestinatarioConfirmacaoDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.dto.PostagemDto
import com.conversa.app.core.network.dto.PostagemEnvioDto
import com.conversa.app.core.network.dto.ReferenciaDto
import com.conversa.app.core.network.dto.ReferenciaEnvioDto
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.ConversaJson
import com.conversa.app.core.registro.resumoDoErro
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    /** Áudio: medidos uma vez antes do upload (ANX-10); numa nova tentativa, não mede de novo. */
    val duracao: Double? = null,
    val intensidade: List<Int>? = null,
)

/** O que fica em `envio_pendente.payloadJson`: o corpo do `PUT /mensagem` (sem os anexos) e os anexos. */
@Serializable
data class PacoteEnvio(val corpo: EnviarMensagemRequisicao, val anexos: List<AnexoPendente> = emptyList()) {
    /** Conteúdos finais, na ordem: os do corpo + os anexos já enviados. */
    fun conteudos(): List<ConteudoEnvioDto> =
        (corpo.conteudos + anexos.map { ConteudoEnvioDto(it.ordem, it.tipo, it.identificador) }).sortedBy { it.ordem }
}

/** Mensagem citada no envio: resposta (7.3) ou encaminhada (tipo 2, 7.6). */
data class ReferenciaPendente(val tipo: TipoReferencia, val mensagem: Mensagem)

/** Postagem de grupo (7.13): a mensagem vira uma postagem ou responde a uma. */
sealed interface PostagemEnvio {
    /** "+" › "Postagem", com título opcional. */
    data class Nova(val titulo: String?) : PostagemEnvio

    /** "Responder à postagem": vai para baixo dela, fora do fluxo do chat. */
    data class Resposta(val postagemId: Long) : PostagemEnvio
}

/**
 * Confirmação de leitura pedida no envio (FC-521): [usuarios] nulo = todos os outros membros. Os
 * [destinatarios] (com o nome) só servem para a otimista já mostrar "Aguardando confirmação".
 */
data class PedidoConfirmacao(val usuarios: List<Long>?, val destinatarios: List<DestinatarioConfirmacao>)

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
    private val medidor: MedidorAudio,
) {
    private val trava = Mutex()

    private val _recusas = MutableSharedFlow<RecusaEnvio>(extraBufferCapacity = 8)

    /**
     * O servidor recusou a mensagem com um motivo (ex.: 403 "Você não tem permissão para: …",
     * GER-09): a bolha fica "falhou" e a tela da conversa mostra o motivo.
     */
    val recusas: SharedFlow<RecusaEnvio> = _recusas.asSharedFlow()

    private val _progresso = MutableStateFlow<Map<Long, Float>>(emptyMap())

    /** Mensagem otimista → fração já enviada dos anexos (0..1), para a barra na bolha. */
    val progresso: StateFlow<Map<Long, Float>> = _progresso.asStateFlow()

    suspend fun enviarTexto(conversaId: Long, texto: String): Long = enviar(conversaId, texto, emptyList())

    /**
     * Cria a mensagem otimista e agenda o envio. Ordem dos conteúdos (como o web):
     * texto primeiro, depois os anexos na ordem em que foram escolhidos. Com [referencia],
     * vai `mensagem_referencia {tipo, origem_mensagem_id}` e a otimista já mostra a citação;
     * encaminhada (7.6) leva antes os conteúdos da original (os identificadores, sem subir de
     * novo; votação não vai: o servidor recusa) e pode ir sem texto. Com [visivelEm] (7.10),
     * agendada: vai `visivel_em` em ISO UTC e a otimista já fica no fim, com o selo.
     * Com [postagem] (7.13): a nova já aparece como postagem, sem respostas; a resposta vai com
     * `postagem_id` e fica embaixo da postagem, fora do fluxo, até o servidor gravar. Com
     * [confirmacao] (FC-521), vai `pede_confirmacao` (e a lista, se houver). [localizacao] (ENV-30) é
     * o JSON do conteúdo tipo 9, depois do texto, como a figurinha. Com [legendaDepois] (a câmera,
     * ANX-20), os anexos vêm antes do texto, como o web manda a foto ou o vídeo com a legenda.
     * Devolve o id local.
     */
    suspend fun enviar(
        conversaId: Long,
        texto: String,
        anexosLocais: List<AnexoLocal>,
        referencia: ReferenciaPendente? = null,
        figurinha: String? = null,
        localizacao: String? = null,
        legendaDepois: Boolean = false,
        visivelEm: Instant? = null,
        postagem: PostagemEnvio? = null,
        confirmacao: PedidoConfirmacao? = null,
    ): Long {
        val atual = sessao.sessao.value ?: error("Sem sessão")
        val agora = relogio.instant()
        val textoLimpo = texto.trim()
        val encaminhados = if (referencia?.tipo == TipoReferencia.ENCAMINHAMENTO) {
            referencia.mensagem.conteudos.sortedBy { it.ordem }.filter { !it.local && it.tipo != TipoConteudo.ENQUETE }
        } else {
            emptyList()
        }
        require(
            textoLimpo.isNotEmpty() || anexosLocais.isNotEmpty() || encaminhados.isNotEmpty() || figurinha != null || localizacao != null,
        ) {
            "Mensagem vazia"
        }
        val idLocal = trava.withLock {
            val id = minOf(mensagemDao.menorId() ?: 0, 0) - 1
            val conteudosEncaminhados = encaminhados.mapIndexed { i, conteudo ->
                ConteudoEnvioDto(i + 1, conteudo.tipo.codigo, conteudo.conteudo)
            }
            // A legenda da câmera vem depois dos anexos (ANX-20).
            val anexosAntes = if (legendaDepois) anexosLocais.size else 0
            val conteudosTexto = conteudosEncaminhados + if (textoLimpo.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    ConteudoEnvioDto(encaminhados.size + anexosAntes + 1, TipoConteudo.TEXTO.codigo, textoLimpo),
                )
            }
            // Figurinha (7.8) vai depois do texto, como no web: "pacote/nome", tipo 7. A localização também (tipo 9).
            val comFigurinha = conteudosTexto + listOfNotNull(
                figurinha?.let { TipoConteudo.FIGURINHA to it },
                localizacao?.let { TipoConteudo.LOCALIZACAO to it },
            ).mapIndexed { i, (tipo, conteudo) -> ConteudoEnvioDto(conteudosTexto.size + i + 1, tipo.codigo, conteudo) }
            val pendentes = anexosLocais.mapIndexed { i, anexo ->
                AnexoPendente(
                    (if (legendaDepois) encaminhados.size else comFigurinha.size) + i + 1,
                    anexo.uri,
                    anexo.nome,
                    anexo.tamanho,
                    anexo.mime,
                    anexo.tipo.codigo,
                )
            }
            val corpo = EnviarMensagemRequisicao(
                conversaId,
                comFigurinha,
                visivelEm = visivelEm?.toString(),
                mensagemReferencia = referencia?.let { ReferenciaEnvioDto(it.tipo.codigo, it.mensagem.id) },
                postagem = (postagem as? PostagemEnvio.Nova)?.let { PostagemEnvioDto(it.titulo) },
                postagemId = (postagem as? PostagemEnvio.Resposta)?.postagemId,
                pedeConfirmacao = confirmacao?.let { true },
                confirmacaoUsuarios = confirmacao?.usuarios,
            )
            val pacote = PacoteEnvio(corpo, pendentes)
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
                            visivelEm = visivelEm,
                            excluidaEm = null,
                            dataEfetiva = visivelEm ?: agora,
                            referenciaJson = referencia?.let {
                                ConversaJson.encodeToString(
                                    ReferenciaDto.serializer(),
                                    ReferenciaDto(it.tipo.codigo, it.mensagem.paraResumidaDto()),
                                )
                            },
                            recebida = false,
                            visualizada = false,
                            reproduzida = false,
                            enviando = true,
                            postagemJson = (postagem as? PostagemEnvio.Nova)?.let {
                                ConversaJson.encodeToString(PostagemDto.serializer(), PostagemDto(titulo = it.titulo))
                            },
                            postagemId = (postagem as? PostagemEnvio.Resposta)?.postagemId,
                            confirmacaoJson = confirmacao?.let { pedido ->
                                val destinatarios = pedido.destinatarios.map { DestinatarioConfirmacaoDto(it.usuarioId, it.nome) }
                                ConversaJson.encodeToString(
                                    ConfirmacaoDto.serializer(),
                                    ConfirmacaoDto(total = destinatarios.size, destinatarios = destinatarios),
                                )
                            },
                        ),
                        conteudos =
                        encaminhados.mapIndexed { i, it ->
                            // Áudio encaminhado leva a duração e a onda da original (ANX-10).
                            ConteudoEntidade(
                                id,
                                i + 1,
                                null,
                                it.tipo.codigo,
                                it.conteudo,
                                it.nome,
                                it.extensao,
                                0,
                                "",
                                duracao = it.duracao,
                                intensidade = it.intensidade?.let(::intensidadeParaTexto),
                            )
                        } +
                            comFigurinha.drop(encaminhados.size).map {
                                ConteudoEntidade(id, it.ordem, null, it.tipo, it.conteudo.orEmpty(), "", "", 0, "")
                            } +
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
        val detalhes = listOfNotNull(
            anexosLocais.takeIf { it.isNotEmpty() }?.let { lista ->
                "${lista.size} anexo(s): " + lista.joinToString { "${it.tipo.name.lowercase()} ${it.tamanho} B" }
            },
            referencia?.tipo?.name?.lowercase(),
            "figurinha".takeIf { figurinha != null },
            "localização".takeIf { localizacao != null },
            "programada".takeIf { visivelEm != null },
            "postagem".takeIf { postagem != null },
            "pede confirmação".takeIf { confirmacao != null },
        )
        Timber.tag(TAG).i(
            "Na fila: mensagem local %d na conversa %d (%s)",
            idLocal,
            conversaId,
            detalhes.joinToString("; ").ifEmpty {
                "texto"
            },
        )
        agendador.agendar()
        return idLocal
    }

    /** Chamado pelo worker: sobe os anexos e manda tudo o que está na fila, em ordem. */
    suspend fun processarPendentes(): ResultadoEnvio = trava.withLock {
        var enviouAlguma = false
        val fila = envioDao.todos()
        Timber.tag(TAG).i("Processando a fila de envio: %d pendente(s)", fila.size)
        for (pendente in fila) {
            val mensagem = mensagemDao.buscar(pendente.mensagemIdLocal)
            if (mensagem == null) {
                Timber.tag(TAG).i("Mensagem local %d sumiu do banco: sai da fila", pendente.mensagemIdLocal)
                envioDao.remover(pendente.mensagemIdLocal)
                liberarArquivos(pendente.payloadJson)
                continue
            }
            if (mensagem.mensagem.falhou) {
                Timber.tag(TAG).d("Mensagem local %d está como falhou: espera o \"Reenviar\"", pendente.mensagemIdLocal)
                continue
            }
            var pacote = ConversaJson.decodeFromString(PacoteEnvio.serializer(), pendente.payloadJson)
            val inicio = System.currentTimeMillis()
            Timber.tag(TAG).i(
                "Enviando a mensagem local %d (conversa %d, tentativa %d, %d anexo(s), na fila desde %s)",
                pendente.mensagemIdLocal,
                pendente.conversaId,
                pendente.tentativas + 1,
                pacote.anexos.size,
                pendente.criadoEm,
            )

            // 1. Anexos que ainda não subiram.
            val falhaAnexo = try {
                pacote = subirAnexos(pendente, pacote)
                null
            } catch (_: FalhaDefinitiva) {
                Timber.tag(TAG).w("Mensagem local %d: o anexo não tem como ir; marcada como falhou", pendente.mensagemIdLocal)
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
                Timber.tag(TAG).i(
                    "Mensagem local %d enviada: é a %d no servidor (%d ms)",
                    pendente.mensagemIdLocal,
                    criada.id,
                    System.currentTimeMillis() - inicio,
                )
                trocarPelaReal(mensagem, criada.id, pacote)
                envioDao.remover(pendente.mensagemIdLocal)
                liberarArquivos(pendente.payloadJson)
                _progresso.update { it - pendente.mensagemIdLocal }
                enviouAlguma = true
                continue
            }
            val erro = resultado.exceptionOrNull()
            Timber.tag(TAG).w(
                "Mensagem local %d não foi (%d ms): %s",
                pendente.mensagemIdLocal,
                System.currentTimeMillis() - inicio,
                erro?.resumoDoErro(),
            )
            when (erro) {
                is ErroApi.SemConexao -> {
                    Timber.tag(TAG).i("Sem conexão: o WorkManager tenta de novo depois")
                    return@withLock ResultadoEnvio.TENTAR_DEPOIS
                }
                is ErroApi.SessaoExpirada -> return@withLock ResultadoEnvio.CONCLUIDO
                is ErroApi.ServidorIndisponivel, is ErroApi.MuitasTentativas ->
                    if (!contarFalha(pendente)) return@withLock ResultadoEnvio.TENTAR_DEPOIS
                is ErroApi.Servidor -> if (erro.status >= 500 && !contarFalha(pendente)) {
                    return@withLock ResultadoEnvio.TENTAR_DEPOIS
                } else if (erro.status < 500) {
                    Timber.tag(TAG).w("Envio recusado pelo servidor (%d): marcada como falhou", erro.status)
                    mensagemDao.marcarEnvio(pendente.mensagemIdLocal, enviando = false, falhou = true)
                    if (erro.status == 403) _recusas.tryEmit(RecusaEnvio(mensagem.mensagem.conversaId, erro.detalhe))
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
                Timber.tag(TAG).w("Anexo %d sem acesso (a permissão do arquivo acabou)", anexo.ordem)
                throw FalhaDefinitiva()
            }
            val base = jaEnviados
            val tipo = TipoConteudo.de(anexo.tipo)
            // Áudio: mede uma vez (ANX-10) e a otimista já mostra a duração e a onda.
            val medido = anexo.duracao?.let { DadosAudio(it, anexo.intensidade.orEmpty()) }
                ?: if (tipo.ehAudio) medidor.medir(anexo.uri, anexo.tamanho) else null
            if (medido != null && anexo.duracao == null) {
                mensagemDao.gravarDadosAudio(
                    pendente.mensagemIdLocal,
                    anexo.ordem,
                    medido.duracao,
                    intensidadeParaTexto(medido.intensidade),
                )
            }
            val inicioAnexo = System.currentTimeMillis()
            Timber.tag(TAG).i("Subindo o anexo %d (%s, %d B)", anexo.ordem, tipo.name.lowercase(), anexo.tamanho)
            val enviado = anexos.enviar(fonte, tipo, medido) { enviados, _ ->
                _progresso.update { it + (pendente.mensagemIdLocal to ((base + enviados).toFloat() / total)) }
            }.onSuccess {
                Timber.tag(TAG).i("Anexo %d subiu em %d ms", anexo.ordem, System.currentTimeMillis() - inicioAnexo)
            }.getOrElse { erro ->
                Timber.tag(TAG).w("Anexo %d não subiu: %s", anexo.ordem, erro.resumoDoErro())
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
                            it.copy(identificador = enviado.identificador, duracao = medido?.duracao, intensidade = medido?.intensidade)
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
        if (pendente.tentativas + 1 < MAX_TENTATIVAS) {
            Timber.tag(TAG).i("Falha %d de %d: tenta de novo depois", pendente.tentativas + 1, MAX_TENTATIVAS)
            return false
        }
        Timber.tag(TAG).w("Mensagem local %d: %d falhas, marcada como falhou", pendente.mensagemIdLocal, MAX_TENTATIVAS)
        mensagemDao.marcarEnvio(pendente.mensagemIdLocal, enviando = false, falhou = true)
        return true
    }

    /**
     * Troca a otimista pela mensagem real. Busca a real (`mensagemreferencia=id`, sem
     * prévias nem seguintes = só ela); se não der, grava a otimista com o id real e os
     * identificadores dos anexos no lugar dos `local:`.
     */
    private suspend fun trocarPelaReal(otimista: MensagemCompleta, idReal: Long, pacote: PacoteEnvio) {
        // Resposta de postagem (7.13): não entra no fluxo. Relê a postagem, que traz as últimas respostas.
        pacote.corpo.postagemId?.let { postagemId ->
            chamarApi { api.mensagens(otimista.mensagem.conversaId, mensagemReferencia = postagemId) }
                .getOrNull()?.firstOrNull { it.id == postagemId }
                ?.let { mensagemDao.salvarCompletas(listOf(it.paraEntidade())) }
            mensagemDao.remover(otimista.mensagem.id)
            return
        }
        val identificadores = pacote.anexos.associate { it.ordem to it.identificador }
        val audios = pacote.anexos.filter { it.duracao != null }.associateBy { it.ordem }
        val real = chamarApi { api.mensagens(otimista.mensagem.conversaId, mensagemReferencia = idReal) }
            .getOrNull()?.firstOrNull { it.id == idReal }?.paraEntidade()
            ?: otimista.copy(
                mensagem = otimista.mensagem.copy(id = idReal, enviando = false, falhou = false),
                conteudos = otimista.conteudos.map {
                    val audio = audios[it.ordem]
                    it.copy(
                        mensagemId = idReal,
                        conteudo = identificadores[it.ordem] ?: it.conteudo,
                        duracao = audio?.duracao ?: it.duracao,
                        intensidade = audio?.intensidade?.let(::intensidadeParaTexto) ?: it.intensidade,
                    )
                },
            )
        mensagemDao.remover(otimista.mensagem.id)
        mensagemDao.salvarCompletas(listOf(real))
    }

    /** "Reenviar" numa mensagem que falhou. */
    suspend fun reenviar(idLocal: Long) {
        Timber.tag(TAG).i("Reenviar a mensagem local %d (pedido da pessoa)", idLocal)
        mensagemDao.marcarEnvio(idLocal, enviando = true, falhou = false)
        envioDao.zerarTentativas(idLocal)
        agendador.agendar()
    }

    /** "Apagar" numa mensagem que falhou (ela nunca chegou ao servidor). */
    suspend fun descartar(idLocal: Long) {
        Timber.tag(TAG).i("Apagar a mensagem local %d que não foi (pedido da pessoa)", idLocal)
        val pendente = envioDao.buscar(idLocal)
        envioDao.remover(idLocal)
        mensagemDao.remover(idLocal)
        _progresso.update { it - idLocal }
        if (pendente != null) liberarArquivos(pendente.payloadJson)
    }

    /** A pessoa tirou o arquivo da fila do campo sem enviar: libera, se nenhuma mensagem da fila o usa. */
    suspend fun desistirDoArquivo(uri: String) = liberar(listOf(uri))

    /**
     * Saiu da fila: devolve o acesso aos arquivos dela e apaga as fotos da câmera
     * ([FontesArquivo.liberar]), menos os que outra mensagem da fila ainda vai enviar.
     * Chamar depois de remover a linha da fila.
     */
    private suspend fun liberarArquivos(payloadJson: String) = liberar(lerPacote(payloadJson)?.anexos?.map { it.uri }.orEmpty())

    private suspend fun liberar(uris: List<String>) {
        if (uris.isEmpty()) return
        val emUso = envioDao.todos().flatMap { lerPacote(it.payloadJson)?.anexos.orEmpty() }.map { it.uri }.toSet()
        uris.distinct().filterNot { it in emUso }.forEach { fontes.liberar(it) }
    }

    private fun lerPacote(json: String): PacoteEnvio? = runCatching {
        ConversaJson.decodeFromString(PacoteEnvio.serializer(), json)
    }.getOrNull()

    /** Na abertura da sessão: se sobrou algo na fila (app morto no meio), agenda. */
    suspend fun retomar() {
        val pendentes = envioDao.todos().size
        if (pendentes > 0) {
            Timber.tag(TAG).i("%d mensagem(ns) ficaram na fila: agendando o envio", pendentes)
            agendador.agendar()
        }
    }

    companion object {
        const val TAG = "Envio"
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
    override suspend fun doWork(): Result {
        Timber.tag(EnvioMensagens.TAG).i("EnvioWorker: execução %d", runAttemptCount + 1)
        val resultado = try {
            envio.processarPendentes()
        } catch (e: CancellationException) {
            // O sistema parou o trabalho (rede caiu, cota do WorkManager, app fechado à força).
            // O motivo só existe no Android 12+.
            val motivo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) stopReason.toString() else "?"
            Timber.tag(EnvioMensagens.TAG).w("EnvioWorker parado pelo sistema (motivo %s)", motivo)
            throw e
        }
        Timber.tag(EnvioMensagens.TAG).i("EnvioWorker: %s", resultado)
        return when (resultado) {
            ResultadoEnvio.CONCLUIDO -> Result.success()
            ResultadoEnvio.TENTAR_DEPOIS -> Result.retry()
        }
    }
}

/** Agenda o [EnvioWorker] com rede. `APPEND_OR_REPLACE`: mensagens novas nunca ficam para trás. */
class AgendadorEnvioWorkManager @Inject constructor(@ApplicationContext private val contexto: Context) : AgendadorEnvio {
    override fun agendar() {
        Timber.tag(EnvioMensagens.TAG).d("Envio agendado no WorkManager (espera haver rede)")
        val pedido = OneTimeWorkRequestBuilder<EnvioWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(NOME, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
    }

    override fun cancelar() {
        Timber.tag(EnvioMensagens.TAG).i("Envio cancelado no WorkManager")
        WorkManager.getInstance(contexto).cancelUniqueWork(NOME)
    }

    companion object {
        /** O trabalho único do envio no WorkManager (o registro do app acompanha o estado dele). */
        const val NOME = "envio_mensagens"
    }
}

/** Uma mensagem que o servidor recusou ([motivo] é o texto dele), na conversa [conversaId]. */
data class RecusaEnvio(val conversaId: Long, val motivo: String)
