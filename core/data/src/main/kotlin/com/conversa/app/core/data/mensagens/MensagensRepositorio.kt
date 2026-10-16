package com.conversa.app.core.data.mensagens

import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.dao.ConversaDao
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.StatusDestinatario
import com.conversa.app.core.model.alternarReacao
import com.conversa.app.core.model.emojiAceito
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.dto.IdDto
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.MensagemIdDto
import com.conversa.app.core.network.dto.PedirConfirmacaoRequisicao
import com.conversa.app.core.network.dto.ReacaoRequisicao
import com.conversa.app.core.network.dto.VisualizarPostagemRequisicao
import com.conversa.app.core.network.http.chamarApi
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Mensagens de uma conversa (MSG-01, MSG-04). O Room é a fonte da tela; a rede só
 * acrescenta. `GET /mensagens` devolve no máximo 100, sempre em ordem crescente, e
 * **inclui** a mensagem de referência (contrato §10.4).
 */
@Singleton
class MensagensRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    private val conversaDao: ConversaDao,
    @EscopoAplicacao private val escopo: CoroutineScope,
    private val relogio: Clock,
) {
    fun observar(conversaId: Long): Flow<List<Mensagem>> =
        mensagemDao.observarDaConversa(conversaId).map { lista -> lista.map { it.paraModelo() } }

    /** Ao abrir a conversa: as [RECENTES] mais recentes (o que já está no Room aparece antes). */
    suspend fun carregarRecentes(conversaId: Long): Result<Int> =
        chamarApi { api.mensagens(conversaId, mensagensPrevias = RECENTES) }.map { salvar(it) }

    /**
     * Rolou para cima: [PAGINA] antes da mais antiga salva. Devolve quantas mensagens
     * **novas** chegaram (a de referência vem repetida e não conta); 0 = chegou ao começo.
     */
    suspend fun carregarAnteriores(conversaId: Long): Result<Int> {
        val primeira = mensagemDao.primeiraSalva(conversaId) ?: return carregarRecentes(conversaId)
        return chamarApi { api.mensagens(conversaId, mensagemReferencia = primeira, mensagensPrevias = PAGINA) }
            .map { lista -> salvar(lista.filter { it.id != primeira }) }
    }

    /** Rolou para baixo depois de um salto: [PAGINA] depois da mais nova salva. 0 = chegou ao fim. */
    suspend fun carregarSeguintes(conversaId: Long): Result<Int> {
        val ultima = mensagemDao.ultimaSalva(conversaId) ?: return carregarRecentes(conversaId)
        return chamarApi { api.mensagens(conversaId, mensagemReferencia = ultima, mensagensSeguintes = PAGINA) }
            .map { lista -> salvar(lista.filter { it.id != ultima }) }
    }

    /**
     * Ir para a mensagem (MSG-06, TODO 7.5): garante que [mensagemId] está no aparelho sem
     * deixar buraco na conversa. Volta de [PAGINA_SALTO] em [PAGINA_SALTO] a partir da mais
     * antiga salva até ela aparecer, no máximo [MAXIMO_PAGINAS_SALTO] vezes. `true` = está.
     */
    suspend fun trazerAte(conversaId: Long, mensagemId: Long): Result<Boolean> {
        repeat(MAXIMO_PAGINAS_SALTO + 1) { tentativa ->
            if (mensagemDao.buscar(mensagemId)?.mensagem?.conversaId == conversaId) return Result.success(true)
            if (tentativa == MAXIMO_PAGINAS_SALTO) return Result.success(false)
            val primeira = mensagemDao.primeiraSalva(conversaId)
            val novas = chamarApi {
                if (primeira == null) {
                    api.mensagens(conversaId, mensagensPrevias = PAGINA_SALTO)
                } else {
                    api.mensagens(conversaId, mensagemReferencia = primeira, mensagensPrevias = PAGINA_SALTO)
                }
            }.map { lista -> salvar(lista.filter { it.id != primeira }) }
                .getOrElse { return Result.failure(it) }
            if (novas == 0) return Result.success(mensagemDao.buscar(mensagemId)?.mensagem?.conversaId == conversaId)
        }
        return Result.success(false)
    }

    /** Minhas respostas de postagem ainda na fila (7.13), por postagem: a bolha mostra embaixo dela. */
    fun observarRespostasLocais(conversaId: Long): Flow<Map<Long, List<Mensagem>>> =
        mensagemDao.observarRespostasLocais(conversaId).map { lista ->
            lista.map { it.paraModelo() }.groupBy { it.postagemId ?: 0 }
        }

    /** Todas as respostas de uma postagem ("Ver todas", 7.13), da mais antiga à mais nova. */
    suspend fun respostas(postagemId: Long): Result<List<Mensagem>> =
        chamarApi { api.respostasPostagem(postagemId) }.map { lista -> lista.map { it.paraEntidade().paraModelo() } }

    /**
     * "Confirmar leitura" (FC-521): `POST /mensagem/confirmar` (idempotente) e relê a mensagem. O WS 8
     * vai para os outros membros, não para mim.
     */
    suspend fun confirmarLeitura(conversaId: Long, mensagemId: Long): Result<Unit> =
        chamarApi { api.confirmarLeitura(MensagemIdDto(mensagemId)) }.map { recarregar(conversaId, mensagemId) }

    /** "Pedir confirmação de leitura" numa minha já enviada (FC-525); [usuarios] nulo = todos os outros membros. */
    suspend fun pedirConfirmacao(conversaId: Long, mensagemId: Long, usuarios: List<Long>?): Result<Unit> =
        chamarApi { api.pedirConfirmacao(PedirConfirmacaoRequisicao(mensagemId, usuarios)) }.map { recarregar(conversaId, mensagemId) }

    /**
     * O chat de uma ligação, só para ler (FC-727): as últimas 100, sem gravar no Room (quem não abriu
     * esse chat não precisa dele no aparelho).
     */
    suspend fun chatDaChamada(conversaId: Long): Result<List<Mensagem>> =
        chamarApi { api.mensagens(conversaId, mensagensPrevias = LIMITE_CHAT_DA_CHAMADA) }
            .map { lista -> lista.map { it.paraEntidade().paraModelo() } }

    /** Uma mensagem do aparelho (a pendente do "Responder no privado", 7.6). */
    suspend fun buscar(mensagemId: Long): Mensagem? = mensagemDao.buscar(mensagemId)?.paraModelo()

    /** Relê uma mensagem do servidor (reações com nomes, marca de oculta). */
    suspend fun recarregar(conversaId: Long, mensagemId: Long): Result<Unit> =
        chamarApi {
            api.mensagens(conversaId, mensagemReferencia = mensagemId)
        }.map { lista -> salvar(lista.filter { it.id == mensagemId }) }

    /**
     * Reagir (ENV-17, FC-501): mostra na hora (otimista), manda o `PUT /mensagem/reacao`
     * (que alterna) e relê a mensagem para ficar igual ao servidor — inclusive se falhar.
     */
    suspend fun reagir(conversaId: Long, mensagemId: Long, emoji: String, eu: Long, meuNome: String): Result<Unit> {
        if (!emojiAceito(emoji)) return Result.failure(IllegalArgumentException("Emoji inválido"))
        val atuais = mensagemDao.buscar(mensagemId)?.paraModelo()?.reacoes ?: emptyList()
        val novas = alternarReacao(atuais, emoji, eu, meuNome, relogio.instant())
        mensagemDao.trocarReacoes(mensagemId, novas.mapIndexed { ordem, reacao -> reacao.paraEntidade(mensagemId, ordem) })
        val resultado = chamarApi { api.reagir(ReacaoRequisicao(mensagemId, emoji)) }.map { }
        recarregar(conversaId, mensagemId)
        return resultado
    }

    /**
     * Ocultar (ENV-18, FC-503): `DELETE /mensagem`. Com `excluida_em` a mensagem fica marcada
     * como oculta; sem, era agendada e foi apagada de vez.
     */
    suspend fun ocultar(mensagemId: Long): Result<Unit> = chamarApi { api.ocultarMensagem(mensagemId) }.map { resposta ->
        val excluidaEm = resposta.excluidaEm
        if (excluidaEm != null) mensagemDao.atualizarOculta(mensagemId, excluidaEm.toEpochMilli()) else mensagemDao.remover(mensagemId)
    }

    /**
     * Pesquisa (8.2, PES-01/02): `GET /pesquisar` em uma conversa ou em todas (`conversa = 0`),
     * mais recentes primeiro. Os resultados não vão para o Room: uma mensagem antiga solta ali
     * abriria um buraco na lista do chat (o "ir para a mensagem" traz o caminho todo).
     */
    /**
     * `GET /pesquisar` (mais recentes primeiro). Com [conversaId], o servidor (`6d20ff7`) traz
     * também as mensagens dos chats das ligações feitas nela: não filtrar por conversa aqui.
     */
    suspend fun pesquisar(texto: String, conversaId: Long = 0): Result<List<Mensagem>> =
        chamarApi { api.pesquisar(texto.trim(), conversaId) }.map { lista ->
            lista.map { it.paraEntidade().paraModelo() }.sortedByDescending { it.dataEfetiva }
        }

    /** Detalhe do status (7.10, FC-513): `GET /mensagem/status/detalhe`, uma linha por destinatário, com datas. */
    suspend fun statusDetalhe(mensagemId: Long): Result<List<StatusDestinatario>> =
        chamarApi { api.statusDetalhe(mensagemId) }.map { lista ->
            lista.map { StatusDestinatario(it.usuarioId, it.nome, it.recebida, it.visualizada, it.reproduzida) }
        }

    private suspend fun salvar(lista: List<com.conversa.app.core.network.dto.MensagemDto>): Int {
        mensagemDao.salvarCompletas(lista.map { it.paraEntidade() })
        return lista.size
    }

    /** `POST /conversa/digitando` (ENV-15). Melhor esforço: falha não importa. */
    suspend fun avisarDigitando(conversaId: Long) {
        chamarApi { api.avisarDigitando(IdDto(conversaId)) }
    }

    /** Gravando áudio (ANX-11): a tela chama a cada 2,5 s enquanto a gravação está aberta (WS 5 aos outros). */
    suspend fun avisarGravando(conversaId: Long) {
        chamarApi { api.avisarGravando(IdDto(conversaId)) }
    }

    // --- Lida (MSG-04) ---

    private val filaLida = Channel<Pair<Long, Long>>(Channel.UNLIMITED)
    private val jaPedidas = mutableSetOf<Long>()
    private var filaIniciada = false

    /**
     * Marca como lida uma mensagem de outra pessoa que apareceu na tela. Otimista:
     * atualiza o Room e o contador da conversa na hora; a fila envia
     * `POST /mensagem/visualizar` uma por vez (a rota marca uma só; pendência S9).
     */
    suspend fun marcarLida(conversaId: Long, mensagemId: Long) {
        if (mensagemId <= 0) return
        val nova = synchronized(jaPedidas) { jaPedidas.add(mensagemId) }
        if (!nova) return
        iniciarFila()
        mensagemDao.marcarLidaPorMim(mensagemId)
        conversaDao.descontarNaoLida(conversaId)
        filaLida.send(conversaId to mensagemId)
    }

    /** Postagens (id, resposta mais recente) já marcadas nesta sessão: uma resposta nova pede de novo. */
    private val postagensVistas = mutableSetOf<Pair<Long, Long>>()

    /**
     * Respostas de postagem (MSG-22, FC-316): ficam fora do fluxo do chat, mas contam nas não
     * lidas da conversa. Quando a postagem aparece na tela com respostas novas, marca todas como
     * vistas (`POST /postagem/visualizar`), desconta o contador na hora e relê a postagem.
     * [ultimaResposta] (a mais recente das `ultimas`) separa um pedido do seguinte.
     */
    suspend fun visualizarPostagem(conversaId: Long, postagemId: Long, naoLidas: Int, ultimaResposta: Long) {
        if (postagemId <= 0 || naoLidas <= 0) return
        val chave = postagemId to ultimaResposta
        val nova = synchronized(postagensVistas) { postagensVistas.add(chave) }
        if (!nova) return
        conversaDao.descontarNaoLidas(conversaId, naoLidas)
        chamarApi { api.visualizarPostagem(VisualizarPostagemRequisicao(postagemId)) }
            .onSuccess { recarregar(conversaId, postagemId) }
            .onFailure {
                synchronized(postagensVistas) { postagensVistas.remove(chave) }
                Timber.d("Falha ao marcar a postagem como vista: %s", it.javaClass.simpleName)
            }
    }

    private fun iniciarFila() {
        synchronized(jaPedidas) {
            if (filaIniciada) return
            filaIniciada = true
        }
        escopo.launch {
            for ((conversa, mensagem) in filaLida) {
                chamarApi { api.visualizar(MarcarStatusRequisicao(conversa, mensagem)) }
                    .onFailure {
                        // Deixa pedir de novo na próxima vez que aparecer na tela.
                        synchronized(jaPedidas) { jaPedidas.remove(mensagem) }
                        Timber.d("Falha ao marcar como lida: %s", it.javaClass.simpleName)
                    }
            }
        }
    }

    /**
     * "Marcar como lida" da notificação (FC-604): todas as de outras pessoas ainda não lidas.
     * Diferente de [marcarLida], espera cada `POST`: quem chama é uma ação de notificação,
     * que tem pouco tempo de vida.
     */
    /**
     * "Marcar como lida" e "Responder" na notificação (FC-610): as respostas das postagens avisadas
     * não estão no Room. `POST /postagem/visualizar` de cada postagem; o contador da conversa desce
     * o que o servidor marcou, e a postagem que está no aparelho é relida.
     */
    suspend fun marcarPostagensVistas(conversaId: Long, postagens: Collection<Long>) {
        for (postagemId in postagens.distinct()) {
            chamarApi { api.visualizarPostagem(VisualizarPostagemRequisicao(postagemId)) }
                .onSuccess { conversaDao.descontarNaoLidas(conversaId, it.vistas) }
                .onFailure { Timber.d("Falha ao marcar a postagem como vista: %s", it.javaClass.simpleName) }
            if (mensagemDao.buscar(postagemId) != null) recarregar(conversaId, postagemId)
        }
    }

    suspend fun marcarConversaLida(conversaId: Long, eu: Long) {
        for (completa in mensagemDao.naoLidasDeOutros(conversaId, eu, Int.MAX_VALUE)) {
            val id = completa.mensagem.id
            if (!synchronized(jaPedidas) { jaPedidas.add(id) }) continue
            mensagemDao.marcarLidaPorMim(id)
            conversaDao.descontarNaoLida(conversaId)
            chamarApi { api.visualizar(MarcarStatusRequisicao(conversaId, id)) }
                .onFailure { synchronized(jaPedidas) { jaPedidas.remove(id) } }
        }
    }

    // --- Reproduzida (ANX-10) ---

    private val jaReproduzidas = mutableSetOf<Long>()

    /**
     * Primeiro play de um áudio de outra pessoa: o botão deixa de ser verde na hora
     * (Room) e `POST /mensagem/reproduzir` vai uma vez. Se falhar, pode pedir de novo.
     */
    suspend fun marcarReproduzida(conversaId: Long, mensagemId: Long) {
        if (mensagemId <= 0) return
        if (!synchronized(jaReproduzidas) { jaReproduzidas.add(mensagemId) }) return
        mensagemDao.marcarReproduzidaPorMim(mensagemId)
        chamarApi { api.reproduzir(MarcarStatusRequisicao(conversaId, mensagemId)) }
            .onFailure {
                synchronized(jaReproduzidas) { jaReproduzidas.remove(mensagemId) }
                Timber.d("Falha ao marcar como reproduzida: %s", it.javaClass.simpleName)
            }
    }

    /** Fim da sessão. */
    fun limpar() {
        synchronized(jaPedidas) { jaPedidas.clear() }
        synchronized(jaReproduzidas) { jaReproduzidas.clear() }
    }

    companion object {
        /** Igual ao web: 80 ao abrir, 60 por página. */
        const val RECENTES = 80
        const val PAGINA = 60

        /** O servidor devolve no máximo 100 por chamada: 99 antes + a de referência. */
        const val PAGINA_SALTO = 99

        /** Até ~2.000 mensagens para trás; além disso, "não foi possível localizar". */
        const val MAXIMO_PAGINAS_SALTO = 20
    }
}

/** Quantas mensagens o chat da ligação mostra (como o web). */
private const val LIMITE_CHAT_DA_CHAMADA = 100
