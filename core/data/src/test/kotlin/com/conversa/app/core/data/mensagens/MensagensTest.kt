package com.conversa.app.core.data.mensagens

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.AnexoEnviado
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.FontesArquivo
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.paraEntidade
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.database.entidades.ConversaEntidade
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ConteudoDto
import com.conversa.app.core.network.dto.ConteudoEnvioDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.MensagemCriadaDto
import com.conversa.app.core.network.dto.MensagemDto
import com.conversa.app.core.network.dto.MensagemExcluidaDto
import com.conversa.app.core.network.dto.ReacaoDto
import com.conversa.app.core.network.dto.ReacaoRequisicao
import com.conversa.app.core.network.dto.ReacaoResposta
import com.conversa.app.core.network.dto.ReferenciaEnvioDto
import com.conversa.app.core.network.dto.SucessoDto
import com.conversa.app.core.network.dto.UsuarioReacaoDto
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.testing.escopoDoTeste
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/** Carregar/paginar, marcar como lida e a fila de envio, com o Room de verdade. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MensagensTest {
    private lateinit var banco: ConversaBanco
    private val api = mockk<ConversaApi>()
    private val agora = Instant.parse("2026-10-07T12:00:00Z")
    private val sessao = mockk<SessaoRepositorio> { every { sessao } returns MutableStateFlow(Sessao("t", 7, "Ana Souza")) }
    private val conversas = mockk<ConversasRepositorio>(relaxed = true)
    private val anexos = mockk<AnexosRepositorio>()
    private val fontes = mockk<FontesArquivo>()
    private val agendador = object : AgendadorEnvio {
        var agendados = 0
        var cancelados = 0

        override fun agendar() {
            agendados++
        }

        override fun cancelar() {
            cancelados++
        }
    }

    private fun msg(id: Long, texto: String = "m$id", remetente: Long = 8) = MensagemDto(
        id = id,
        remetenteId = remetente,
        remetente = "Bruno",
        conversaId = 42,
        inserida = agora.plusSeconds(id),
        conteudos = listOf(ConteudoDto(id = id, ordem = 1, tipo = 1, conteudo = texto)),
    )

    @Before
    fun abrir() {
        banco = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ConversaBanco::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun fechar() = banco.close()

    private fun envio() = EnvioMensagens(
        api,
        banco.mensagemDao(),
        banco.envioPendenteDao(),
        sessao,
        conversas,
        agendador,
        anexos,
        fontes,
        Clock.fixed(agora, ZoneOffset.UTC),
    )

    // --- Carregar e paginar ---

    @Test
    fun `abrir traz as 80 recentes e rolar para cima pede 60 antes da mais antiga, sem repetir`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo, java.time.Clock.systemUTC())
        coEvery { api.mensagens(42, 0, 80, 0) } returns (100L..179L).map { msg(it) }
        assertThat(repo.carregarRecentes(42).getOrThrow()).isEqualTo(80)

        coEvery { api.mensagens(42, 100, 60, 0) } returns (41L..100L).map { msg(it) }
        assertThat(repo.carregarAnteriores(42).getOrThrow()).isEqualTo(59)

        coEvery { api.mensagens(42, 41, 60, 0) } returns listOf(msg(41))
        assertThat(repo.carregarAnteriores(42).getOrThrow()).isEqualTo(0)

        val todas = repo.observar(42).first()
        assertThat(todas).hasSize(139)
        assertThat(todas.first().id).isEqualTo(41)
        escopo.cancel()
    }

    @Test
    fun `marcar como lida e otimista, desconta o contador e manda uma vez so`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo, java.time.Clock.systemUTC())
        banco.conversaDao().salvar(listOf(ConversaEntidade(42, 1, null, "Bruno", 8, 105, agora, "oi", 2, null, null, null)))
        coEvery { api.mensagens(42, 0, 80, 0) } returns listOf(msg(104), msg(105))
        coEvery { api.visualizar(any()) } returns SucessoDto(true)
        repo.carregarRecentes(42)

        repo.marcarLida(42, 104)
        repo.marcarLida(42, 104)
        advanceUntilIdle()

        assertThat(banco.mensagemDao().buscar(104)!!.mensagem.visualizada).isTrue()
        assertThat(banco.conversaDao().observar(42).first()!!.naoLidas).isEqualTo(1)
        coVerify(exactly = 1) { api.visualizar(MarcarStatusRequisicao(42, 104)) }
        escopo.cancel()
    }

    @Test
    fun `marcar a conversa como lida pela notificacao marca todas as de outros e espera o servidor`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo, java.time.Clock.systemUTC())
        banco.conversaDao().salvar(listOf(ConversaEntidade(42, 1, null, "Bruno", 8, 106, agora, "oi", 3, null, null, null)))
        coEvery { api.mensagens(42, 0, 80, 0) } returns listOf(msg(104), msg(105, remetente = 7), msg(106))
        coEvery { api.visualizar(any()) } returns SucessoDto(true)
        repo.carregarRecentes(42)

        repo.marcarConversaLida(42, eu = 7)

        // Sem avançar a fila: as chamadas já foram feitas.
        coVerify(exactly = 1) { api.visualizar(MarcarStatusRequisicao(42, 104)) }
        coVerify(exactly = 1) { api.visualizar(MarcarStatusRequisicao(42, 106)) }
        coVerify(exactly = 0) { api.visualizar(MarcarStatusRequisicao(42, 105)) }
        assertThat(banco.mensagemDao().naoLidasDeOutros(42, 7, 10)).isEmpty()
        assertThat(banco.conversaDao().observar(42).first()!!.naoLidas).isEqualTo(1)
        escopo.cancel()
    }

    @Test
    fun `primeiro play marca reproduzida no Room e no servidor uma vez so`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo, java.time.Clock.systemUTC())
        coEvery { api.mensagens(42, 0, 80, 0) } returns listOf(msg(104))
        coEvery { api.reproduzir(any()) } returns SucessoDto(true)
        repo.carregarRecentes(42)

        repo.marcarReproduzida(42, 104)
        repo.marcarReproduzida(42, 104)

        val mensagem = banco.mensagemDao().buscar(104)!!.mensagem
        assertThat(mensagem.reproduzida).isTrue()
        // Ouvir não é ler (o servidor também não marca).
        assertThat(mensagem.visualizada).isFalse()
        coVerify(exactly = 1) { api.reproduzir(MarcarStatusRequisicao(42, 104)) }
        escopo.cancel()
    }

    // --- Envio ---

    @Test
    fun `enviar cria a otimista, agenda e troca pela real`() = runTest {
        val envio = envio()
        val idLocal = envio.enviarTexto(42, "olá")

        assertThat(idLocal).isLessThan(0)
        val otimista = banco.mensagemDao().buscar(idLocal)!!
        assertThat(otimista.mensagem.enviando).isTrue()
        assertThat(otimista.mensagem.remetente).isEqualTo("Ana")
        assertThat(agendador.agendados).isEqualTo(1)

        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 500, conversaId = 42)
        coEvery { api.mensagens(42, 500, 0, 0) } returns listOf(msg(500, "olá", remetente = 7))

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        coVerify { api.enviarMensagem(EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "olá")))) }
        assertThat(banco.mensagemDao().buscar(idLocal)).isNull()
        assertThat(banco.mensagemDao().buscar(500)!!.conteudos.single().conteudo).isEqualTo("olá")
        assertThat(banco.envioPendenteDao().todos()).isEmpty()
        coVerify { conversas.atualizar() }
    }

    @Test
    fun `resposta manda a referencia e a otimista ja mostra a citacao`() = runTest {
        val envio = envio()
        val respondida = msg(300, "pergunta", remetente = 8).paraModelo()

        val idLocal = envio.enviar(42, "resposta", emptyList(), ReferenciaPendente(TipoReferencia.RESPOSTA, respondida))

        val citacao = banco.mensagemDao().buscar(idLocal)!!.paraModelo().referencia!!
        assertThat(citacao.tipo).isEqualTo(TipoReferencia.RESPOSTA)
        assertThat(citacao.mensagem!!.id).isEqualTo(300)
        assertThat(citacao.mensagem!!.conteudos.single().conteudo).isEqualTo("pergunta")

        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 501, conversaId = 42)
        coEvery { api.mensagens(42, 501, 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()

        coVerify {
            api.enviarMensagem(
                EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "resposta")), mensagemReferencia = ReferenciaEnvioDto(1, 300)),
            )
        }
    }

    @Test
    fun `sem rede fica na fila sem contar tentativa e na ordem`() = runTest {
        val envio = envio()
        val primeira = envio.enviarTexto(42, "1")
        val segunda = envio.enviarTexto(42, "2")
        assertThat(segunda).isLessThan(primeira)

        coEvery { api.enviarMensagem(any()) } throws IOException("sem rede")
        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.TENTAR_DEPOIS)
        coVerify(exactly = 1) { api.enviarMensagem(any()) }
        assertThat(banco.envioPendenteDao().todos().map { it.tentativas }).containsExactly(0, 0)
        assertThat(banco.mensagemDao().buscar(primeira)!!.mensagem.falhou).isFalse()
    }

    @Test
    fun `recusa do servidor marca falhou e segue com as outras, reenviar e apagar`() = runTest {
        val envio = envio()
        val ruim = envio.enviarTexto(42, "ruim")
        val boa = envio.enviarTexto(42, "boa")
        coEvery { api.enviarMensagem(match { it.conteudos.single().conteudo == "ruim" }) } throws
            HttpException(Response.error<Any>(403, """{"error":"Acesso negado!"}""".toResponseBody(null)))
        coEvery { api.enviarMensagem(match { it.conteudos.single().conteudo == "boa" }) } returns
            MensagemCriadaDto(id = 600, conversaId = 42)
        coEvery { api.mensagens(42, 600, 0, 0) } throws IOException("caiu")

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        assertThat(banco.mensagemDao().buscar(ruim)!!.mensagem.falhou).isTrue()
        // A real não veio: a otimista vira a real com o id do servidor.
        assertThat(banco.mensagemDao().buscar(boa)).isNull()
        assertThat(banco.mensagemDao().buscar(600)!!.conteudos.single().conteudo).isEqualTo("boa")

        envio.reenviar(ruim)
        assertThat(banco.mensagemDao().buscar(ruim)!!.mensagem.falhou).isFalse()
        envio.descartar(ruim)
        assertThat(banco.mensagemDao().buscar(ruim)).isNull()
        assertThat(banco.envioPendenteDao().todos()).isEmpty()
    }

    @Test
    fun `erro do servidor conta tentativas e desiste na quinta`() = runTest {
        val envio = envio()
        val id = envio.enviarTexto(42, "x")
        coEvery { api.enviarMensagem(any()) } throws ErroApi.ServidorIndisponivel(502)

        repeat(EnvioMensagens.MAX_TENTATIVAS - 1) {
            assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.TENTAR_DEPOIS)
        }
        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isFalse()
        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isTrue()
    }

    // --- Anexos (4.2) ---

    private fun fonte(nome: String, tamanho: Long) = object : com.conversa.app.core.data.anexos.FonteArquivo {
        override val nome = nome
        override val tamanho = tamanho
        override val mime = null

        override fun abrir() = java.io.ByteArrayInputStream(ByteArray(tamanho.toInt()))
    }

    @Test
    fun `anexos sobem antes, o identificador fica gravado e a ordem e texto e depois os arquivos`() = runTest {
        val envio = envio()
        val a1 = AnexoLocal("content://x/1", "foto.jpg", 100, "image/jpeg", TipoConteudo.IMAGEM)
        val a2 = AnexoLocal("content://x/2", "doc.pdf", 300, "application/pdf", TipoConteudo.ARQUIVO)
        val id = envio.enviar(42, "veja", listOf(a1, a2))

        val otimista = banco.mensagemDao().buscar(id)!!.conteudos.sortedBy { it.ordem }
        assertThat(otimista.map { it.conteudo }).containsExactly("veja", "local:content://x/1", "local:content://x/2").inOrder()

        every { fontes.abrir("content://x/1") } returns fonte("foto.jpg", 100)
        every { fontes.abrir("content://x/2") } returns fonte("doc.pdf", 300)
        every { fontes.liberar(any()) } just Runs
        coEvery { anexos.enviar(match { it.nome == "foto.jpg" }, TipoConteudo.IMAGEM, any()) } returns
            Result.success(AnexoEnviado(1, "hash1", TipoConteudo.IMAGEM, "foto.jpg", "jpg", 100))
        coEvery { anexos.enviar(match { it.nome == "doc.pdf" }, TipoConteudo.ARQUIVO, any()) } returns
            Result.failure(ErroApi.SemConexao(IOException("caiu")))

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.TENTAR_DEPOIS)
        coVerify(exactly = 0) { api.enviarMensagem(any()) }
        assertThat(banco.envioPendenteDao().todos().single().payloadJson).contains("hash1")
        // Ainda na fila: o acesso aos arquivos continua.
        verify(exactly = 0) { fontes.liberar(any()) }

        coEvery { anexos.enviar(match { it.nome == "doc.pdf" }, TipoConteudo.ARQUIVO, any()) } returns
            Result.success(AnexoEnviado(2, "hash2", TipoConteudo.ARQUIVO, "doc.pdf", "pdf", 300))
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 700, conversaId = 42)
        coEvery { api.mensagens(42, 700, 0, 0) } throws IOException("caiu")

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        coVerify(exactly = 1) { anexos.enviar(match { it.nome == "foto.jpg" }, any(), any()) }
        coVerify {
            api.enviarMensagem(
                EnviarMensagemRequisicao(
                    42,
                    listOf(ConteudoEnvioDto(1, 1, "veja"), ConteudoEnvioDto(2, 2, "hash1"), ConteudoEnvioDto(3, 3, "hash2")),
                ),
            )
        }
        // Sem a real, a otimista vira a real com os identificadores no lugar dos local:
        assertThat(banco.mensagemDao().buscar(700)!!.conteudos.sortedBy { it.ordem }.map { it.conteudo })
            .containsExactly("veja", "hash1", "hash2").inOrder()
        assertThat(envio.progresso.value).isEmpty()
        // Enviada: devolve o acesso aos dois arquivos.
        verify { fontes.liberar("content://x/1") }
        verify { fontes.liberar("content://x/2") }
    }

    @Test
    fun `arquivo usado por outra mensagem da fila so e liberado quando a ultima sai`() = runTest {
        val envio = envio()
        val foto = AnexoLocal("content://x/1", "foto.jpg", 100, "image/jpeg", TipoConteudo.IMAGEM)
        val primeira = envio.enviar(42, "", listOf(foto))
        val segunda = envio.enviar(42, "de novo", listOf(foto))
        every { fontes.liberar(any()) } just Runs

        envio.descartar(primeira)
        verify(exactly = 0) { fontes.liberar(any()) }

        envio.descartar(segunda)
        verify(exactly = 1) { fontes.liberar("content://x/1") }
    }

    @Test
    fun `tirar da fila do campo libera, a nao ser que uma mensagem na fila use o arquivo`() = runTest {
        val envio = envio()
        every { fontes.liberar(any()) } just Runs
        envio.enviar(42, "", listOf(AnexoLocal("content://x/1", "foto.jpg", 100, "image/jpeg", TipoConteudo.IMAGEM)))

        envio.desistirDoArquivo("content://x/1")
        envio.desistirDoArquivo("content://x/2")

        verify(exactly = 0) { fontes.liberar("content://x/1") }
        verify(exactly = 1) { fontes.liberar("content://x/2") }
    }

    @Test
    fun `sem acesso ao arquivo a mensagem falha e nao manda nada`() = runTest {
        val envio = envio()
        val id = envio.enviar(42, "", listOf(AnexoLocal("content://sumiu", "a.jpg", 10, null, TipoConteudo.IMAGEM)))
        every { fontes.abrir(any()) } returns null

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)
        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isTrue()
        coVerify(exactly = 0) { api.enviarMensagem(any()) }
    }

    // --- Ir para a mensagem (7.5) ---

    @Test
    fun `trazer ate a mensagem volta de 99 em 99 sem deixar buraco`() = runTest {
        val repo = repo()
        banco.mensagemDao().salvarCompletas((300L..309L).map { msg(it).paraEntidade() })
        coEvery { api.mensagens(42, 300, 99, 0) } returns (201L..300L).map { msg(it) }
        coEvery { api.mensagens(42, 201, 99, 0) } returns (102L..201L).map { msg(it) }

        assertThat(repo.trazerAte(42, 305).getOrThrow()).isTrue()
        assertThat(repo.trazerAte(42, 150).getOrThrow()).isTrue()

        // Já estava: nem pede. Depois de duas páginas, tudo de 102 a 309 está no aparelho.
        coVerify(exactly = 1) { api.mensagens(42, 300, 99, 0) }
        assertThat((102L..309L).all { banco.mensagemDao().buscar(it) != null }).isTrue()
    }

    @Test
    fun `trazer ate a mensagem que nao existe para no comeco da conversa e falha sem rede`() = runTest {
        val repo = repo()
        banco.mensagemDao().salvarCompletas(listOf(msg(300).paraEntidade()))
        coEvery { api.mensagens(42, 300, 99, 0) } returns listOf(msg(300))

        assertThat(repo.trazerAte(42, 7).getOrThrow()).isFalse()

        coEvery { api.mensagens(42, 300, 99, 0) } throws java.io.IOException("sem rede")
        assertThat(repo.trazerAte(42, 7).isFailure).isTrue()
    }

    // --- Reagir e ocultar (etapa 7) ---

    private fun repo() = MensagensRepositorio(
        api,
        banco.mensagemDao(),
        banco.conversaDao(),
        backgroundScopeFalso,
        Clock.fixed(agora, ZoneOffset.UTC),
    )

    private val backgroundScopeFalso = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @Test
    fun `reacoes ficam na ordem do servidor, nao na do emoji`() = runTest {
        val doServidor = msg(1).copy(
            reacoes = listOf("🤩", "👍", "❤️").map { ReacaoDto(it, 1, false, listOf(UsuarioReacaoDto(8, "Bruno"))) },
        )
        banco.mensagemDao().salvarCompletas(listOf(doServidor.paraEntidade()))

        assertThat(banco.mensagemDao().buscar(1)!!.paraModelo().reacoes.map { it.emoji }).containsExactly("🤩", "👍", "❤️").inOrder()

        // A otimista entra no fim, como o servidor fará (a primeira reação do emoji é a mais nova).
        coEvery { api.reagir(any()) } returns ReacaoResposta(1, "😂", "add")
        coEvery { api.mensagens(42, 1, 0, 0) } throws java.io.IOException("sem rede")
        repo().reagir(42, 1, "😂", 7, "Ana Souza")

        assertThat(banco.mensagemDao().buscar(1)!!.paraModelo().reacoes.map { it.emoji }).containsExactly("🤩", "👍", "❤️", "😂").inOrder()
    }

    @Test
    fun `reagir mostra na hora e depois fica igual ao servidor`() = runTest {
        banco.mensagemDao().salvarCompletas(listOf(msg(1).paraEntidade()))
        var vistaDuranteOEnvio: List<com.conversa.app.core.model.Reacao> = emptyList()
        coEvery { api.reagir(any()) } coAnswers {
            vistaDuranteOEnvio = banco.mensagemDao().buscar(1)!!.paraModelo().reacoes
            ReacaoResposta(1, "👍", "add")
        }
        val doServidor = msg(1).copy(
            reacoes = listOf(ReacaoDto("👍", 2, true, listOf(UsuarioReacaoDto(8, "Bruno"), UsuarioReacaoDto(7, "Ana Souza")))),
        )
        coEvery { api.mensagens(42, 1, 0, 0) } returns listOf(msg(0), doServidor)

        val resultado = repo().reagir(42, 1, "👍", 7, "Ana Souza")

        assertThat(resultado.isSuccess).isTrue()
        assertThat(vistaDuranteOEnvio.single().let { it.emoji to it.reagiu }).isEqualTo("👍" to true)
        val reacao = banco.mensagemDao().buscar(1)!!.paraModelo().reacoes.single()
        assertThat(reacao.quantidade).isEqualTo(2)
        assertThat(reacao.usuarios.map { it.nome }).containsExactly("Bruno", "Ana Souza")
        coVerify { api.reagir(ReacaoRequisicao(1, "👍")) }
    }

    @Test
    fun `reagir que falha volta ao que o servidor tem`() = runTest {
        banco.mensagemDao().salvarCompletas(listOf(msg(1).paraEntidade()))
        coEvery { api.reagir(any()) } throws java.io.IOException("sem rede")
        coEvery { api.mensagens(42, 1, 0, 0) } throws java.io.IOException("sem rede")

        val resultado = repo().reagir(42, 1, "👍", 7, "Ana Souza")

        assertThat(resultado.isFailure).isTrue()
        // Sem rede nem para reler: fica a otimista; a próxima carga corrige.
        assertThat(banco.mensagemDao().buscar(1)!!.paraModelo().reacoes.single().reagiu).isTrue()
    }

    @Test
    fun `ocultar marca como oculta e a agendada que nao saiu some`() = runTest {
        banco.mensagemDao().salvarCompletas(listOf(msg(1).paraEntidade(), msg(2).paraEntidade()))
        coEvery { api.ocultarMensagem(1) } returns MensagemExcluidaDto(1, 42, agora)
        coEvery { api.ocultarMensagem(2) } returns MensagemExcluidaDto(2, 42, null)

        assertThat(repo().ocultar(1).isSuccess).isTrue()
        assertThat(repo().ocultar(2).isSuccess).isTrue()

        assertThat(banco.mensagemDao().buscar(1)!!.paraModelo().oculta).isTrue()
        assertThat(banco.mensagemDao().buscar(2)).isNull()
    }
}
