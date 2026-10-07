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
    private val player = PlayerFalso()

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

    private fun TestScope.criar(): ChatViewModel {
        val vm = ChatViewModel(
            SavedStateHandle(mapOf("conversaId" to 42L)),
            conversas,
            contatos,
            presenca,
            sessao,
            mensagens,
            envio,
            arquivos,
            mockk(relaxed = true),
            player,
            mockk(relaxed = true),
            Clock.fixed(agora, ZoneOffset.UTC),
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
