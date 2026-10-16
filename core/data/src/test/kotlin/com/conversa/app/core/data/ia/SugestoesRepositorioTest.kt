package com.conversa.app.core.data.ia

import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.data.sistema.RecursosRepositorio
import com.conversa.app.core.model.Recursos
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.PedirSugestaoRequisicao
import com.conversa.app.core.network.dto.SugestaoDto
import com.conversa.app.core.network.http.ErroApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Sugestão de texto da IA (ENV-25). */
class SugestoesRepositorioTest {
    private val api = mockk<ConversaApi>()
    private val servidor = MutableStateFlow<Recursos?>(Recursos(transcricao = false, ia = true))
    private val recursos = mockk<RecursosRepositorio> { every { this@mockk.recursos } returns servidor }
    private val pessoa = MutableStateFlow(true)
    private val preferencias = mockk<PreferenciasRepositorio> { every { sugestoesIa } returns pessoa }
    private val repo = SugestoesRepositorio(api, recursos, preferencias)

    @Test
    fun `liga com a IA do servidor e a preferencia do aparelho`() = runTest {
        assertThat(repo.ligadas.first()).isTrue()
        pessoa.value = false
        assertThat(repo.ligadas.first()).isFalse()
        assertThat(repo.disponivel.first()).isTrue()
        servidor.value = Recursos(transcricao = false, ia = false)
        assertThat(repo.disponivel.first()).isFalse()
    }

    @Test
    fun `a continuacao vem como esta e a falha vira vazio`() = runTest {
        coEvery { api.sugestao(PedirSugestaoRequisicao(42, "Ok, então vamos")) } returns SugestaoDto(" aguardar até segunda.")
        assertThat(repo.sugerir(42, "Ok, então vamos")).isEqualTo(" aguardar até segunda.")

        coEvery { api.sugestao(any()) } throws ErroApi.Servidor(400, "IA não configurada")
        assertThat(repo.sugerir(42, "Ok, então vamos")).isEmpty()
    }
}
