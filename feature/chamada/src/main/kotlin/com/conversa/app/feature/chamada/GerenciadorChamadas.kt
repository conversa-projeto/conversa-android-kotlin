package com.conversa.app.feature.chamada

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.chamadas.ChamadasRemotas
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.model.Chamada
import com.conversa.app.core.model.ChamadaPendente
import com.conversa.app.core.model.StatusParticipante
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.registro.resumoDoErro
import com.conversa.app.core.webrtc.EtapaVideo
import com.conversa.app.core.webrtc.MidiaChamada
import com.conversa.app.core.webrtc.MidiaLocal
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber

/** Onde o gerenciador roda: uma coisa por vez. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DespachanteChamadas

@Module
@InstallIn(SingletonComponent::class)
object ChamadaModulo {
    @Provides
    @DespachanteChamadas
    fun despachante(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
}

/**
 * Único dono da chamada (TODO 6.2, FC-700, plano §3.4). Recebe os comandos da tela,
 * do Telecom e da notificação, os eventos 51–57 do WebSocket e as chamadas pendentes,
 * fala com o servidor (contrato §9.2) e manda a mídia ([MidiaChamada]) agir.
 * O resto (toque, notificação, Telecom, serviço) observa [estado].
 *
 * Segue o comportamento do web (`conversa-web/src/stores/call.ts`). Roda num
 * despachante de uma coisa por vez, como o JavaScript: o estado só muda entre as
 * suspensões. Quem espera a rede guarda a [geracao] e confere, na volta, se a
 * chamada ainda é a mesma.
 */
