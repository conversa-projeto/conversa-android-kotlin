package com.conversa.app.core.data.rascunhos

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.model.RascunhoNaLista
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoReferencia
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rascunho por conversa no Room (FC-519). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RascunhosRepositorioTest {
    private lateinit var banco: ConversaBanco

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    @Test
    fun `guarda texto, anexos e a resposta, e vazio apaga`() = runTest {
        val repo = RascunhosRepositorio(banco.rascunhoDao(), backgroundScope, Clock.systemUTC())
        val foto = AnexoLocal("content://fotos/1", "foto.jpg", 2048, "image/jpeg", TipoConteudo.IMAGEM)
        val rascunho = Rascunho("oi @[Bia](3)", listOf(foto), TipoReferencia.RESPOSTA to 10L)

        repo.guardar(42, rascunho).join()

        assertThat(repo.ler(42)).isEqualTo(rascunho)
        assertThat(repo.ler(43)).isNull()

        repo.guardar(42, Rascunho()).join()
        assertThat(repo.ler(42)).isNull()
    }

    @Test
    fun `gravacoes seguidas valem na ordem`() = runTest {
        val repo = RascunhosRepositorio(banco.rascunhoDao(), backgroundScope, Clock.systemUTC())

        repo.guardar(42, Rascunho("a"))
        repo.guardar(42, Rascunho("ab"))
        repo.guardar(42, Rascunho("abc")).join()

        assertThat(repo.ler(42)!!.texto).isEqualTo("abc")
    }

    @Test
    fun `a lista ve texto e anexos, e nao ve a resposta pendente sozinha`() = runTest {
        val repo = RascunhosRepositorio(banco.rascunhoDao(), backgroundScope, Clock.systemUTC())
        val foto = AnexoLocal("content://fotos/1", "foto.jpg", 2048, "image/jpeg", TipoConteudo.IMAGEM)

        repo.guardar(1, Rascunho("oi @[Bia](3)")).join()
        repo.guardar(2, Rascunho(anexos = listOf(foto, foto.copy(uri = "content://fotos/2")))).join()
        repo.guardar(3, Rascunho(referencia = TipoReferencia.RESPOSTA to 10L)).join()

        assertThat(repo.observarNaLista().first()).containsExactly(
            1L,
            RascunhoNaLista("oi @Bia", 0),
            2L,
            RascunhoNaLista("", 2),
        )

        // Enviada (campo vazio): sai da lista.
        repo.guardar(1, Rascunho()).join()
        assertThat(repo.observarNaLista().first().keys).containsExactly(2L)
    }
}
