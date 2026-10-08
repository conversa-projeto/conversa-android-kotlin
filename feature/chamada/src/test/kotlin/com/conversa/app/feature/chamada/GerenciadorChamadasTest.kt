package com.conversa.app.feature.chamada

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.chamadas.ChamadasRemotas
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.model.Chamada
import com.conversa.app.core.model.ChamadaPendente
import com.conversa.app.core.model.ParticipanteChamada
import com.conversa.app.core.model.ServidoresIce
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.StatusChamada
import com.conversa.app.core.model.StatusParticipante
import com.conversa.app.core.model.StatusParticipante.ENTROU
import com.conversa.app.core.model.StatusParticipante.PENDENTE
import com.conversa.app.core.model.StatusParticipante.RECUSOU
import com.conversa.app.core.model.StatusParticipante.SAIU
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.RealtimeClient
import com.conversa.app.core.webrtc.MidiaChamada
import com.conversa.app.core.webrtc.MidiaLocal
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * Máquina de estados da chamada (TODO 6.2): todas as transições, com eventos
 * duplicados e fora de ordem. O monitor de 4 s roda para sempre durante a chamada:
 * por isso só `runCurrent`/`advanceTimeBy`, nunca `advanceUntilIdle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GerenciadorChamadasTest {
    private val eventos = MutableSharedFlow<EventoSocket>(extraBufferCapacity = 32)
    private val pendentes = MutableSharedFlow<List<ChamadaPendente>>(extraBufferCapacity = 8)
    private val sessaoAtual = MutableStateFlow<Sessao?>(Sessao("t", EU, "Ana"))
    private val chamadaAtiva = MutableStateFlow(false)
    private val remoto = RemotoFalso()
    private val midia = MidiaFalsa()

    private fun TestScope.criar(): GerenciadorChamadas {
        val tempoReal = mockk<RealtimeClient> { every { eventos } returns this@GerenciadorChamadasTest.eventos }
        val sincronizacao = mockk<SyncManager> { every { chamadasPendentes } returns pendentes }
        val conexao = mockk<ConexaoTempoReal> { every { chamadaAtiva } returns this@GerenciadorChamadasTest.chamadaAtiva }
        val sessao = mockk<SessaoRepositorio> { every { sessao } returns sessaoAtual }
        val relogio = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId?): Clock = this

            override fun instant(): Instant = INICIO.plusMillis(testScheduler.currentTime)
        }
        return GerenciadorChamadas(
            remoto,
            midia,
            tempoReal,
            sincronizacao,
            conexao,
            sessao,
            relogio,
            backgroundScope,
            StandardTestDispatcher(testScheduler),
        ).also {
            it.iniciar()
            runCurrent()
        }
    }

    private fun TestScope.evento(evento: EventoSocket) {
        eventos.tryEmit(evento)
        runCurrent()
    }

    private fun TestScope.ligando(g: GerenciadorChamadas, outros: List<Long> = listOf(OUTRO)) {
        g.ligar(TipoChamada.AUDIO, outros, 42)
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CHAMANDO)
    }

    /** O B ligou para mim (51) e está tocando. */
    private fun TestScope.tocando(
        g: GerenciadorChamadas,
        tipo: TipoChamada = TipoChamada.AUDIO,
        outros: List<ParticipanteChamada> = listOf(p(OUTRO, ENTROU)),
    ) {
        remoto.dados[RECEBIDA] = chamada(RECEBIDA, tipo, *outros.toTypedArray(), p(EU, PENDENTE), criadoPor = OUTRO)
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))
        assertThat(g.estado.value.tocando).isTrue()
    }

    private fun TestScope.ativaRecebida(g: GerenciadorChamadas, outros: List<ParticipanteChamada> = listOf(p(OUTRO, ENTROU))) {
        tocando(g, TipoChamada.AUDIO, outros)
        g.atender()
        runCurrent()
        remoto.dados[RECEBIDA] = remoto.dados.getValue(RECEBIDA).comStatus(EU, ENTROU)
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
    }

    // --- Ligar ---

    @Test
    fun `ligar abre a midia, cria a chamada comigo na lista e publica`() = runTest {
        val g = criar()
        ligando(g)

        assertThat(remoto.chamadas).containsExactly("iniciar AUDIO [7, 8] 42").inOrder()
        assertThat(midia.acoes).containsExactly("abrir video=false", "publicar 10").inOrder()
        val estado = g.estado.value
        assertThat(estado.chamadaId).isEqualTo(10L)
        assertThat(estado.remetenteId).isEqualTo(EU)
        assertThat(estado.midiaLocal).isEqualTo(MidiaLocal.AUDIO)
        assertThat(chamadaAtiva.value).isTrue()
    }

    @Test
    fun `ligar com chamada em andamento avisa e nao chama o servidor`() = runTest {
        val g = criar()
        ligando(g)
        val avisos = mutableListOf<AvisoChamada>()
        backgroundScope.launch { g.avisos.collect { avisos += it } }
        runCurrent()

        g.ligar(TipoChamada.VIDEO, listOf(TERCEIRO), null)
        runCurrent()

        assertThat(avisos).containsExactly(AvisoChamada.JaEmChamada)
        assertThat(remoto.chamadas.count { it.startsWith("iniciar") }).isEqualTo(1)
    }

    @Test
    fun `ligar sem microfone volta a inativo e avisa`() = runTest {
        midia.local = MidiaLocal.NENHUMA
        val g = criar()
        val avisos = mutableListOf<AvisoChamada>()
        backgroundScope.launch { g.avisos.collect { avisos += it } }
        runCurrent()

        g.ligar(TipoChamada.AUDIO, listOf(OUTRO), null)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(avisos).containsExactly(AvisoChamada.MicrofoneIndisponivel)
        assertThat(remoto.chamadas).isEmpty()
    }

    @Test
    fun `ligar de video sem camera segue so com audio`() = runTest {
        midia.local = MidiaLocal.AUDIO
        val g = criar()

        g.ligar(TipoChamada.VIDEO, listOf(OUTRO), null)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CHAMANDO)
        assertThat(g.estado.value.midiaLocal).isEqualTo(MidiaLocal.AUDIO)
        assertThat(g.estado.value.cameraLigada).isFalse()
    }

    @Test
    fun `servidor recusa iniciar - inativo, midia fechada e aviso com o motivo`() = runTest {
        remoto.falharIniciar = ErroApi.Servidor(500, "Chamada não encontrada!")
        val g = criar()
        val avisos = mutableListOf<AvisoChamada>()
        backgroundScope.launch { g.avisos.collect { avisos += it } }
        runCurrent()

        g.ligar(TipoChamada.AUDIO, listOf(OUTRO), null)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(midia.acoes).contains("encerrar")
        assertThat(avisos).containsExactly(AvisoChamada.Falhou("Chamada não encontrada!"))
        assertThat(chamadaAtiva.value).isFalse()
    }

    @Test
    fun `falha ao publicar nao derruba a chamada`() = runTest {
        midia.falharPublicar = true
        val g = criar()
        ligando(g)
        assertThat(g.estado.value.chamadaId).isEqualTo(10L)
    }

    @Test
    fun `chamando - 54 de outro fica ativa com cronometro e assina quem entrou`() = runTest {
        val g = criar()
        ligando(g)
        advanceTimeBy(1_500)
        remoto.dados[10] = remoto.dados.getValue(10).comStatus(OUTRO, ENTROU)

        evento(EventoSocket.UsuarioEntrou(10, OUTRO))

        val estado = g.estado.value
        assertThat(estado.fase).isEqualTo(FaseChamada.ATIVA)
        assertThat(estado.ativaDesde).isEqualTo(INICIO.plusMillis(1_500))
        assertThat(midia.acoes).contains("sincronizar [8]")
    }

    @Test
    fun `chamando - desligar cancela no servidor e passa por encerrando`() = runTest {
        val g = criar()
        ligando(g)
        val portao = remoto.portao("cancelar")

        g.desligar()
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ENCERRANDO)
        assertThat(midia.acoes.last()).isEqualTo("encerrar")
        portao.complete(Unit)
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).contains("cancelar 10")
        assertThat(chamadaAtiva.value).isFalse()
    }

    @Test
    fun `desligar enquanto o servidor cria a chamada cancela a que nasceu`() = runTest {
        val g = criar()
        val portao = remoto.portao("iniciar")
        g.ligar(TipoChamada.AUDIO, listOf(OUTRO), null)
        runCurrent()

        g.desligar()
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)

        portao.complete(Unit)
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).contains("cancelar 10")
        assertThat(midia.acoes).doesNotContain("publicar 10")
    }

    @Test
    fun `chamando - ninguem atende em 45 s cancela`() = runTest {
        val g = criar()
        ligando(g)

        advanceTimeBy(GerenciadorChamadas.TEMPO_SEM_RESPOSTA_MS + 1)
        runCurrent()

        assertThat(remoto.chamadas).contains("cancelar 10")
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `chamando 1 para 1 - 53 do outro encerra sem chamar acao no servidor`() = runTest {
        val g = criar()
        ligando(g)
        remoto.dados[10] = remoto.dados.getValue(10).comStatus(OUTRO, RECUSOU)

        evento(EventoSocket.UsuarioRecusou(10, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas.filterNot { it.startsWith("iniciar") || it.startsWith("dados") }).isEmpty()
    }

    @Test
    fun `chamando em grupo - um recusa e outro ainda toca - continua chamando`() = runTest {
        val g = criar()
        ligando(g, listOf(OUTRO, TERCEIRO))
        remoto.dados[10] = remoto.dados.getValue(10).comStatus(OUTRO, RECUSOU)

        evento(EventoSocket.UsuarioRecusou(10, OUTRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CHAMANDO)

        remoto.dados[10] = remoto.dados.getValue(10).comStatus(TERCEIRO, RECUSOU)
        evento(EventoSocket.UsuarioRecusou(10, TERCEIRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `chamando - 54 perdido - o monitor ve que entrou e fica ativa`() = runTest {
        val g = criar()
        ligando(g)
        remoto.dados[10] = remoto.dados.getValue(10).comStatus(OUTRO, ENTROU)

        advanceTimeBy(GerenciadorChamadas.INTERVALO_MONITOR_MS + 1)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
    }

    @Test
    fun `chamando - 52 encerra`() = runTest {
        val g = criar()
        ligando(g)

        evento(EventoSocket.ChamadaFinalizada(10, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(midia.acoes.last()).isEqualTo("encerrar")
    }

    // --- Recebida ---

    @Test
    fun `51 busca os dados e toca com o tipo da chamada`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)

        val estado = g.estado.value
        assertThat(estado.fase).isEqualTo(FaseChamada.RECEBENDO)
        assertThat(estado.tipo).isEqualTo(TipoChamada.VIDEO)
        assertThat(estado.remetenteId).isEqualTo(OUTRO)
        assertThat(chamadaAtiva.value).isTrue()
    }

    @Test
    fun `51 duplicado busca os dados uma vez so`() = runTest {
        val g = criar()
        tocando(g)
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))

        assertThat(remoto.chamadas).containsExactly("dados $RECEBIDA")
        assertThat(g.estado.value.tocando).isTrue()
    }

    @Test
    fun `51 durante outra chamada recusa como nao atendeu sem tocar`() = runTest {
        val g = criar()
        ligando(g)

        evento(EventoSocket.ChamadaRecebida(RECEBIDA, TERCEIRO))

        assertThat(remoto.chamadas).contains("recusar $RECEBIDA nao_atendeu")
        assertThat(g.estado.value.chamadaId).isEqualTo(10L)
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CHAMANDO)
    }

    @Test
    fun `51 enquanto outra toca recusa a segunda como nao atendeu`() = runTest {
        val g = criar()
        tocando(g)

        evento(EventoSocket.ChamadaRecebida(99, TERCEIRO))

        assertThat(remoto.chamadas).contains("recusar 99 nao_atendeu")
        assertThat(g.estado.value.chamadaId).isEqualTo(RECEBIDA)
    }

    @Test
    fun `51 da minha propria chamada e ignorado`() = runTest {
        val g = criar()

        evento(EventoSocket.ChamadaRecebida(RECEBIDA, EU))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).isEmpty()
    }

    @Test
    fun `51 que chega depois do 52 da mesma chamada nao toca`() = runTest {
        val g = criar()

        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).isEmpty()
    }

    @Test
    fun `51 de chamada ja encerrada ou ja atendida em outro aparelho nao toca`() = runTest {
        val g = criar()
        remoto.dados[RECEBIDA] = chamada(RECEBIDA, TipoChamada.AUDIO, p(OUTRO, ENTROU), p(EU, PENDENTE), status = StatusChamada.CANCELADA)
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)

        remoto.dados[30] = chamada(30, TipoChamada.AUDIO, p(OUTRO, ENTROU), p(EU, ENTROU), status = StatusChamada.EM_ANDAMENTO)
        evento(EventoSocket.ChamadaRecebida(30, OUTRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `51 sem conseguir os dados nao toca e a pendente pode tentar de novo`() = runTest {
        val g = criar()
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)

        remoto.dados[RECEBIDA] = chamada(RECEBIDA, TipoChamada.AUDIO, p(OUTRO, ENTROU), p(EU, PENDENTE))
        pendentes.tryEmit(listOf(pendente(RECEBIDA, INICIO)))
        runCurrent()
        assertThat(g.estado.value.tocando).isTrue()
    }

    @Test
    fun `30 s tocando recusa como nao atendeu`() = runTest {
        val g = criar()
        tocando(g)

        advanceTimeBy(GerenciadorChamadas.TEMPO_TOCANDO_MS + 1)
        runCurrent()

        assertThat(remoto.chamadas).contains("recusar $RECEBIDA nao_atendeu")
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `recusar para de tocar e manda um unico recusar`() = runTest {
        val g = criar()
        tocando(g)

        g.recusar()
        g.recusar()
        g.desligar()
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas.filter { it.startsWith("recusar") }).containsExactly("recusar $RECEBIDA")
        // O 53 do próprio recusar, que volta pelo WebSocket, não muda nada.
        evento(EventoSocket.UsuarioRecusou(RECEBIDA, EU))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        advanceTimeBy(GerenciadorChamadas.TEMPO_TOCANDO_MS + 1)
        runCurrent()
        assertThat(remoto.chamadas.filter { it.startsWith("recusar") }).hasSize(1)
    }

    @Test
    fun `recusou em outro aparelho - 53 com o meu id para de tocar sem chamar o servidor`() = runTest {
        val g = criar()
        tocando(g)

        evento(EventoSocket.UsuarioRecusou(RECEBIDA, EU))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).containsExactly("dados $RECEBIDA")
    }

    @Test
    fun `atendeu em outro aparelho - 54 com o meu id para de tocar`() = runTest {
        val g = criar()
        tocando(g)

        evento(EventoSocket.UsuarioEntrou(RECEBIDA, EU))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).containsExactly("dados $RECEBIDA")
    }

    @Test
    fun `pendente que chega depois de recusar nao toca de novo`() = runTest {
        val g = criar()
        tocando(g)
        g.recusar()
        runCurrent()

        pendentes.tryEmit(listOf(pendente(RECEBIDA, INICIO)))
        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `52 enquanto toca para de tocar`() = runTest {
        val g = criar()
        tocando(g)

        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `atender - conectando, entrar, publicar, ativa e assinar quem ligou`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)
        val portao = remoto.portao("entrar")

        g.atender()
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CONECTANDO)
        // O meu próprio 54 chega enquanto conecto: não é "atendeu em outro aparelho".
        evento(EventoSocket.UsuarioEntrou(RECEBIDA, EU))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.CONECTANDO)

        portao.complete(Unit)
        runCurrent()

        val estado = g.estado.value
        assertThat(estado.fase).isEqualTo(FaseChamada.ATIVA)
        assertThat(estado.midiaLocal).isEqualTo(MidiaLocal.AUDIO_VIDEO)
        assertThat(estado.cameraLigada).isTrue()
        assertThat(remoto.chamadas).contains("entrar $RECEBIDA")
        assertThat(midia.acoes).containsAtLeast("abrir video=true", "publicar $RECEBIDA", "sincronizar [8]").inOrder()
        // O toque não recusa mais depois de atender.
        advanceTimeBy(GerenciadorChamadas.TEMPO_TOCANDO_MS + 1)
        runCurrent()
        assertThat(remoto.chamadas.filter { it.startsWith("recusar") }).isEmpty()
    }

    @Test
    fun `atender so assistindo entra com a camera desligada`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)

        g.atender(soAssistir = true)
        runCurrent()

        assertThat(midia.acoes).contains("camera false")
        assertThat(g.estado.value.cameraLigada).isFalse()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
    }

    @Test
    fun `atender sem microfone nem camera entra so recebendo e nao publica`() = runTest {
        midia.local = MidiaLocal.NENHUMA
        val g = criar()
        tocando(g)

        g.atender()
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
        assertThat(g.estado.value.microfoneLigado).isFalse()
        assertThat(midia.acoes.none { it.startsWith("publicar") }).isTrue()
    }

    @Test
    fun `52 durante o atender encerra e nao fica ativa`() = runTest {
        val g = criar()
        tocando(g)
        val portao = remoto.portao("entrar")
        g.atender()
        runCurrent()

        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))
        portao.complete(Unit)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(midia.acoes.none { it.startsWith("publicar") }).isTrue()
    }

    @Test
    fun `entrar recusado pelo servidor volta a inativo e avisa`() = runTest {
        val g = criar()
        tocando(g)
        remoto.falharEntrar = ErroApi.Servidor(404, "Chamada não encontrada!")
        val avisos = mutableListOf<AvisoChamada>()
        backgroundScope.launch { g.avisos.collect { avisos += it } }
        runCurrent()

        g.atender()
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(avisos).containsExactly(AvisoChamada.Falhou("Chamada não encontrada!"))
    }

    @Test
    fun `atender so vale enquanto toca`() = runTest {
        val g = criar()
        g.atender()
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).isEmpty()
    }

    // --- Ativa ---

    @Test
    fun `55 do unico outro - confere os dados e sai (o servidor nao manda 52)`() = runTest {
        val g = criar()
        ativaRecebida(g)
        remoto.dados[RECEBIDA] = remoto.dados.getValue(RECEBIDA).comStatus(OUTRO, SAIU)

        evento(EventoSocket.UsuarioSaiu(RECEBIDA, OUTRO))
        runCurrent()

        assertThat(midia.acoes).contains("desconectar $OUTRO")
        assertThat(remoto.chamadas).contains("sair $RECEBIDA")
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `55 em grupo com alguem ainda na chamada so solta quem saiu`() = runTest {
        val g = criar()
        ativaRecebida(g, listOf(p(OUTRO, ENTROU), p(TERCEIRO, ENTROU)))
        remoto.dados[RECEBIDA] = remoto.dados.getValue(RECEBIDA).comStatus(TERCEIRO, SAIU)

        evento(EventoSocket.UsuarioSaiu(RECEBIDA, TERCEIRO))

        assertThat(midia.acoes).contains("desconectar $TERCEIRO")
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
        assertThat(remoto.chamadas).doesNotContain("sair $RECEBIDA")
    }

    @Test
    fun `desligar na chamada corta a midia antes e manda um unico sair`() = runTest {
        val g = criar()
        ativaRecebida(g)
        val portao = remoto.portao("sair")

        g.desligar()
        g.desligar()
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ENCERRANDO)
        assertThat(midia.acoes.last()).isEqualTo("encerrar")

        portao.complete(Unit)
        runCurrent()
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas.filter { it.startsWith("sair") }).containsExactly("sair $RECEBIDA")
    }

    @Test
    fun `servidor lento ao sair - encerrando dura no maximo 3 s`() = runTest {
        val g = criar()
        ativaRecebida(g)
        remoto.portao("sair")

        g.desligar()
        runCurrent()
        advanceTimeBy(GerenciadorChamadas.ESPERA_ENCERRAR_MS + 1)
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `52 na chamada ativa encerra`() = runTest {
        val g = criar()
        ativaRecebida(g)

        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(chamadaAtiva.value).isFalse()
        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `monitor a cada 4 s sincroniza e sai se o 55 se perdeu`() = runTest {
        val g = criar()
        ativaRecebida(g)
        advanceTimeBy(10_000)
        runCurrent()
        val sincronizacoes = midia.acoes.count { it.startsWith("sincronizar") }

        advanceTimeBy(GerenciadorChamadas.INTERVALO_MONITOR_MS)
        runCurrent()
        assertThat(midia.acoes.count { it.startsWith("sincronizar") }).isEqualTo(sincronizacoes + 1)

        remoto.dados[RECEBIDA] = remoto.dados.getValue(RECEBIDA).comStatus(OUTRO, SAIU)
        advanceTimeBy(GerenciadorChamadas.INTERVALO_MONITOR_MS)
        runCurrent()
        assertThat(remoto.chamadas).contains("sair $RECEBIDA")
        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
    }

    @Test
    fun `51 da chamada em que estou (alguem adicionado) so sincroniza`() = runTest {
        val g = criar()
        ativaRecebida(g)
        advanceTimeBy(3_500)
        remoto.dados[RECEBIDA] = remoto.dados.getValue(RECEBIDA).let { it.copy(participantes = it.participantes + p(TERCEIRO, ENTROU)) }

        evento(EventoSocket.ChamadaRecebida(RECEBIDA, OUTRO))

        assertThat(remoto.chamadas.filter { it.startsWith("recusar") }).isEmpty()
        assertThat(midia.acoes.last()).isEqualTo("sincronizar [8, 9]")
    }

    @Test
    fun `56 em chamada de audio pergunta e em 15 s fica so assistindo`() = runTest {
        val g = criar()
        ativaRecebida(g)

        evento(EventoSocket.VideoAtivado(RECEBIDA, OUTRO))
        assertThat(g.estado.value.pedidoVideo).isEqualTo(PedidoVideo(OUTRO, "Usuário 8"))

        advanceTimeBy(GerenciadorChamadas.TEMPO_PEDIDO_VIDEO_MS + 1)
        runCurrent()

        val estado = g.estado.value
        assertThat(estado.pedidoVideo).isNull()
        assertThat(estado.tipo).isEqualTo(TipoChamada.VIDEO)
        assertThat(estado.cameraLigada).isFalse()
        assertThat(midia.acoes).contains("ativarVideo false")
        assertThat(remoto.chamadas.filter { it.startsWith("video") }).isEmpty()
    }

    @Test
    fun `56 respondido com transmitir tambem liga a camera sem reanunciar`() = runTest {
        val g = criar()
        ativaRecebida(g)
        evento(EventoSocket.VideoAtivado(RECEBIDA, OUTRO))

        g.responderVideo(transmitir = true)
        runCurrent()

        assertThat(g.estado.value.cameraLigada).isTrue()
        assertThat(midia.acoes).contains("ativarVideo true")
        assertThat(remoto.chamadas.filter { it.startsWith("video") }).isEmpty()
        // O temporizador de 15 s não age de novo.
        advanceTimeBy(GerenciadorChamadas.TEMPO_PEDIDO_VIDEO_MS + 1)
        runCurrent()
        assertThat(midia.acoes.count { it.startsWith("ativarVideo") }).isEqualTo(1)
    }

    @Test
    fun `56 em chamada de video so reassina quem republicou`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)
        g.atender()
        runCurrent()

        evento(EventoSocket.VideoAtivado(RECEBIDA, OUTRO))

        assertThat(midia.acoes).contains("reassinar $OUTRO")
        assertThat(g.estado.value.pedidoVideo).isNull()
    }

    @Test
    fun `ligar o video anuncia uma vez mesmo com toque duplo`() = runTest {
        val g = criar()
        ativaRecebida(g)
        midia.portaoVideo = CompletableDeferred()

        g.ligarVideo()
        g.ligarVideo()
        runCurrent()
        midia.portaoVideo?.complete(Unit)
        runCurrent()

        assertThat(midia.acoes.count { it.startsWith("ativarVideo") }).isEqualTo(1)
        assertThat(remoto.chamadas.filter { it.startsWith("video") }).containsExactly("video $RECEBIDA")
        assertThat(g.estado.value.tipo).isEqualTo(TipoChamada.VIDEO)
    }

    @Test
    fun `57 chat guarda a conversa do chat da chamada`() = runTest {
        val g = criar()
        ativaRecebida(g)

        evento(
            EventoSocket.SinalChamada(
                RECEBIDA,
                OUTRO,
                buildJsonObject {
                    put("acao", "chat")
                    put("conversa_id", 77)
                },
            ),
        )

        assertThat(g.estado.value.conversaChatId).isEqualTo(77L)
    }

    @Test
    fun `microfone liga e desliga`() = runTest {
        val g = criar()
        ativaRecebida(g)

        g.alternarMicrofone()
        runCurrent()
        assertThat(g.estado.value.microfoneLigado).isFalse()
        g.alternarMicrofone()
        runCurrent()

        assertThat(g.estado.value.microfoneLigado).isTrue()
        assertThat(midia.acoes).containsAtLeast("microfone false", "microfone true").inOrder()
    }

    @Test
    fun `eventos de outra chamada nao mexem na atual`() = runTest {
        val g = criar()
        ativaRecebida(g)
        val antes = remoto.chamadas.size

        evento(EventoSocket.UsuarioSaiu(99, OUTRO))
        evento(EventoSocket.UsuarioRecusou(99, OUTRO))
        evento(EventoSocket.UsuarioEntrou(99, OUTRO))
        evento(EventoSocket.ChamadaFinalizada(99, OUTRO))
        evento(EventoSocket.VideoAtivado(99, OUTRO))
        evento(
            EventoSocket.SinalChamada(
                99,
                OUTRO,
                buildJsonObject {
                    put("acao", JsonPrimitive("chat"))
                    put("conversa_id", 5)
                },
            ),
        )

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.ATIVA)
        assertThat(g.estado.value.conversaChatId).isNull()
        assertThat(remoto.chamadas).hasSize(antes)
    }

    // --- Pendentes e sessão ---

    @Test
    fun `pendente recente toca e repetida nao duplica`() = runTest {
        val g = criar()
        remoto.dados[RECEBIDA] = chamada(RECEBIDA, TipoChamada.AUDIO, p(OUTRO, ENTROU), p(EU, PENDENTE))
        advanceTimeBy(10_000)

        pendentes.tryEmit(listOf(pendente(RECEBIDA, INICIO)))
        runCurrent()
        pendentes.tryEmit(listOf(pendente(RECEBIDA, INICIO)))
        runCurrent()

        assertThat(g.estado.value.tocando).isTrue()
        assertThat(remoto.chamadas).containsExactly("dados $RECEBIDA")
    }

    @Test
    fun `pendente com mais de 25 s vira perdida sem tocar`() = runTest {
        val g = criar()
        advanceTimeBy(30_000)

        pendentes.tryEmit(listOf(pendente(RECEBIDA, INICIO)))
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(remoto.chamadas).containsExactly("recusar $RECEBIDA nao_atendeu")
    }

    @Test
    fun `logout encerra a chamada e fecha a midia`() = runTest {
        val g = criar()
        ativaRecebida(g)

        sessaoAtual.value = null
        runCurrent()

        assertThat(g.estado.value.fase).isEqualTo(FaseChamada.INATIVO)
        assertThat(midia.acoes.last()).isEqualTo("encerrar")
        assertThat(chamadaAtiva.value).isFalse()
    }

    // --- Adicionar à chamada (6.13) ---

    @Test
    fun `adicionar manda um PUT por pessoa e atualiza os dados`() = runTest {
        val g = criar()
        ativaRecebida(g)

        g.adicionar(listOf(TERCEIRO, 40L, TERCEIRO))
        runCurrent()

        assertThat(remoto.chamadas.filter { it.startsWith("adicionar") })
            .containsExactly("adicionar $RECEBIDA $TERCEIRO", "adicionar $RECEBIDA 40").inOrder()
        assertThat(remoto.chamadas.last()).isEqualTo("dados $RECEBIDA")
        assertThat(g.estado.value.dados?.participantes?.map { it.usuarioId }).containsAtLeast(TERCEIRO, 40L)
    }

    @Test
    fun `adicionar so vale com a chamada ativa`() = runTest {
        val g = criar()
        tocando(g)

        g.adicionar(listOf(TERCEIRO))
        runCurrent()

        assertThat(remoto.chamadas.filter { it.startsWith("adicionar") }).isEmpty()
    }

    // --- Chat da chamada (6.13) ---

    @Test
    fun `garantir o chat cria uma vez e depois usa a conversa guardada`() = runTest {
        val g = criar()
        ativaRecebida(g)
        val ids = mutableListOf<Long?>()

        backgroundScope.launch { ids += g.garantirChat() }
        runCurrent()
        backgroundScope.launch { ids += g.garantirChat() }
        runCurrent()

        assertThat(ids).containsExactly(77L, 77L)
        assertThat(remoto.chamadas.filter { it.startsWith("chat") }).containsExactly("chat $RECEBIDA")
        assertThat(g.estado.value.conversaChatId).isEqualTo(77L)
    }

    @Test
    fun `garantir o chat sem chamada devolve nulo`() = runTest {
        val g = criar()
        val ids = mutableListOf<Long?>()

        backgroundScope.launch { ids += g.garantirChat() }
        runCurrent()

        assertThat(ids).containsExactly(null)
        assertThat(remoto.chamadas).isEmpty()
    }

    // --- Modo de exibição (6.11) ---

    @Test
    fun `apenas assistir abre em tela unica em quem ligou o video`() = runTest {
        val g = criar()
        ativaRecebida(g)
        evento(EventoSocket.VideoAtivado(RECEBIDA, OUTRO))

        g.responderVideo(transmitir = false)
        runCurrent()

        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao(ModoExibicao.UNICA, OUTRO))
    }

    @Test
    fun `transmitir tambem nao muda o modo de exibicao`() = runTest {
        val g = criar()
        ativaRecebida(g)
        evento(EventoSocket.VideoAtivado(RECEBIDA, OUTRO))

        g.responderVideo(transmitir = true)
        runCurrent()

        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao())
    }

    @Test
    fun `atender so assistindo uma chamada de video abre em tela unica em quem ligou`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)

        g.atender(soAssistir = true)
        runCurrent()

        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao(ModoExibicao.UNICA, OUTRO))
    }

    @Test
    fun `atender com camera fica na grade`() = runTest {
        val g = criar()
        tocando(g, TipoChamada.VIDEO)

        g.atender()
        runCurrent()

        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao())
    }

    @Test
    fun `exibir destaque, trocar o destacado, voltar a grade e a proxima chamada comeca na grade`() = runTest {
        val g = criar()
        ativaRecebida(g, listOf(p(OUTRO, ENTROU), p(TERCEIRO, ENTROU)))

        g.exibir(ModoExibicao.DESTAQUE, OUTRO)
        runCurrent()
        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao(ModoExibicao.DESTAQUE, OUTRO))
        // Trocar só o modo mantém quem estava em destaque.
        g.exibir(ModoExibicao.UNICA)
        runCurrent()
        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao(ModoExibicao.UNICA, OUTRO))
        g.exibir(ModoExibicao.GRADE, TERCEIRO)
        runCurrent()
        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao())

        g.exibir(ModoExibicao.DESTAQUE, TERCEIRO)
        evento(EventoSocket.ChamadaFinalizada(RECEBIDA, OUTRO))
        assertThat(g.estado.value.exibicao).isEqualTo(Exibicao())
    }

    private class RemotoFalso : ChamadasRemotas {
        val chamadas = mutableListOf<String>()
        val dados = mutableMapOf<Long, Chamada>()
        var falharIniciar: Exception? = null
        var falharEntrar: Exception? = null
        private val portoes = mutableMapOf<String, CompletableDeferred<Unit>>()

        fun portao(acao: String) = CompletableDeferred<Unit>().also { portoes[acao] = it }

        private suspend fun passar(acao: String) {
            portoes[acao]?.await()
        }

        override suspend fun iniciar(tipo: TipoChamada, usuarios: List<Long>, conversaId: Long?): Chamada {
            chamadas += "iniciar $tipo ${usuarios.sorted()} $conversaId"
            passar("iniciar")
            falharIniciar?.let { throw it }
            val nova = chamada(10, tipo, *usuarios.map { p(it, if (it == EU) ENTROU else PENDENTE) }.toTypedArray(), criadoPor = EU)
            dados[nova.id] = nova
            return nova
        }

        override suspend fun dados(chamadaId: Long): Chamada {
            chamadas += "dados $chamadaId"
            passar("dados")
            return dados[chamadaId] ?: throw IOException("sem rede")
        }

        override suspend fun entrar(chamadaId: Long) {
            chamadas += "entrar $chamadaId"
            passar("entrar")
            falharEntrar?.let { throw it }
        }

        override suspend fun recusar(chamadaId: Long, naoAtendeu: Boolean) {
            chamadas += "recusar $chamadaId" + if (naoAtendeu) " nao_atendeu" else ""
        }

        override suspend fun sair(chamadaId: Long) {
            chamadas += "sair $chamadaId"
            passar("sair")
        }

        override suspend fun cancelar(chamadaId: Long) {
            chamadas += "cancelar $chamadaId"
            passar("cancelar")
        }

        override suspend fun anunciarVideo(chamadaId: Long) {
            chamadas += "video $chamadaId"
        }

        override suspend fun chat(chamadaId: Long): Long {
            chamadas += "chat $chamadaId"
            return 77
        }

        override suspend fun adicionar(chamadaId: Long, usuarioId: Long) {
            chamadas += "adicionar $chamadaId $usuarioId"
            dados[chamadaId]?.let { dados[chamadaId] = it.copy(participantes = it.participantes + p(usuarioId, PENDENTE)) }
        }

        override suspend fun ice() = ServidoresIce(emptyList(), somenteRelay = true)
    }

    private class MidiaFalsa : MidiaChamada {
        val acoes = mutableListOf<String>()
        var local = MidiaLocal.AUDIO_VIDEO
        var falharPublicar = false
        var portaoVideo: CompletableDeferred<Unit>? = null

        override suspend fun abrirLocal(video: Boolean): MidiaLocal {
            acoes += "abrir video=$video"
            return if (!video && local == MidiaLocal.AUDIO_VIDEO) MidiaLocal.AUDIO else local
        }

        override suspend fun publicar(chamadaId: Long, eu: Long) {
            acoes += "publicar $chamadaId"
            if (falharPublicar) throw IOException("WHIP")
        }

        override suspend fun sincronizar(chamadaId: Long, participantes: Set<Long>, comVideo: Boolean) {
            acoes += "sincronizar ${participantes.sorted()}"
        }

        override suspend fun reassinar(usuarioId: Long) {
            acoes += "reassinar $usuarioId"
        }

        override fun desconectar(usuarioId: Long) {
            acoes += "desconectar $usuarioId"
        }

        override suspend fun ativarVideo(transmitir: Boolean): Boolean {
            acoes += "ativarVideo $transmitir"
            portaoVideo?.await()
            return true
        }

        override fun microfone(ligado: Boolean) {
            acoes += "microfone $ligado"
        }

        override fun camera(ligada: Boolean) {
            acoes += "camera $ligada"
        }

        override fun encerrar() {
            acoes += "encerrar"
        }
    }

    private companion object {
        const val EU = 7L
        const val OUTRO = 8L
        const val TERCEIRO = 9L
        const val RECEBIDA = 20L
        val INICIO: Instant = Instant.parse("2026-10-08T12:00:00Z")

        fun p(id: Long, status: StatusParticipante) = ParticipanteChamada(id, "Usuário $id", status)

        fun chamada(
            id: Long,
            tipo: TipoChamada,
            vararg participantes: ParticipanteChamada,
            status: StatusChamada = StatusChamada.PENDENTE,
            criadoPor: Long = OUTRO,
        ) = Chamada(id, tipo, status, INICIO, criadoPor, null, null, null, participantes.toList())

        fun Chamada.comStatus(usuario: Long, status: StatusParticipante) =
            copy(participantes = participantes.map { if (it.usuarioId == usuario) it.copy(status = status) else it })

        fun pendente(id: Long, criadoEm: Instant) = ChamadaPendente(id, TipoChamada.AUDIO, StatusChamada.PENDENTE, 42, criadoEm, OUTRO)
    }
}
