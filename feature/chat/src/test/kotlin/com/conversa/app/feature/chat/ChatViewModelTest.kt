package com.conversa.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.ArquivosLocais
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.data.mensagens.ReferenciaPendente
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.media.EstadoAudio
import com.conversa.app.core.media.PlayerAudio
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val agora = Instant.parse("2026-10-07T15:00:00Z")
    private val lista = MutableStateFlow<List<Mensagem>>(emptyList())
    private val mensagens = mockk<MensagensRepositorio>(relaxed = true) {
        every { observar(42) } returns lista
        coEvery { carregarRecentes(42) } returns Result.success(0)
    }
    private val envio = mockk<EnvioMensagens>(relaxed = true) { every { progresso } returns MutableStateFlow(emptyMap()) }
    private val conversas = mockk<ConversasRepositorio>(relaxed = true) {
        every { observarTodas() } returns MutableStateFlow(emptyList())
        every { observar(42) } returns MutableStateFlow(
            Conversa(42, TipoConversa.DIRETA, null, "Bruno", 8, 0, null, null, 0, null, null, null),
        )
    }
    private val contatos = mockk<ContatosRepositorio> { every { observarOutros() } returns MutableStateFlow(emptyList()) }
    private val presenca = mockk<PresencaRepositorio> {
        every { online } returns MutableStateFlow(setOf(8L))
        every { digitando } returns MutableStateFlow(emptyMap())
        every { gravando } returns MutableStateFlow(emptyMap())
    }
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana")) }
    private val arquivos = mockk<ArquivosLocais>(relaxed = true)
    private val transcricoes = mockk<com.conversa.app.core.data.anexos.TranscricoesRepositorio>(relaxed = true) {
        every { desligada } returns MutableStateFlow(false)
        every { erros } returns MutableStateFlow(emptyMap())
        every { pedindo } returns MutableStateFlow(emptySet())
    }
    private val downloads = mockk<com.conversa.app.core.data.anexos.DownloadsRepositorio>(relaxed = true) {
        every { resultados } returns kotlinx.coroutines.flow.MutableSharedFlow()
    }
    private val player = PlayerFalso()
    private val enquetes = mockk<com.conversa.app.core.data.enquetes.EnquetesRepositorio>(relaxed = true)
    private val rascunhos = mockk<com.conversa.app.core.data.rascunhos.RascunhosRepositorio>(relaxed = true) {
        coEvery { ler(any()) } returns null
    }

    private fun mensagem(id: Long, remetente: Long, lida: Boolean) = Mensagem(
        id = id,
        remetenteId = remetente,
        remetente = "x",
        conversaId = 42,
        inserida = agora.plusSeconds(id),
        visivelEm = null,
        excluidaEm = null,
        referencia = null,
        recebida = true,
        visualizada = lida,
        reproduzida = false,
        conteudos = listOf(Conteudo(id, 1, TipoConteudo.TEXTO, "m$id")),
    )

    private val compartilhamentos = mockk<com.conversa.app.core.data.anexos.Compartilhamentos>(relaxed = true)

    private fun TestScope.criar(
        comCompartilhamento: Boolean = false,
        focar: Boolean = false,
        mensagemId: Long = 0,
        encaminharDe: Long = 0,
    ): ChatViewModel {
        val vm = ChatViewModel(
            SavedStateHandle(
                mapOf(
                    "conversaId" to 42L,
                    "comCompartilhamento" to comCompartilhamento,
                    "focar" to focar,
                    "mensagemId" to mensagemId,
                    "encaminharDe" to encaminharDe,
                ),
            ),
            conversas,
            contatos,
            presenca,
            sessao,
            mensagens,
            envio,
            mockk(relaxed = true),
            arquivos,
            downloads,
            transcricoes,
            compartilhamentos,
            mockk<com.conversa.app.core.data.rede.EconomiaDados> { every { ativa } returns MutableStateFlow(false) },
            com.conversa.app.core.data.notificacoes.ConversaEmTela(),
            mockk(relaxed = true),
            player,
            mockk(relaxed = true),
            Clock.fixed(agora, ZoneOffset.UTC),
            rascunhos,
            enquetes,
        )
        backgroundScope.launch { vm.estado.collect {} }
        return vm
    }

    @Test
    fun `abrir recarrega da rede e decide a linha Ultimas uma vez so`() = runTest {
        lista.value =
            listOf(mensagem(1, 8, lida = true), mensagem(2, 7, lida = false), mensagem(3, 8, lida = false), mensagem(4, 8, lida = false))
        val vm = criar()
        advanceUntilIdle()

        coVerify { mensagens.carregarRecentes(42) }
        val estado = vm.estado.value
        assertThat(estado.pronto).isTrue()
        assertThat(estado.online).isTrue()
        val ordem = estado.itens.map { (it as? ItemChat.Bolha)?.mensagem?.id ?: if (it is ItemChat.NaoLidas) -99L else 0L }
        // A minha (2) não conta como não lida; a linha fica antes da 3.
        assertThat(ordem).containsExactly(0L, 1L, 2L, -99L, 3L, 4L).inOrder()

        // Depois de lidas, a linha não pula (fica até sair da conversa).
        lista.value = lista.value.map { it.copy(visualizada = true) } + mensagem(5, 8, lida = false)
        advanceUntilIdle()
        val linha = vm.estado.value.itens.indexOfFirst { it is ItemChat.NaoLidas }
        assertThat((vm.estado.value.itens[linha + 1] as ItemChat.Bolha).mensagem.id).isEqualTo(3)
    }

    @Test
    fun `marca como lidas so as de outras pessoas ainda nao lidas`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        vm.marcarLidas(
            listOf(mensagem(1, 8, lida = false), mensagem(2, 7, lida = false), mensagem(3, 8, lida = true), mensagem(-4, 8, lida = false)),
        )
        advanceUntilIdle()
        coVerify(exactly = 1) { mensagens.marcarLida(42, 1) }
        coVerify(exactly = 1) { mensagens.marcarLida(any(), any()) }
    }

    @Test
    fun `digitando - no maximo um aviso a cada 2,5 s e o ultimo e adiado, nao descartado`() = runTest {
        val vm = criar()
        advanceUntilIdle()

        vm.aoDigitar("o")
        runCurrent()
        coVerify(exactly = 1) { mensagens.avisarDigitando(42) }

        repeat(5) {
            advanceTimeBy(300)
            vm.aoDigitar("oi$it")
        }
        runCurrent()
        coVerify(exactly = 1) { mensagens.avisarDigitando(42) }

        advanceTimeBy(ChatViewModel.INTERVALO_DIGITANDO_MS)
        runCurrent()
        coVerify(exactly = 2) { mensagens.avisarDigitando(42) }

        vm.aoDigitar("   ")
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 2) { mensagens.avisarDigitando(42) }
    }

    @Test
    fun `votacao - a falha traz a mensagem do servidor`() = runTest {
        coEvery { enquetes.votar(5, listOf(1L)) } returns Result.failure(
            com.conversa.app.core.network.http.ErroApi.Servidor(400, "Esta votação já foi encerrada."),
        )
        coEvery { enquetes.encerrar(5) } returns Result.success(
            com.conversa.app.core.model.Enquete(5, 42, 9, "Onde?", false, 7, emptyList(), 0, emptyList()),
        )
        val vm = criar()

        assertThat(vm.acoesEnquete.votar(5, listOf(1L)).exceptionOrNull()?.message).isEqualTo("Esta votação já foi encerrada.")
        assertThat(vm.acoesEnquete.encerrar(5).isSuccess).isTrue()
    }

    @Test
    fun `criar votacao rele as mensagens e a lista, e mostra o erro do servidor`() = runTest {
        coEvery { enquetes.criar(42, "Onde?", listOf("a", "b"), false, null) } returns Result.success(77)
        coEvery { enquetes.criar(42, "Onde?", listOf("a"), false, null) } returns Result.failure(
            com.conversa.app.core.network.http.ErroApi.Servidor(400, "Votação só em grupo."),
        )
        val vm = criar()
        advanceUntilIdle()

        assertThat(vm.criarVotacao("Onde?", listOf("a", "b"), false, null)).isNull()
        coVerify(exactly = 2) { mensagens.carregarRecentes(42) }
        coVerify { conversas.atualizar() }
        assertThat(vm.criarVotacao("Onde?", listOf("a"), false, null)).isEqualTo("Votação só em grupo.")
    }

    @Test
    fun `rascunho volta ao abrir - texto, anexos e a resposta`() = runTest {
        val pergunta = mensagem(10, 8, lida = true)
        val foto = com.conversa.app.core.data.anexos.AnexoLocal("content://f/1", "f.jpg", 10, "image/jpeg", TipoConteudo.IMAGEM)
        coEvery { rascunhos.ler(42) } returns com.conversa.app.core.data.rascunhos.Rascunho(
            "oi @[Bia](3)",
            listOf(foto),
            TipoReferencia.RESPOSTA to 10L,
        )
        coEvery { mensagens.buscar(10) } returns pergunta
        val vm = criar()
        advanceUntilIdle()

        assertThat(vm.estado.value.textoParaCampo).isEqualTo("oi @[Bia](3)")
        assertThat(vm.estado.value.fila).containsExactly(foto)
        assertThat(vm.estado.value.respondendo).isEqualTo(ReferenciaPendente(TipoReferencia.RESPOSTA, pergunta))
    }

    @Test
    fun `mudancas do campo viram rascunho depois de um pequeno atraso`() = runTest {
        val vm = criar()
        runCurrent()

        vm.aoMudarRascunho("a")
        vm.aoMudarRascunho("ab")
        advanceTimeBy(600)
        runCurrent()

        io.mockk.verify(exactly = 1) { rascunhos.guardar(42, com.conversa.app.core.data.rascunhos.Rascunho("ab")) }
        io.mockk.verify(exactly = 0) { rascunhos.guardar(42, com.conversa.app.core.data.rascunhos.Rascunho("a")) }
    }

    @Test
    fun `agendada fica fora do chat, no relogio, e entra na hora exata`() = runTest {
        val agendada = mensagem(5, 7, lida = false).copy(visivelEm = agora.plusSeconds(60))
        lista.value = listOf(mensagem(1, 8, lida = true), agendada)
        val vm = criar()
        runCurrent()

        val bolhas = { vm.estado.value.itens.filterIsInstance<ItemChat.Bolha>().map { it.mensagem.id } }
        assertThat(bolhas()).containsExactly(1L)
        assertThat(vm.estado.value.agendadas.map { it.id }).containsExactly(5L)

        advanceTimeBy(60_001)
        runCurrent()

        assertThat(bolhas()).containsExactly(1L, 5L).inOrder()
        assertThat(vm.estado.value.agendadas).isEmpty()
    }

    @Test
    fun `agendar manda o mesmo envio com visivel_em e limpa o campo`() = runTest {
        val quando = java.time.Instant.parse("2027-01-02T11:00:00Z")
        coEvery { envio.enviar(42, "depois", any(), any(), any(), any()) } returns -1
        val vm = criar()
        advanceUntilIdle()

        var limpou = false
        vm.agendar("depois", quando) { limpou = true }
        advanceUntilIdle()

        coVerify { envio.enviar(42, "depois", emptyList(), null, visivelEm = quando) }
        assertThat(limpou).isTrue()
    }

    @Test
    fun `enviar grava, avisa para limpar o campo, rola ao fim e zera o digitando`() = runTest {
        coEvery { envio.enviar(42, "olá", any()) } returns -1
        val vm = criar()
        advanceUntilIdle()
        vm.aoDigitar("olá")
        runCurrent()

        var limpou = false
        vm.enviar("  olá  ") { limpou = true }
        advanceUntilIdle()

        coVerify { envio.enviar(42, "olá", emptyList()) }
        assertThat(limpou).isTrue()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.RolarAoFim)
        // Depois de enviar, o próximo "digitando" sai na hora (o limite recomeça).
        vm.aoDigitar("a")
        runCurrent()
        coVerify(exactly = 2) { mensagens.avisarDigitando(42) }

        vm.enviar("   ") { }
        advanceUntilIdle()
        coVerify(exactly = 1) { envio.enviar(any(), any(), any()) }
    }

    // --- Áudio (4.5) ---

    private fun audio(id: Long, remetente: Long) = mensagem(id, remetente, lida = true).copy(
        conteudos = listOf(Conteudo(id, 1, TipoConteudo.GRAVACAO_AUDIO, "hash$id", "audio-$id.webm", "webm")),
    )

    @Test
    fun `audio - baixa uma vez, toca, marca ouvido so o de outra pessoa e o toque seguinte pausa e continua`() = runTest {
        val arquivo = java.io.File("/cache/anexos/hash1/audio 1.webm")
        coEvery { arquivos.baixar("hash1", "audio-1.webm") } returns Result.success(arquivo)
        coEvery { arquivos.baixar("hash2", "audio-2.webm") } returns Result.success(java.io.File("/cache/b.webm"))
        val vm = criar()
        advanceUntilIdle()
        val deOutro = audio(1, 8)
        val meu = audio(2, 7)

        vm.alternarAudio(deOutro, deOutro.conteudos[0])
        advanceUntilIdle()
        assertThat(player.tocados.single()).isEqualTo("42:1:1" to arquivo.toURI().toString())
        coVerify(exactly = 1) { mensagens.marcarReproduzida(42, 1) }

        vm.alternarAudio(deOutro, deOutro.conteudos[0])
        assertThat(player.estado.value.tocando).isFalse()
        vm.alternarAudio(deOutro, deOutro.conteudos[0])
        assertThat(player.estado.value.tocando).isTrue()
        coVerify(exactly = 1) { arquivos.baixar(any(), any()) }

        // O meu: toca no lugar do outro, sem marcar "ouvido".
        vm.alternarAudio(meu, meu.conteudos[0])
        advanceUntilIdle()
        assertThat(player.tocados.last().first).isEqualTo("42:2:1")
        coVerify(exactly = 0) { mensagens.marcarReproduzida(42, 2) }
    }

    @Test
    fun `audio - pula so no que esta no player, guarda a duracao e para ao sair da conversa`() = runTest {
        coEvery { arquivos.baixar(any(), any()) } returns Result.success(java.io.File("/cache/a.webm"))
        val vm = criar()
        backgroundScope.launch { vm.audio.collect {} }
        advanceUntilIdle()
        val m = audio(1, 8)
        vm.alternarAudio(m, m.conteudos[0])
        advanceUntilIdle()
        player.estado.update { it.copy(duracaoMs = 10_000) }
        advanceUntilIdle()

        vm.buscarAudio("42:1:1", 0.5f)
        assertThat(player.estado.value.posicaoMs).isEqualTo(5_000)
        vm.buscarAudio("42:9:1", 0.9f)
        assertThat(player.estado.value.posicaoMs).isEqualTo(5_000)
        assertThat(vm.audio.value.duracoes).containsEntry("42:1:1", 10_000L)
        assertThat(vm.audio.value.tocando).isTrue()

        // Sair da conversa (ViewModel limpo) para o áudio dela.
        val loja = ViewModelStore()
        ViewModelProvider.create(loja, viewModelFactory { initializer { vm } })[ChatViewModel::class]
        loja.clear()
        assertThat(player.estado.value.chave).isNull()
    }

    @Test
    fun `audio de outra conversa nao aparece nesta nem e parado por ela`() = runTest {
        player.tocar("99:5:1", "file:/x")
        val vm = criar()
        backgroundScope.launch { vm.audio.collect {} }
        advanceUntilIdle()
        assertThat(vm.audio.value.chave).isNull()
        assertThat(vm.audio.value.tocando).isFalse()

        val loja = ViewModelStore()
        ViewModelProvider.create(loja, viewModelFactory { initializer { vm } })[ChatViewModel::class]
        loja.clear()
        assertThat(player.estado.value.chave).isEqualTo("99:5:1")
    }

    // --- Compartilhar e colar (4.9, 4.10) ---

    private fun anexoLocal(nome: String) = com.conversa.app.core.data.anexos.AnexoLocal(
        "content://app.arquivos/compartilhados/$nome",
        nome,
        10,
        "image/png",
        TipoConteudo.IMAGEM,
    )

    @Test
    fun `vindo do Enviar para, os arquivos entram na fila e o texto vai uma vez para o campo`() = runTest {
        every { compartilhamentos.retirar() } returns
            com.conversa.app.core.data.anexos.ItensCompartilhados("veja", listOf(anexoLocal("a.png")))
        val vm = criar(comCompartilhamento = true)
        advanceUntilIdle()

        assertThat(vm.estado.value.fila.map { it.nome }).containsExactly("a.png")
        assertThat(vm.estado.value.textoParaCampo).isEqualTo("veja")
        vm.textoUsado()
        advanceUntilIdle()
        assertThat(vm.estado.value.textoParaCampo).isNull()
    }

    @Test
    fun `conversa aberta normalmente nao pega o compartilhamento pendente`() = runTest {
        criar()
        advanceUntilIdle()
        io.mockk.verify(exactly = 0) { compartilhamentos.retirar() }
    }

    @Test
    fun `imagem colada e copiada e entra na fila sem repetir`() = runTest {
        coEvery { compartilhamentos.copiarColados(listOf("content://teclado/1")) } returns listOf(anexoLocal("colada.png"))
        val vm = criar()
        advanceUntilIdle()

        vm.colarAnexos(listOf("content://teclado/1"))
        vm.colarAnexos(listOf("content://teclado/1"))
        advanceUntilIdle()

        assertThat(vm.estado.value.fila.map { it.nome }).containsExactly("colada.png")
    }

    // --- Menu da mensagem (etapa 7) ---

    @Test
    fun `copiar junta os textos da mensagem, um por linha`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        val duas = mensagem(5, 8, lida = true).copy(
            conteudos = listOf(Conteudo(5, 1, TipoConteudo.TEXTO, "oi"), Conteudo(5, 2, TipoConteudo.TEXTO, "tudo bem?")),
        )

        vm.copiar(duas)

        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.CopiarTexto("oi\ntudo bem?"))
    }

    @Test
    fun `copiar imagem baixa e manda o arquivo, nao o texto`() = runTest {
        val arquivo = java.io.File("foto.jpg")
        coEvery { arquivos.baixar("img-1", "foto.jpg") } returns Result.success(arquivo)
        val vm = criar()
        advanceUntilIdle()
        val comImagem = mensagem(6, 8, lida = true).copy(
            conteudos = listOf(
                Conteudo(6, 1, TipoConteudo.IMAGEM, "img-1", nome = "foto.jpg"),
                Conteudo(6, 2, TipoConteudo.TEXTO, "legenda"),
            ),
        )

        vm.copiar(comImagem)
        advanceUntilIdle()

        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.CopiarImagem(arquivo))
    }

    @Test
    fun `reagir usa o meu nome e ocultar que falha avisa a tela`() = runTest {
        coEvery { mensagens.reagir(any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { mensagens.ocultar(5) } returns Result.failure(java.io.IOException("sem rede"))
        val vm = criar()
        advanceUntilIdle()

        vm.reagir(mensagem(5, 8, lida = true), "👍")
        vm.ocultar(mensagem(5, 7, lida = true))
        advanceUntilIdle()

        coVerify { mensagens.reagir(42, 5, "👍", 7, "Ana") }
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.OcultarFalhou)
        coVerify(exactly = 0) { conversas.atualizar() }
    }

    @Test
    fun `ocultar que deu certo atualiza a lista de conversas`() = runTest {
        coEvery { mensagens.ocultar(5) } returns Result.success(Unit)
        val vm = criar()
        advanceUntilIdle()

        vm.ocultar(mensagem(5, 7, lida = true))
        advanceUntilIdle()

        coVerify(exactly = 1) { conversas.atualizar() }
    }

    @Test
    fun `conversa que nao esta no aparelho e buscada antes dos membros`() = runTest {
        val fluxo = MutableStateFlow<Conversa?>(null)
        every { conversas.observar(42) } returns fluxo
        coEvery { conversas.atualizar() } coAnswers {
            fluxo.value = Conversa(42, TipoConversa.GRUPO, "Chamada: Ana, Bruno", null, 0, 0, null, null, 0, null, null, null)
            Result.success(Unit)
        }
        coEvery { conversas.membros(42) } returns Result.success(emptyList())

        criar()
        advanceUntilIdle()

        coVerify(exactly = 1) { conversas.atualizar() }
        coVerify { conversas.membros(42) }
    }

    @Test
    fun `responder mostra a barra, foca o campo e vai no proximo envio`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        val pergunta = mensagem(9, 8, lida = true)

        vm.responder(mensagem(-3, 7, lida = true))
        advanceUntilIdle()
        assertThat(vm.estado.value.respondendo).isNull()

        vm.responder(pergunta)
        advanceUntilIdle()
        assertThat(vm.estado.value.respondendo?.mensagem).isEqualTo(pergunta)
        assertThat(vm.estado.value.focarCampo).isTrue()

        vm.enviar("sim") { }
        advanceUntilIdle()

        coVerify { envio.enviar(42, "sim", emptyList(), ReferenciaPendente(TipoReferencia.RESPOSTA, pergunta)) }
        assertThat(vm.estado.value.respondendo).isNull()

        vm.responder(pergunta)
        vm.cancelarResposta()
        vm.enviar("outra") { }
        advanceUntilIdle()
        coVerify { envio.enviar(42, "outra", emptyList(), null) }
    }

    @Test
    fun `ir para a mensagem desta conversa rola, destaca 1,2 s e some`() = runTest {
        coEvery { mensagens.trazerAte(42, 7) } returns Result.success(true)
        val vm = criar()
        advanceUntilIdle()

        vm.irParaMensagem(7, 42)
        advanceUntilIdle()
        assertThat(vm.estado.value.irPara).isEqualTo(7)
        assertThat(vm.estado.value.destaque).isEqualTo(7)

        vm.chegouNaMensagem()
        runCurrent()
        assertThat(vm.estado.value.irPara).isNull()
        advanceTimeBy(1_100)
        assertThat(vm.estado.value.destaque).isEqualTo(7)
        advanceTimeBy(200)
        assertThat(vm.estado.value.destaque).isNull()
    }

    @Test
    fun `ir para a mensagem que nao achou avisa`() = runTest {
        coEvery { mensagens.trazerAte(42, 7) } returns Result.success(false)
        val vm = criar()
        advanceUntilIdle()

        vm.irParaMensagem(7, 42)
        advanceUntilIdle()

        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.MensagemNaoLocalizada)
        assertThat(vm.estado.value.irPara).isNull()
    }

    @Test
    fun `encaminhada de outra conversa abre aquela so se participo`() = runTest {
        every { conversas.observar(50) } returns MutableStateFlow(
            Conversa(50, TipoConversa.GRUPO, "Outro grupo", null, 0, 0, null, null, 0, null, null, null),
        )
        every { conversas.observar(60) } returns MutableStateFlow(null)
        val vm = criar()
        advanceUntilIdle()

        vm.irParaMensagem(70, 50)
        advanceUntilIdle()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.AbrirConversa(50, 70))

        vm.irParaMensagem(80, 60)
        advanceUntilIdle()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.MensagemNaoLocalizada)
    }

    @Test
    fun `aberta com a mensagem do link vai ate ela`() = runTest {
        coEvery { mensagens.trazerAte(42, 7) } returns Result.success(true)
        val vm = criar(mensagemId = 7)
        advanceUntilIdle()

        assertThat(vm.estado.value.irPara).isEqualTo(7)
    }

    @Test
    fun `encaminhar para conversa vai pela fila como encaminhada e abre o destino`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        val original = mensagem(9, 8, lida = true)

        vm.encaminhar(original, DestinoEncaminhar.ParaConversa(50, "Outro", grupo = true))
        advanceUntilIdle()

        coVerify { envio.enviar(50, "", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original)) }
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.AbrirConversa(50))
    }

    @Test
    fun `encaminhar para contato cria a direta antes e avisa se nao conseguir`() = runTest {
        coEvery { conversas.obterOuCriarDireta(8) } returns Result.success(60)
        coEvery { conversas.obterOuCriarDireta(9) } returns Result.failure(java.io.IOException("sem rede"))
        val vm = criar()
        advanceUntilIdle()
        val original = mensagem(9, 8, lida = true)

        vm.encaminhar(original, DestinoEncaminhar.ParaContato(8, "Bruno", null))
        advanceUntilIdle()
        coVerify { envio.enviar(60, "", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original)) }
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.AbrirConversa(60))

        vm.encaminhar(original, DestinoEncaminhar.ParaContato(9, "Carla", null))
        advanceUntilIdle()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.EncaminharFalhou)
    }

    @Test
    fun `responder no privado abre a direta com a mensagem pendente, que pode ir sem texto`() = runTest {
        coEvery { conversas.obterOuCriarDireta(8) } returns Result.success(60)
        val vm = criar()
        advanceUntilIdle()

        vm.responderNoPrivado(mensagem(9, 8, lida = true))
        vm.responderNoPrivado(mensagem(10, 7, lida = true))
        advanceUntilIdle()

        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.AbrirConversa(60, encaminharDe = 9))
        coVerify(exactly = 1) { conversas.obterOuCriarDireta(any()) }
    }

    @Test
    fun `aberta com encaminharDe deixa a encaminhada pendente e o envio sem texto sai`() = runTest {
        val original = mensagem(9, 8, lida = true)
        coEvery { mensagens.buscar(9) } returns original
        val vm = criar(encaminharDe = 9)
        advanceUntilIdle()

        assertThat(vm.estado.value.respondendo).isEqualTo(ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original))
        assertThat(vm.estado.value.focarCampo).isTrue()

        vm.enviar("") { }
        advanceUntilIdle()

        coVerify { envio.enviar(42, "", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original)) }
        assertThat(vm.estado.value.respondendo).isNull()
    }

    @Test
    fun `figurinha vai na hora e leva a resposta pendente`() = runTest {
        val vm = criar()
        advanceUntilIdle()
        val pergunta = mensagem(9, 8, lida = true)

        vm.responder(pergunta)
        vm.enviarFigurinha("basico/coracao")
        advanceUntilIdle()

        coVerify { envio.enviar(42, "", emptyList(), ReferenciaPendente(TipoReferencia.RESPOSTA, pergunta), figurinha = "basico/coracao") }
        assertThat(vm.estado.value.respondendo).isNull()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoChat.RolarAoFim)
    }

    @Test
    fun `focar o campo vale uma vez`() = runTest {
        val vm = criar(focar = true)
        advanceUntilIdle()
        assertThat(vm.estado.value.focarCampo).isTrue()

        vm.campoFocado()
        advanceUntilIdle()

        assertThat(vm.estado.value.focarCampo).isFalse()
    }

    private class PlayerFalso : PlayerAudio {
        override val estado = MutableStateFlow(EstadoAudio())
        override val falhas = MutableSharedFlow<String>()
        val tocados = mutableListOf<Pair<String, String>>()

        override fun tocar(chave: String, uri: String) {
            tocados += chave to uri
            estado.value = EstadoAudio(chave = chave, tocando = true)
        }

        override fun pausar() = estado.update { it.copy(tocando = false) }

        override fun continuar() = estado.update { it.copy(tocando = true) }

        override fun buscar(posicaoMs: Long) = estado.update { it.copy(posicaoMs = posicaoMs) }

        override fun parar() {
            estado.value = EstadoAudio()
        }
    }
}
