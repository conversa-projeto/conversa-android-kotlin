package com.conversa.app.feature.chamada

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.testing.RegraDispatcherPrincipal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/** Ligar a partir do perfil da pessoa (TODO 8.4). */
class LigarViewModelTest {
    @get:Rule val regra = RegraDispatcherPrincipal()

    private val gerenciador = mockk<GerenciadorChamadas>(relaxed = true)
    private val sessoes = mockk<SessaoRepositorio> {
        every { sessao } returns MutableStateFlow(Sessao("tok", 7, "Ana"))
    }

    @Test
    fun `ligar para a pessoa e voz na direta, com ela e eu`() {
        val vm = LigarViewModel(gerenciador, mockk<ConversasRepositorio>(), sessoes)

        vm.ligarParaUsuario(usuarioId = 9, conversaId = 3)

        verify { gerenciador.ligar(TipoChamada.AUDIO, listOf(9L, 7L), 3L) }
    }
}