@Singleton
class GerenciadorChamadas @Inject constructor(
    private val remoto: ChamadasRemotas,
    private val midia: MidiaChamada,
    private val tempoReal: RealtimeClient,
    private val sincronizacao: SyncManager,
    private val conexao: ConexaoTempoReal,
    private val sessao: SessaoRepositorio,
    private val relogio: Clock,
    @EscopoAplicacao escopoApp: CoroutineScope,
    @DespachanteChamadas despachante: CoroutineDispatcher,
) {
    private val escopo = CoroutineScope(
        SupervisorJob(escopoApp.coroutineContext[Job]) + despachante +
            CoroutineExceptionHandler { _, e -> Timber.tag(TAG).e(e, "Falha no gerenciador de chamadas") },
    )

    private val _estado = MutableStateFlow(EstadoChamada())
    val estado: StateFlow<EstadoChamada> = _estado.asStateFlow()

    private val _avisos = MutableSharedFlow<AvisoChamada>(extraBufferCapacity = 8)
    val avisos: SharedFlow<AvisoChamada> = _avisos.asSharedFlow()

    private var iniciado = false

    /** Muda ao abrir uma chamada, ao entrar em ENCERRANDO e ao voltar a INATIVO. */
    private var geracao = 0L

    /** Temporizadores e sincronizações da chamada atual; cancelado ao encerrar. */
    private var trabalho: Job? = null
    private var monitor: Job? = null
    private var sincronizando = false
    private var ativandoVideo = false

    /** Chamadas que já acabaram aqui: um 51 atrasado ou uma pendente antiga não tocam de novo. */
    private val encerradas = ArrayDeque<Long>()

    /** Das [encerradas], as que o servidor encerrou (52): estas nem o "Chamar de novo" reabre. */
    private val finalizadas = ArrayDeque<Long>()

    /** Chamar uma vez (no `Application`). */
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch { tempoReal.eventos.collect { evento -> escopo.launch { tratar(evento) } } }
        escopo.launch { sincronizacao.chamadasPendentes.collect { lista -> escopo.launch { avaliarPendentes(lista) } } }
        // Cada mudança da chamada vai para o registro do app (fase, participantes, mídia).
        escopo.launch {
            _estado.map { it.resumoParaRegistro() }.distinctUntilChanged().drop(1).collect { Timber.tag(TAG).i("Estado: %s", it) }
        }
        // O WebSocket fica ligado em segundo plano enquanto houver chamada.
        escopo.launch {
            _estado.map { it.fase != FaseChamada.INATIVO }.distinctUntilChanged().collect { conexao.chamadaAtiva.value = it }
        }
        escopo.launch {
            sessao.sessao.map { it?.usuarioId }.distinctUntilChanged().collect { if (it == null) encerrar() }
        }
    }

    // --- Comandos ---

    /** Liga para [participantes] (eu entro sozinho na lista). Só sem outra chamada. */
    fun ligar(tipo: TipoChamada, participantes: List<Long>, conversaId: Long?) {
        Timber.tag(TAG).i("Ligar (%s) para %s, conversa %s", tipo.name.lowercase(), participantes.joinToString(), conversaId)
        escopo.launch {
            val eu = eu() ?: return@launch
            if (_estado.value.fase != FaseChamada.INATIVO) {
                _avisos.tryEmit(AvisoChamada.JaEmChamada)
                return@launch
            }
            val g = abrir(EstadoChamada(fase = FaseChamada.CHAMANDO, tipo = tipo, remetenteId = eu))
            val local = midia.abrirLocal(video = tipo == TipoChamada.VIDEO)
            if (!vivo(g)) return@launch
            if (local == MidiaLocal.NENHUMA) {
                encerrar()
                _avisos.tryEmit(AvisoChamada.MicrofoneIndisponivel)
                return@launch
            }
            atualizar { it.copy(midiaLocal = local, cameraLigada = local == MidiaLocal.AUDIO_VIDEO) }
            val chamada = try {
                remoto.iniciar(tipo, (participantes + eu).distinct(), conversaId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (vivo(g)) {
                    encerrar()
                    _avisos.tryEmit(AvisoChamada.Falhou(e.message))
                }
                return@launch
            }
            if (!vivo(g)) {
                // Desligou enquanto o servidor criava: cancela a que acabou de nascer.
                lembrarEncerrada(chamada.id)
                escopo.launch { tentar { remoto.cancelar(chamada.id) } }
                return@launch
            }
            atualizar { it.copy(chamadaId = chamada.id, dados = chamada) }
            noTrabalho { semResposta(chamada.id) }
            garantirMonitor(chamada.id)
            // Publica já, para quem atender encontrar a transmissão (como o web).
            tentar { midia.publicar(chamada.id, eu) }
        }
    }

    /** Atende a chamada que está tocando. [soAssistir]: entra com a câmera desligada. */
    fun atender(soAssistir: Boolean = false) {
        Timber.tag(TAG).i(if (soAssistir) "Atender só assistindo" else "Atender")
        escopo.launch {
            val atual = _estado.value
            val id = atual.chamadaId ?: return@launch
            if (!atual.tocando) return@launch
            val eu = eu() ?: return@launch
            atualizar { it.copy(fase = FaseChamada.CONECTANDO) }
            val g = geracao
            // Vídeo + áudio → só áudio → sem nada (só recebe).
            val local = midia.abrirLocal(video = atual.tipo == TipoChamada.VIDEO)
            if (!vivo(g)) return@launch
            val comCamera = local == MidiaLocal.AUDIO_VIDEO && !soAssistir
            if (local == MidiaLocal.AUDIO_VIDEO && soAssistir) midia.camera(false)
            atualizar {
                it.copy(
                    midiaLocal = local,
                    microfoneLigado = local != MidiaLocal.NENHUMA,
                    cameraLigada = comCamera,
                    // Vídeo sem mandar vídeo (só assistindo ou sem câmera): tela única em quem ligou, como o web.
                    exibicao = if (atual.tipo == TipoChamada.VIDEO &&
                        !comCamera
                    ) {
                        Exibicao(ModoExibicao.UNICA, atual.remetenteId)
                    } else {
                        it.exibicao
                    },
                )
            }
            // Publica antes do `entrar` (FC-729, web `d244fc8`): o web que ligou já pré-assina e recebe o
            // áudio sem tocar o som de espera. O `entrar` (o 54) continua sendo o sinal de que atendeu e
            // espera a publicação até [ESPERA_PUBLICAR_ANTES_MS]; se o `entrar` falhar, o encerrar a desfaz.
            val publicacao = if (local != MidiaLocal.NENHUMA) async { tentar { midia.publicar(id, eu) } } else null
            withTimeoutOrNull(ESPERA_PUBLICAR_ANTES_MS) { publicacao?.await() }
            if (!vivo(g)) return@launch
            try {
                remoto.entrar(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (vivo(g)) {
                    encerrar()
                    _avisos.tryEmit(AvisoChamada.Falhou(e.message))
                }
                return@launch
            }
            if (!vivo(g)) return@launch
            publicacao?.await()
            if (!vivo(g)) return@launch
            ficarAtiva(id)
        }
    }

    /** Recusa a chamada que está tocando: **um** `POST /chamada/recusar`. */
    fun recusar() {
        Timber.tag(TAG).i("Recusar")
        escopo.launch { if (_estado.value.fase == FaseChamada.RECEBENDO) recusarAgora(naoAtendeu = false) }
    }

    /** O botão vermelho: cancela (chamando), recusa (tocando) ou sai (na chamada). */
    fun desligar() {
        Timber.tag(TAG).i("Desligar")
        escopo.launch { desligarAgora() }
    }

    fun alternarMicrofone() {
        escopo.launch {
            val atual = _estado.value
            if (!atual.emChamada || atual.midiaLocal == MidiaLocal.NENHUMA) return@launch
            val ligado = !atual.microfoneLigado
            midia.microfone(ligado)
            atualizar { it.copy(microfoneLigado = ligado) }
        }
    }

    fun alternarCamera() {
        escopo.launch {
            val atual = _estado.value
            if (!atual.emChamada || atual.midiaLocal != MidiaLocal.AUDIO_VIDEO) return@launch
            val ligada = !atual.cameraLigada
            midia.camera(ligada)
            atualizar { it.copy(cameraLigada = ligada) }
        }
    }

    /**
     * "Adicionar à chamada" (6.13): um `PUT /chamada/usuario` por pessoa, em sequência (como o web),
     * e depois os dados da chamada. Quem já está nela recebe o WS 51 e passa a ver o novo participante.
     */
    fun adicionar(usuarios: List<Long>) {
        escopo.launch {
            val atual = _estado.value
            val id = atual.chamadaId ?: return@launch
            if (atual.fase != FaseChamada.ATIVA || usuarios.isEmpty()) return@launch
            val g = geracao
            for (usuario in usuarios.distinct()) {
                try {
                    remoto.adicionar(id, usuario)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (vivo(g)) _avisos.tryEmit(AvisoChamada.Falhou(e.message))
                }
                if (!vivo(g)) return@launch
            }
            atualizarDados(id)
        }
    }

    /**
     * "Chamar de novo" (FC-726, contrato §9.14): toca outra vez para quem recusou ou deixou tocar
     * sem atender, com a chamada em andamento. A resposta já traz os dados; os outros participantes
     * recebem o WS 57 `{acao:"participantes"}`. Se falhar, relê os dados e devolve o erro.
     */
    suspend fun chamarNovamente(usuario: Long): Result<Unit> = escopo.async {
        val atual = _estado.value
        val id = atual.chamadaId
        if (id == null || atual.fase !in FASES_CHAMAR_DE_NOVO) return@async Result.success(Unit)
        val g = geracao
        try {
            val dados = remoto.chamarNovamente(id, usuario)
            if (vivo(g)) atualizar { it.copy(dados = dados, conversaChatId = dados.conversaChatId ?: it.conversaChatId) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (vivo(g)) atualizarDados(id)
            Result.failure(e)
        }
    }.await()

    /**
     * O chat da chamada (6.13): a conversa que já existe ou, na primeira vez, a criada agora
     * (`PUT /chamada/chat`; os outros recebem o WS 57 `{acao:"chat"}`). Nulo sem chamada ou se falhar.
     */
    suspend fun garantirChat(): Long? = escopo.async {
        val atual = _estado.value
        atual.conversaChatId?.let { return@async it }
        val id = atual.chamadaId ?: return@async null
        if (!atual.emChamada) return@async null
        val g = geracao
        val conversa = tentar { remoto.chat(id) } ?: return@async null
        if (vivo(g)) atualizar { it.copy(conversaChatId = conversa) }
        conversa
    }.await()

    /**
     * Somente recepção (6.13): quem entrou sem microfone nem câmera passa a transmitir
     * ("Ativar microfone" / "Ativar câmera", como o web). Sem microfone, avisa.
     */
    fun ativarTransmissao(video: Boolean) {
        escopo.launch {
            val atual = _estado.value
            val id = atual.chamadaId ?: return@launch
            if (atual.fase != FaseChamada.ATIVA || atual.midiaLocal != MidiaLocal.NENHUMA) return@launch
            val eu = eu() ?: return@launch
            val g = geracao
            val local = midia.abrirLocal(video)
            if (!vivo(g)) return@launch
            if (local == MidiaLocal.NENHUMA) {
                _avisos.tryEmit(AvisoChamada.MicrofoneIndisponivel)
                return@launch
            }
            atualizar { it.copy(midiaLocal = local, microfoneLigado = true, cameraLigada = local == MidiaLocal.AUDIO_VIDEO) }
            tentar { midia.publicar(id, eu) }
        }
    }

    /** Liga o vídeo numa chamada de áudio e avisa os outros (WS 56). */
    fun ligarVideo() {
        escopo.launch { ativarVideo(notificar = true, transmitir = true) }
    }

    /** Grade, destaque ou tela única; [destaque] é quem fica grande (na grade, ignorado). */
    fun exibir(modo: ModoExibicao, destaque: Long? = null) {
        escopo.launch {
            if (_estado.value.fase == FaseChamada.INATIVO) return@launch
            atualizar {
                it.copy(
                    exibicao = if (modo ==
                        ModoExibicao.GRADE
                    ) {
                        Exibicao()
                    } else {
                        Exibicao(modo, destaque ?: it.exibicao.destaque)
                    },
                )
            }
        }
    }

    /** Resposta ao [EstadoChamada.pedidoVideo]: "Transmitir também" ou "Apenas assistir". */
    fun responderVideo(transmitir: Boolean) {
        escopo.launch { responderVideoAgora(transmitir) }
    }

    // --- Eventos do WebSocket ---

    private suspend fun tratar(evento: EventoSocket) {
        val eu = eu() ?: return
        when (evento) {
            is EventoSocket.ChamadaRecebida -> recebida(evento.chamadaId, evento.usuarioId, eu)
            is EventoSocket.ChamadaFinalizada -> {
                lembrar(finalizadas, evento.chamadaId)
                if (_estado.value.chamadaId == evento.chamadaId) encerrar() else lembrarEncerrada(evento.chamadaId)
            }
            is EventoSocket.UsuarioRecusou -> recusou(evento.chamadaId, evento.usuarioId, eu)
            is EventoSocket.UsuarioEntrou -> entrou(evento.chamadaId, evento.usuarioId, eu)
            is EventoSocket.UsuarioSaiu -> saiu(evento.chamadaId, evento.usuarioId, eu)
            is EventoSocket.VideoAtivado -> videoAtivado(evento.chamadaId, evento.usuarioId, eu)
            is EventoSocket.SinalChamada -> sinal(evento.chamadaId, evento.usuarioId, evento.dados, eu)
            else -> Unit
        }
    }

    /** 51 (ou pendente): toca, ou recusa sem tocar se estou ocupado (FC-708). */
    private suspend fun recebida(id: Long, de: Long, eu: Long) {
        if (id in encerradas) {
            if (id in finalizadas || !chamadoDeNovo(id, eu)) return
            encerradas.remove(id)
        }
        val atual = _estado.value
        if (atual.chamadaId == id) {
            // Alguém foi adicionado à chamada em que já estou.
            if (atual.emChamada) noTrabalho { sincronizar(id) }
            return
        }
        // Eu mesmo adicionei alguém, em outro aparelho.
        if (de == eu && atual.fase == FaseChamada.INATIVO) return
        if (atual.fase != FaseChamada.INATIVO) {
            // Ocupado: nem toca; vira chamada perdida.
            lembrarEncerrada(id)
            escopo.launch { tentar { remoto.recusar(id, naoAtendeu = true) } }
            return
        }
        val g = abrir(EstadoChamada(fase = FaseChamada.RECEBENDO, chamadaId = id, remetenteId = de))
        val dados = tentar { remoto.dados(id) }
        if (!vivo(g)) return
        val meu = dados?.participantes?.firstOrNull { it.usuarioId == eu }
        when {
            // Sem os dados não dá para tocar; a próxima pendente tenta de novo.
            dados == null -> encerrar(lembrar = false)
            // Já acabou, ou já atendi/recusei em outro aparelho.
            dados.status.final || meu?.status != StatusParticipante.PENDENTE -> encerrar()
            else -> {
                atualizar { it.copy(dados = dados, tipo = dados.tipo, conversaChatId = dados.conversaChatId) }
                noTrabalho { tocar(id) }
            }
        }
    }

    /**
     * "Chamar de novo" (FC-724, contrato §9.14): o servidor manda outro 51 de uma chamada que
     * este aparelho já recusou ou deixou passar. Só toca de novo se fui mesmo chamado outra vez:
     * o meu status voltou a pendente com a recusa anterior registrada (no 51 repetido da primeira
     * vez, ou antes de o servidor processar a minha recusa, `recusou_em` ainda está vazio) e a
     * chamada continua.
     */
    private suspend fun chamadoDeNovo(id: Long, eu: Long): Boolean {
        val dados = tentar { remoto.dados(id) } ?: return false
        val meu = dados.participantes.firstOrNull { it.usuarioId == eu } ?: return false
        return !dados.status.final && meu.status == StatusParticipante.PENDENTE && meu.recusouEm != null
    }

    private suspend fun recusou(id: Long, quem: Long, eu: Long) {
        val atual = _estado.value
        if (atual.chamadaId != id) return
        if (quem == eu) {
            // Recusei em outro aparelho: aqui para de tocar.
            if (atual.fase == FaseChamada.RECEBENDO) encerrar()
            return
        }
        if (atual.fase != FaseChamada.CHAMANDO && atual.fase != FaseChamada.ATIVA) return
        val dados = atualizarDados(id) ?: return
        conferirSeAcabou(id, dados, eu)
    }

    private fun entrou(id: Long, quem: Long, eu: Long) {
        val atual = _estado.value
        if (atual.chamadaId != id) return
        if (quem == eu) {
            // Atendi em outro aparelho (o meu próprio 54 daqui chega já em CONECTANDO).
            if (atual.fase == FaseChamada.RECEBENDO) encerrar()
            return
        }
        when (atual.fase) {
            FaseChamada.CHAMANDO -> ficarAtiva(id)
            FaseChamada.CONECTANDO, FaseChamada.ATIVA -> noTrabalho { sincronizarComRetentativas(id) }
            else -> Unit
        }
    }

    /** 55: o servidor **não** manda o 52 quando a chamada acaba por saída; conferir pelos dados. */
    private suspend fun saiu(id: Long, quem: Long, eu: Long) {
        val atual = _estado.value
        if (atual.chamadaId != id || quem == eu) return
        if (atual.fase != FaseChamada.CHAMANDO && atual.fase != FaseChamada.CONECTANDO && atual.fase != FaseChamada.ATIVA) return
        telasVistas.remove(quem)
        atualizar { it.copy(escolhendoTela = it.escolhendoTela - quem) }
        midia.desconectar(quem)
        val dados = atualizarDados(id) ?: return
        conferirSeAcabou(id, dados, eu)
    }

    private suspend fun videoAtivado(id: Long, quem: Long, eu: Long) {
        val atual = _estado.value
        if (atual.chamadaId != id || quem == eu) return
        if (atual.fase != FaseChamada.CONECTANDO && atual.fase != FaseChamada.ATIVA) return
        if (atual.tipo == TipoChamada.VIDEO) {
            // Já estou em vídeo: só reassinar quem republicou.
            tentar { midia.reassinar(quem) }
            return
        }
        val nome = atual.dados?.participantes?.firstOrNull { it.usuarioId == quem }?.nome.orEmpty()
        atualizar { it.copy(pedidoVideo = PedidoVideo(quem, nome)) }
        noTrabalho {
            delay(TEMPO_PEDIDO_VIDEO_MS)
            if (_estado.value.pedidoVideo?.usuarioId == quem) responderVideoAgora(transmitir = false)
        }
    }

    /** Telas dos outros que eu já avisei que estou vendo (uma vez por compartilhamento). */
    private val telasVistas = mutableSetOf<Long>()

    /**
     * A tela compartilhada de [alvo] apareceu aqui: avisa quem compartilha com o WS 57
     * `{acao:"vendo", alvo}` (web `edb1785`), uma vez por compartilhamento. Sem o aviso, o web
     * dele mostra este aparelho como "(conectando...)" em "Vendo sua tela:".
     */
    fun avisarTelaVista(alvo: Long) {
        escopo.launch {
            val atual = _estado.value
            val id = atual.chamadaId ?: return@launch
            if (alvo !in atual.telas || !telasVistas.add(alvo)) return@launch
            val enviado = tempoReal.enviarSinal(
                id,
                buildJsonObject {
                    put("acao", "vendo")
                    put("alvo", alvo)
                },
            )
            // Socket fechado: avisa na próxima vez que a tela aparecer.
            if (!enviado) telasVistas.remove(alvo)
        }
    }

    /** WS 57 (contrato §9.11): chat da chamada, tela compartilhada (e quem a escolhe) e ponteiro remoto. */
    private fun sinal(id: Long, quem: Long, dados: JsonObject, eu: Long) {
        if (_estado.value.chamadaId != id || quem == eu) return
        when ((dados["acao"] as? JsonPrimitive)?.contentOrNull) {
            "chat" -> {
                val conversa = (dados["conversa_id"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return
                atualizar { it.copy(conversaChatId = conversa) }
            }
            // Alguém foi chamado de novo (§9.14): relê quem está tocando, para a grade.
            "participantes" -> noTrabalho { atualizarDados(id) }
            "tela" -> {
                val ativa = (dados["ativa"] as? JsonPrimitive)?.booleanOrNull == true
                // Cada compartilhamento novo é avisado de novo (`vendo`).
                if (!ativa) telasVistas.remove(quem)
                atualizar { e ->
                    if (ativa) {
                        // Tela nova: em destaque (TODO 6.13); o aviso da tela substitui o "escolhendo".
                        e.copy(
                            telas = e.telas + quem,
                            escolhendoTela = e.escolhendoTela - quem,
                            exibicao = Exibicao(ModoExibicao.DESTAQUE, quem),
                        )
                    } else {
                        e.copy(
                            telas = e.telas - quem,
                            escolhendoTela = e.escolhendoTela - quem,
                            ponteiros = e.ponteiros.filterValues { it.alvo != quem },
                            exibicao = if (e.exibicao.destaque == quem) Exibicao() else e.exibicao,
                        )
                    }
                }
            }
            // Abriu (ou fechou sem escolher) a escolha da tela: "Fulano está escolhendo a tela..." (web `edb1785`).
            "escolhendo" -> {
                val ativa = (dados["ativa"] as? JsonPrimitive)?.booleanOrNull == true
                atualizar { e -> e.copy(escolhendoTela = if (ativa) e.escolhendoTela + quem else e.escolhendoTela - quem) }
            }
            "ponteiro" -> {
                val alvo = (dados["alvo"] as? JsonPrimitive)?.longOrNull
                val x = (dados["x"] as? JsonPrimitive)?.floatOrNull
                val y = (dados["y"] as? JsonPrimitive)?.floatOrNull
                if (alvo == null || x == null || y == null) {
                    // x/y nulos: o ponteiro saiu da tela.
                    atualizar { it.copy(ponteiros = it.ponteiros - quem) }
                    return
                }
                val nome = _estado.value.dados?.participantes?.firstOrNull { it.usuarioId == quem }?.nome?.takeIf { it.isNotBlank() }
                    ?: NOME_PADRAO_PONTEIRO
                val ponteiro = PonteiroRemoto(quem, nome, alvo, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), relogio.instant())
                atualizar { it.copy(ponteiros = it.ponteiros + (quem to ponteiro)) }
                garantirLimpezaDePonteiros()
            }
        }
    }

    private var limpezaPonteiros: Job? = null

    /** Ponteiro sem atualizar há [PONTEIRO_SOME_MS] some (como o web). */
    private fun garantirLimpezaDePonteiros() {
        if (limpezaPonteiros?.isActive == true) return
        limpezaPonteiros = noTrabalho {
            while (_estado.value.ponteiros.isNotEmpty()) {
                delay(VERIFICAR_PONTEIROS_MS)
                val limite = relogio.instant().minusMillis(PONTEIRO_SOME_MS)
                atualizar { e -> e.copy(ponteiros = e.ponteiros.filterValues { it.em.isAfter(limite) }) }
            }
        }
    }

    /** Pendentes (ao conectar ou abrir pelo push): antigas viram perdidas; as novas tocam (FC-710). */
    private suspend fun avaliarPendentes(lista: List<ChamadaPendente>) {
        val eu = eu() ?: return
        val agora = relogio.instant()
        for (pendente in lista) {
            if (pendente.id in encerradas || pendente.id == _estado.value.chamadaId) continue
            val idade = pendente.criadoEm?.let { Duration.between(it, agora) }
            if (idade != null && idade.toMillis() > IDADE_MAXIMA_PENDENTE_MS) {
                lembrarEncerrada(pendente.id)
                escopo.launch { tentar { remoto.recusar(pendente.id, naoAtendeu = true) } }
            } else {
                recebida(pendente.id, pendente.criadoPor ?: 0, eu)
            }
        }
    }

    // --- Regras internas ---

    private suspend fun desligarAgora() {
        when (_estado.value.fase) {
            FaseChamada.CHAMANDO -> terminar { remoto.cancelar(it) }
            FaseChamada.RECEBENDO -> recusarAgora(naoAtendeu = false)
            FaseChamada.CONECTANDO, FaseChamada.ATIVA -> terminar { remoto.sair(it) }
            FaseChamada.INATIVO, FaseChamada.ENCERRANDO -> Unit
        }
    }

    private fun recusarAgora(naoAtendeu: Boolean) {
        val id = _estado.value.chamadaId ?: return
        encerrar()
        escopo.launch { tentar { remoto.recusar(id, naoAtendeu) } }
    }

    private fun ficarAtiva(id: Long) {
        atualizar { it.copy(fase = FaseChamada.ATIVA, ativaDesde = relogio.instant()) }
        garantirMonitor(id)
        noTrabalho { sincronizarComRetentativas(id) }
    }

    /**
     * Com os dados novos: ainda tem com quem falar?
     * - chamando: alguém entrou → ativa (cobre um 54 perdido); ninguém mais tocando → todos recusaram;
     * - ativa: ninguém mais "Entrou" → sair (como o web no 55).
     */
    private suspend fun conferirSeAcabou(id: Long, dados: Chamada, eu: Long) {
        val atual = _estado.value
        if (atual.chamadaId != id) return
        val outros = dados.participantes.filter { it.usuarioId != eu }
        val alguemEntrou = outros.any { it.status == StatusParticipante.ENTROU }
        val alguemTocando = outros.any { it.status == StatusParticipante.PENDENTE }
        when (atual.fase) {
            FaseChamada.CHAMANDO -> when {
                alguemEntrou -> ficarAtiva(id)
                !alguemTocando -> encerrar()
            }
            FaseChamada.ATIVA -> if (!alguemEntrou) terminar { remoto.sair(it) }
            else -> Unit
        }
    }

    /** Corta a mídia, mostra "Encerrando…" enquanto avisa o servidor (até [ESPERA_ENCERRAR_MS]) e volta a INATIVO. */
    private suspend fun terminar(acao: suspend (Long) -> Unit) {
        val atual = _estado.value
        if (atual.fase == FaseChamada.INATIVO || atual.fase == FaseChamada.ENCERRANDO) return
        geracao++
        val g = geracao
        cancelarTrabalho()
        midia.encerrar()
        _estado.value = atual.copy(fase = FaseChamada.ENCERRANDO, pedidoVideo = null)
        val id = atual.chamadaId
        if (id != null) {
            lembrarEncerrada(id)
            // Fora da chamada: o aviso ao servidor vai até o fim mesmo se a espera acabar.
            val envio = escopo.async { tentar { acao(id) } }
            withTimeoutOrNull(ESPERA_ENCERRAR_MS) { envio.await() }
        }
        if (vivo(g)) encerrar()
    }

    /**
     * Volta a INATIVO. Idempotente: corta a mídia (DELETE WHIP/WHEP e `dispose`) e
     * cancela temporizadores; toque, notificações, Telecom e serviço observam o estado.
     */
    private fun encerrar(lembrar: Boolean = true) {
        val atual = _estado.value
        if (atual.fase == FaseChamada.INATIVO) return
        if (lembrar) atual.chamadaId?.let(::lembrarEncerrada)
        geracao++
        cancelarTrabalho()
        midia.encerrar()
        telasVistas.clear()
        _estado.value = EstadoChamada()
    }

    private suspend fun responderVideoAgora(transmitir: Boolean) {
        val pedido = _estado.value.pedidoVideo ?: return
        atualizar { it.copy(pedidoVideo = null) }
        ativarVideo(notificar = false, transmitir = transmitir)
        // "Apenas assistir": tela única em quem ligou o vídeo (como o web).
        if (!transmitir && vivoNaChamada(pedido.usuarioId)) atualizar { it.copy(exibicao = Exibicao(ModoExibicao.UNICA, pedido.usuarioId)) }
    }

    private fun vivoNaChamada(usuarioId: Long) =
        _estado.value.chamadaId != null && _estado.value.fase != FaseChamada.INATIVO && usuarioId > 0

    /** Áudio → vídeo. Protegido contra toque duplo: duas câmeras seriam recusadas (406). */
    private suspend fun ativarVideo(notificar: Boolean, transmitir: Boolean) {
        val atual = _estado.value
        val id = atual.chamadaId ?: return
        if (atual.fase != FaseChamada.ATIVA || atual.tipo != TipoChamada.AUDIO || ativandoVideo) return
        ativandoVideo = true
        val g = geracao
        // As etapas (FC-728) chegam da vez da mídia: passam para esta antes de mexer no estado.
        atualizar { it.copy(etapaVideo = EtapaVideo.CAMERA) }
        try {
            val comCamera = tentar {
                midia.ativarVideo(transmitir) { etapa ->
                    escopo.launch { if (vivo(g) && ativandoVideo) atualizar { it.copy(etapaVideo = etapa) } }
                }
            } ?: false
            if (!vivo(g)) return
            atualizar {
                it.copy(
                    tipo = TipoChamada.VIDEO,
                    midiaLocal = if (comCamera) MidiaLocal.AUDIO_VIDEO else it.midiaLocal,
                    cameraLigada = comCamera && transmitir,
                )
            }
            if (notificar) tentar { remoto.anunciarVideo(id) }
        } finally {
            ativandoVideo = false
            if (vivo(g)) atualizar { it.copy(etapaVideo = null) }
        }
    }

    // --- Temporizadores e sincronização ---

    /** 30 s tocando sem resposta → recusa como "não atendeu" (chamada perdida). */
    private suspend fun tocar(id: Long) {
        delay(TEMPO_TOCANDO_MS)
        val atual = _estado.value
        if (atual.fase == FaseChamada.RECEBENDO && atual.chamadaId == id) emSeguida { recusarAgora(naoAtendeu = true) }
    }

    /** Ninguém atendeu em [TEMPO_SEM_RESPOSTA_MS] → cancela. */
    private suspend fun semResposta(id: Long) {
        delay(TEMPO_SEM_RESPOSTA_MS)
        val atual = _estado.value
        if (atual.fase == FaseChamada.CHAMANDO && atual.chamadaId == id) emSeguida { desligarAgora() }
    }

    /** A cada [INTERVALO_MONITOR_MS], chamando ou ativa: dados novos, reconectar quem caiu e conferir se acabou. */
    private fun garantirMonitor(id: Long) {
        if (monitor?.isActive == true) return
        monitor = noTrabalho {
            while (true) {
                delay(INTERVALO_MONITOR_MS)
                val atual = _estado.value
                if (atual.chamadaId != id) return@noTrabalho
                val dados = when (atual.fase) {
                    FaseChamada.CHAMANDO -> atualizarDados(id)
                    FaseChamada.ATIVA -> sincronizar(id)
                    else -> null
                } ?: continue
                val eu = eu() ?: continue
                emSeguida { conferirSeAcabou(id, dados, eu) }
            }
        }
    }

    private suspend fun sincronizarComRetentativas(id: Long) {
        repeat(TENTATIVAS_SINCRONIZAR) { tentativa ->
            sincronizar(id)
            if (tentativa < TENTATIVAS_SINCRONIZAR - 1) delay((tentativa + 1) * 1_000L)
        }
    }

    /** Assina quem está "Entrou" e solta quem saiu. Devolve os dados usados. */
    private suspend fun sincronizar(id: Long): Chamada? {
        if (sincronizando) return null
        sincronizando = true
        try {
            val dados = atualizarDados(id) ?: return null
            val eu = eu() ?: return null
            val atual = _estado.value
            if (atual.fase != FaseChamada.CONECTANDO && atual.fase != FaseChamada.ATIVA) return dados
            val ativos = dados.participantes
                .filter { it.usuarioId != eu && it.status == StatusParticipante.ENTROU }
                .mapTo(mutableSetOf()) { it.usuarioId }
            tentar { midia.sincronizar(id, ativos, atual.tipo == TipoChamada.VIDEO) }
            return dados
        } finally {
            sincronizando = false
        }
    }

    private suspend fun atualizarDados(id: Long): Chamada? {
        val g = geracao
        val dados = tentar { remoto.dados(id) } ?: return null
        if (!vivo(g)) return null
        atualizar { it.copy(dados = dados, conversaChatId = dados.conversaChatId ?: it.conversaChatId) }
        return dados
    }

    // --- Utilidades ---

    private fun eu(): Long? = sessao.sessao.value?.usuarioId

    private fun vivo(g: Long) = g == geracao

    private fun atualizar(mudanca: (EstadoChamada) -> EstadoChamada) {
        _estado.value = mudanca(_estado.value)
    }

    private fun abrir(novo: EstadoChamada): Long {
        geracao++
        cancelarTrabalho()
        trabalho = SupervisorJob(escopo.coroutineContext[Job])
        _estado.value = novo
        return geracao
    }

    private fun noTrabalho(bloco: suspend CoroutineScope.() -> Unit): Job? {
        val pai = trabalho ?: return null
        return escopo.launch(pai, block = bloco)
    }

    /** Ações que encerram rodam fora do [trabalho] (encerrar cancela o [trabalho]). */
    private fun emSeguida(bloco: suspend () -> Unit) {
        escopo.launch { bloco() }
    }

    private fun cancelarTrabalho() {
        trabalho?.cancel()
        trabalho = null
        monitor = null
        sincronizando = false
        ativandoVideo = false
    }

    private fun lembrarEncerrada(id: Long) = lembrar(encerradas, id)

    private fun lembrar(lista: ArrayDeque<Long>, id: Long) {
        if (id in lista) return
        lista.addLast(id)
        while (lista.size > MAX_ENCERRADAS) lista.removeFirst()
    }

    private suspend inline fun <T> tentar(bloco: () -> T): T? = try {
        bloco()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w("Falhou: %s", e.resumoDoErro())
        null
    }

    companion object {
        const val TAG = "Chamada"

        /** Onde o "Chamar de novo" vale: liguei, atendi ou estou na chamada. */
        private val FASES_CHAMAR_DE_NOVO = setOf(FaseChamada.CHAMANDO, FaseChamada.CONECTANDO, FaseChamada.ATIVA)

        /** Como o web: 30 s tocando → "não atendeu". */
        const val TEMPO_TOCANDO_MS = 30_000L

        /** Ao atender, quanto o `entrar` espera a publicação (FC-729); a coleta do ICE leva até 5 s no pior caso. */
        const val ESPERA_PUBLICAR_ANTES_MS = 2_000L

        /** Sem ninguém atender (o outro sem app nem push): cancela. */
        const val TEMPO_SEM_RESPOSTA_MS = 45_000L

        /** Pendente criada há mais que isso tocou com o app fechado: vira perdida. */
        const val IDADE_MAXIMA_PENDENTE_MS = 25_000L

        const val INTERVALO_MONITOR_MS = 4_000L
        const val TEMPO_PEDIDO_VIDEO_MS = 15_000L
        const val ESPERA_ENCERRAR_MS = 3_000L
        const val PONTEIRO_SOME_MS = 5_000L
        private const val VERIFICAR_PONTEIROS_MS = 1_000L

        /** O web usa "Participante" quando não acha o nome. */
        private const val NOME_PADRAO_PONTEIRO = "Participante"
        private const val TENTATIVAS_SINCRONIZAR = 3
        private const val MAX_ENCERRADAS = 32
    }
}
