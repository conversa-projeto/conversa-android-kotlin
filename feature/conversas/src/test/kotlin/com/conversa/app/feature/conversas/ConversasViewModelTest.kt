package com.conversa.app.feature.conversas

import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.PreviaConversa
import com.conversa.app.core.model.RotuloData
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import com.conversa.app.feature.conversas.lista.ConversasUiState
import com.conversa.app.feature.conversas.lista.ConversasViewModel
import com.conversa.app.feature.conversas.lista.EventoConversas
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ConversasViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val agora = Instant.parse("2026-10-07T15:00:00Z")
    private val conversas = MutableStateFlow<List<Conversa>>(emptyList())
    private val contatos = MutableStateFlow<List<Contato>>(emptyList())
    private val online = MutableStateFlow<Set<Long>>(emptySet())
    private val digitando = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())
    private val carregada = MutableStateFlow(false)
    private val falha = MutableStateFlow<ErroApi?>(null)

    private val repoConversas = mockk<ConversasRepositorio>(relaxed = true) { every { observarTodas() } returns conversas }
    private val repoContatos = mockk<ContatosRepositorio>(relaxed = true) { every { observarOutros() } returns contatos }
    private val presenca = mockk<PresencaRepositorio> {
        every { online } returns this@ConversasViewModelTest.online
        every { digitando } returns this@ConversasViewModelTest.digitando
    }
    private val iniciador = mockk<IniciadorSessao> {
        every { carregada } returns this@ConversasViewModelTest.carregada
        every { falha } returns this@ConversasViewModelTest.falha
    }

    private fun conversa(
        id: Long,
        tipo: TipoConversa = TipoConversa.DIRETA,
        nome: String = "C$id",
        destinatario: Long? = null,
        ultima: Long = 0,
        naoLidas: Int = 0,
        fixada: Int? = null,
        arquivada: Boolean = false,
    ) = Conversa(
        id = id,
        tipo = tipo,
        descricao = nome,
        nome = null,
        destinatarioId = destinatario,
        ultimaMensagemId = ultima,
        ultimaMensagemEm = if (ultima > 0) agora else null,
        ultimaMensagemTexto = if (ultima > 0) "oi @[Ana](7)" else null,
        naoLidas = naoLidas,
        fixadaOrdem = fixada,
        arquivadaEm = if (arquivada) agora else null,
        avatarUrl = null,
    )

    private fun TestScope.criar(): Pair<ConversasViewModel, () -> ConversasUiState> {
        val vm = ConversasViewModel(repoConversas, repoContatos, presenca, iniciador, Clock.fixed(agora, ZoneOffset.UTC))
        backgroundScope.launch { vm.estado.collect {} }
        return vm to { vm.estado.value }
    }

    @Test
    fun `cache vazio antes da primeira carga e carregando, depois vira lista vazia`() = runTest {
        val (_, estado) = criar()
        advanceUntilIdle()
        assertThat(estado().carregando).isTrue()

        carregada.value = true
        advanceUntilIdle()
        assertThat(estado().carregando).isFalse()
        assertThat(estado().principais).isEmpty()
    }

    @Test
    fun `falha da primeira carga com cache vazio vira erro em tela cheia`() = runTest {
        val (_, estado) = criar()
        falha.value = ErroApi.ServidorIndisponivel(502)
        advanceUntilIdle()
        assertThat(estado().carregando).isFalse()
        assertThat(estado().erro).isNotNull()
    }

    @Test
    fun `monta itens - ordem, online, digitando, previa e arquivadas sem contador`() = runTest {
        carregada.value = true
        conversas.value = listOf(
            conversa(1, destinatario = 8, ultima = 50, naoLidas = 2),
            conversa(2, tipo = TipoConversa.GRUPO, ultima = 90, fixada = 1),
            conversa(3, destinatario = 9, ultima = 70, naoLidas = 5, arquivada = true),
        )
        online.value = setOf(8L)
        digitando.value = mapOf(1L to setOf(8L))
        val (_, estado) = criar()
        advanceUntilIdle()

        val s = estado()
        assertThat(s.principais.map { it.id }).containsExactly(2L, 1L).inOrder()
        val direta = s.principais.last()
        assertThat(direta.online).isTrue()
        assertThat(direta.digitando).isTrue()
        assertThat(direta.naoLidas).isEqualTo(2)
        assertThat(direta.previa).isEqualTo(PreviaConversa.Texto("oi @Ana"))
        assertThat(direta.rotulo).isEqualTo(RotuloData.Hora("15:00"))
        assertThat(s.principais.first().grupo).isTrue()
        assertThat(s.arquivadas.single().id).isEqualTo(3)
        assertThat(s.arquivadas.single().naoLidas).isEqualTo(0)
    }

    @Test
    fun `com termo as arquivadas entram na busca e nova conversa filtra contatos sem direta`() = runTest {
        carregada.value = true
        conversas.value = listOf(conversa(1, nome = "Bruno", destinatario = 8), conversa(3, nome = "Bruna antiga", arquivada = true))
        contatos.value =
            listOf(
                Contato(8, "Bruno", "bruno", null, null),
                Contato(9, "Bruna", "bruna", null, null),
                Contato(10, "Carla", "carla", null, null),
            )
        val (vm, estado) = criar()
        advanceUntilIdle()
        assertThat(estado().novaConversa.map { it.id }).containsExactly(9L, 10L)

        vm.alterarTermo("brun")
        advanceUntilIdle()
        assertThat(estado().principais.map { it.id }).containsExactly(1L, 3L)
        assertThat(estado().arquivadas).isEmpty()
        assertThat(estado().novaConversa.map { it.id }).containsExactly(9L)
    }

    @Test
    fun `fixadas sabem se podem subir ou descer`() = runTest {
        carregada.value = true
        conversas.value = listOf(conversa(1, fixada = 1), conversa(2, fixada = 2), conversa(3, fixada = 3), conversa(4))
        val (_, estado) = criar()
        advanceUntilIdle()
        val porId = estado().principais.associateBy { it.id }
        assertThat(porId.getValue(1).podeSubir).isFalse()
        assertThat(porId.getValue(1).podeDescer).isTrue()
        assertThat(porId.getValue(3).podeDescer).isFalse()
        assertThat(porId.getValue(4).podeSubir).isFalse()
    }

    @Test
    fun `erro ao fixar vira aviso`() = runTest {
        coEvery { repoConversas.fixar(4) } returns Result.failure(ErroApi.SemConexao(java.io.IOException("x")))
        val (vm, _) = criar()
        vm.fixar(4)
        advanceUntilIdle()
        assertThat(vm.eventos.fluxo.first()).isInstanceOf(EventoConversas.Erro::class.java)
    }

    @Test
    fun `tocar num contato abre a direta`() = runTest {
        coEvery { repoConversas.obterOuCriarDireta(9) } returns Result.success(42)
        val (vm, _) = criar()
        vm.abrirContato(9)
        advanceUntilIdle()
        assertThat(vm.eventos.fluxo.first()).isEqualTo(EventoConversas.Abrir(42))
    }
}
