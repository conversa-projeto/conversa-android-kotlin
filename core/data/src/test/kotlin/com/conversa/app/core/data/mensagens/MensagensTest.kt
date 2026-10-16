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
import com.conversa.app.core.model.DestinatarioConfirmacao
import com.conversa.app.core.model.Postagem
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoExibicao
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.classificarMensagem
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.ConcluidasDto
import com.conversa.app.core.network.dto.ConfirmacaoDto
import com.conversa.app.core.network.dto.ConteudoDto
import com.conversa.app.core.network.dto.ConteudoEnvioDto
import com.conversa.app.core.network.dto.EnviarMensagemRequisicao
import com.conversa.app.core.network.dto.MarcarStatusRequisicao
import com.conversa.app.core.network.dto.MensagemCriadaDto
import com.conversa.app.core.network.dto.MensagemDto
import com.conversa.app.core.network.dto.MensagemExcluidaDto
import com.conversa.app.core.network.dto.MensagemIdDto
import com.conversa.app.core.network.dto.PedirConfirmacaoRequisicao
import com.conversa.app.core.network.dto.PostagemDto
import com.conversa.app.core.network.dto.PostagemEnvioDto
import com.conversa.app.core.network.dto.ReacaoDto
import com.conversa.app.core.network.dto.ReacaoRequisicao
import com.conversa.app.core.network.dto.ReacaoResposta
import com.conversa.app.core.network.dto.ReacoesVistasRequisicao
import com.conversa.app.core.network.dto.ReferenciaEnvioDto
import com.conversa.app.core.network.dto.RespostasVistasDto
import com.conversa.app.core.network.dto.SucessoDto
import com.conversa.app.core.network.dto.UsuarioReacaoDto
import com.conversa.app.core.network.dto.VisualizarPostagemRequisicao
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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
    fun `postagem com respostas novas - marca vistas uma vez, desconta as respostas e rele a postagem`() = runTest {
        val escopo = escopoDoTeste()
        val repo = MensagensRepositorio(api, banco.mensagemDao(), banco.conversaDao(), escopo, java.time.Clock.systemUTC())
        banco.conversaDao().salvar(listOf(ConversaEntidade(42, 2, "Grupo", null, null, 160, agora, "resposta", 3, null, null, null)))
        val resposta = msg(160, "resposta", remetente = 9).copy(postagemId = 104)
        val comNovas = msg(104, "aviso").copy(postagem = PostagemDto("Aviso", respostas = 2, naoLidas = 2, ultimas = listOf(resposta)))
        coEvery { api.mensagens(42, 0, 80, 0) } returns listOf(comNovas)
        coEvery { api.visualizarPostagem(VisualizarPostagemRequisicao(104)) } returns RespostasVistasDto(2)
        coEvery { api.mensagens(42, 104, 0, 0) } returns listOf(comNovas.copy(postagem = comNovas.postagem!!.copy(naoLidas = 0)))
        repo.carregarRecentes(42)
        assertThat(repo.observar(42).first().single().postagem!!.ultimas.single().id).isEqualTo(160)

        repo.visualizarPostagem(42, 104, 2, ultimaResposta = 160)
        repo.visualizarPostagem(42, 104, 2, ultimaResposta = 160)
        advanceUntilIdle()

        coVerify(exactly = 1) { api.visualizarPostagem(VisualizarPostagemRequisicao(104)) }
        assertThat(banco.conversaDao().observar(42).first()!!.naoLidas).isEqualTo(1)
        assertThat(repo.observar(42).first().single().postagem!!.naoLidas).isEqualTo(0)

        // Resposta nova (outra resposta mais recente), mesmo com a mesma quantidade de novas: marca de novo.
        repo.visualizarPostagem(42, 104, 2, ultimaResposta = 161)
        advanceUntilIdle()
        coVerify(exactly = 2) { api.visualizarPostagem(VisualizarPostagemRequisicao(104)) }
        escopo.cancel()
    }

    @Test
    fun `marcar como lida pela notificacao marca as respostas das postagens avisadas e desconta o que o servidor marcou`() = runTest {
        val repo = repo()
        banco.conversaDao().salvar(listOf(ConversaEntidade(42, 2, "Grupo", null, null, 160, agora, "resposta", 3, null, null, null)))
        val postagem = msg(104, "aviso").copy(postagem = PostagemDto("Aviso", respostas = 2, naoLidas = 2))
        banco.mensagemDao().salvarCompletas(listOf(postagem.paraEntidade()))
        coEvery { api.visualizarPostagem(VisualizarPostagemRequisicao(104)) } returns RespostasVistasDto(2)
        coEvery { api.visualizarPostagem(VisualizarPostagemRequisicao(105)) } returns RespostasVistasDto(1)
        coEvery { api.mensagens(42, 104, 0, 0) } returns listOf(postagem.copy(postagem = postagem.postagem!!.copy(naoLidas = 0)))

        // 105 não está no aparelho: só marca no servidor.
        repo.marcarPostagensVistas(42, listOf(104, 105, 104))

        coVerify(exactly = 1) { api.visualizarPostagem(VisualizarPostagemRequisicao(104)) }
        coVerify(exactly = 1) { api.visualizarPostagem(VisualizarPostagemRequisicao(105)) }
        coVerify(exactly = 0) { api.mensagens(42, 105, 0, 0) }
        assertThat(banco.conversaDao().observar(42).first()!!.naoLidas).isEqualTo(0)
        assertThat(banco.mensagemDao().buscar(104)!!.paraModelo().postagem!!.naoLidas).isEqualTo(0)
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
    fun `figurinha vai sozinha como tipo 7, ou depois do texto`() = runTest {
        val envio = envio()

        val sozinha = envio.enviar(42, "", emptyList(), figurinha = "basico/coracao")
        val comTexto = envio.enviar(42, "oi", emptyList(), figurinha = "coisas/bolo")

        assertThat(banco.mensagemDao().buscar(sozinha)!!.paraModelo().conteudos.map { it.tipo to it.conteudo })
            .containsExactly(TipoConteudo.FIGURINHA to "basico/coracao")
        assertThat(banco.mensagemDao().buscar(comTexto)!!.paraModelo().conteudos.map { it.ordem to it.conteudo })
            .containsExactly(1 to "oi", 2 to "coisas/bolo").inOrder()
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 700, conversaId = 42)
        coEvery { api.mensagens(42, any(), 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()
        coVerify { api.enviarMensagem(EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 7, "basico/coracao")))) }
    }

    @Test
    fun `agendada vai com visivel_em em ISO UTC e a otimista ordena pela data agendada`() = runTest {
        val envio = envio()
        val quando = java.time.Instant.parse("2027-01-02T11:00:00Z")

        val id = envio.enviar(42, "depois", emptyList(), visivelEm = quando)

        val otimista = banco.mensagemDao().buscar(id)!!.paraModelo()
        assertThat(otimista.visivelEm).isEqualTo(quando)
        assertThat(otimista.dataEfetiva).isEqualTo(quando)
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 701, conversaId = 42)
        coEvery { api.mensagens(42, any(), 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()
        coVerify {
            api.enviarMensagem(EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "depois")), visivelEm = "2027-01-02T11:00:00Z"))
        }
    }

    @Test
    fun `encaminhada leva os conteudos da original antes do texto e pode ir sem texto`() = runTest {
        val envio = envio()
        val original = msg(300, "olha isto", remetente = 8).copy(
            conteudos = listOf(
                ConteudoDto(1, 1, 1, "olha isto"),
                ConteudoDto(2, 2, 2, "img-9", nome = "foto.jpg", extensao = "jpg"),
            ),
        ).paraModelo()

        val semTexto = envio.enviar(50, "", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original))
        val comTexto = envio.enviar(50, "veja", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, original))

        val otimista = banco.mensagemDao().buscar(semTexto)!!.paraModelo()
        assertThat(otimista.conteudos.map { it.tipo to it.conteudo }).containsExactly(
            TipoConteudo.TEXTO to "olha isto",
            TipoConteudo.IMAGEM to "img-9",
        ).inOrder()
        assertThat(otimista.referencia!!.tipo).isEqualTo(TipoReferencia.ENCAMINHAMENTO)
        assertThat(
            banco.mensagemDao().buscar(comTexto)!!.paraModelo().conteudos.map {
                it.conteudo
            },
        ).containsExactly("olha isto", "img-9", "veja").inOrder()

        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 600, conversaId = 50)
        coEvery { api.mensagens(50, any(), 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()
        coVerify {
            api.enviarMensagem(
                EnviarMensagemRequisicao(
                    50,
                    listOf(ConteudoEnvioDto(1, 1, "olha isto"), ConteudoEnvioDto(2, 2, "img-9"), ConteudoEnvioDto(3, 1, "veja")),
                    mensagemReferencia = ReferenciaEnvioDto(2, 300),
                ),
            )
        }
    }

    // --- Reações vistas (servidor ef13c22: atividade pendente até concluir) ---

    @Test
    fun `reacoes vistas avisa uma vez por quantidade e de novo se falhar`() = runTest {
        coEvery { api.concluirReacoesVistas(any()) } throws IOException("sem rede") andThen ConcluidasDto(1)
        val minha = msg(900, remetente = 1).copy(reacoes = listOf(ReacaoDto("👍", quantidade = 2, reagiu = true))).paraModelo()
        val semDeOutros = msg(901, remetente = 1).copy(reacoes = listOf(ReacaoDto("👍", quantidade = 1, reagiu = true))).paraModelo()
        val r = repo()

        r.reacoesVistas(listOf(minha, semDeOutros))
        r.reacoesVistas(listOf(minha))
        r.reacoesVistas(listOf(minha))
        // Mais uma reação: avisa de novo.
        r.reacoesVistas(listOf(minha.copy(reacoes = minha.reacoes.map { it.copy(quantidade = 3) })))

        coVerify(exactly = 3) { api.concluirReacoesVistas(ReacoesVistasRequisicao(listOf(900))) }
    }

    // --- Chat da ligação só leitura (FC-727) ---

    @Test
    fun `chat da ligacao vem do servidor sem gravar no Room e o conteudo guarda o chat`() = runTest {
        coEvery { api.mensagens(15, 0, 100, 0) } returns listOf(msg(900, "combinado").copy(conversaId = 15))

        val lista = repo().chatDaChamada(15).getOrThrow()

        assertThat(lista.map { it.id }).containsExactly(900L)
        assertThat(banco.mensagemDao().buscar(900)).isNull()

        // A mensagem da ligação guarda no Room o chat dela (para a bolha abrir).
        val ligacao = msg(901).copy(conteudos = listOf(ConteudoDto(id = 1, ordem = 1, tipo = 6, conteudo = "{}", chatChamadaId = 15)))
        banco.mensagemDao().salvarCompletas(listOf(ligacao.paraEntidade()))
        assertThat(banco.mensagemDao().buscar(901)!!.paraModelo().conteudos.single().chatChamadaId).isEqualTo(15)
    }

    // --- Confirmação de leitura (FC-521, FC-525) ---

    @Test
    fun `pedir confirmacao no envio manda a lista e a otimista ja aguarda os destinatarios`() = runTest {
        val envio = envio()
        val pedido = PedidoConfirmacao(listOf(8), listOf(DestinatarioConfirmacao(8, "Bruno")))

        val idLocal = envio.enviar(42, "leia", emptyList(), confirmacao = pedido)

        val confirmacao = banco.mensagemDao().buscar(idLocal)!!.paraModelo().confirmacao!!
        assertThat(confirmacao.total).isEqualTo(1)
        assertThat(confirmacao.destinatarios.single().nome).isEqualTo("Bruno")
        assertThat(confirmacao.usuarios).isEmpty()
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 520, conversaId = 42)
        coEvery { api.mensagens(42, 520, 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()
        coVerify {
            api.enviarMensagem(
                EnviarMensagemRequisicao(
                    42,
                    listOf(ConteudoEnvioDto(1, 1, "leia")),
                    pedeConfirmacao = true,
                    confirmacaoUsuarios = listOf(8),
                ),
            )
        }
    }

    @Test
    fun `confirmar a leitura e pedir confirmacao depois chamam o servidor e releem a mensagem`() = runTest {
        val repo = repo()
        val pede = msg(300, "leia").copy(confirmacao = ConfirmacaoDto(total = 1, podeConfirmar = true))
        banco.mensagemDao().salvarCompletas(listOf(pede.paraEntidade()))
        coEvery { api.confirmarLeitura(MensagemIdDto(300)) } returns MensagemIdDto(300)
        coEvery { api.mensagens(42, 300, 0, 0) } returns listOf(pede.copy(confirmacao = ConfirmacaoDto(total = 1, confirmou = true)))

        assertThat(repo.confirmarLeitura(42, 300).isSuccess).isTrue()
        assertThat(banco.mensagemDao().buscar(300)!!.paraModelo().confirmacao!!.confirmou).isTrue()

        // Fora da lista: o 403 do servidor volta como falha.
        coEvery { api.confirmarLeitura(MensagemIdDto(301)) } throws HttpException(
            Response.error<Any>(403, """{"error":"Você não está entre quem precisa confirmar esta mensagem."}""".toResponseBody()),
        )
        assertThat(repo.confirmarLeitura(42, 301).isFailure).isTrue()

        val minha = msg(302, "aviso", remetente = 7)
        banco.mensagemDao().salvarCompletas(listOf(minha.paraEntidade()))
        coEvery { api.pedirConfirmacao(PedirConfirmacaoRequisicao(302, null)) } returns ConfirmacaoDto(total = 2)
        coEvery { api.mensagens(42, 302, 0, 0) } returns listOf(minha.copy(confirmacao = ConfirmacaoDto(total = 2)))
        assertThat(repo.pedirConfirmacao(42, 302, null).isSuccess).isTrue()
        assertThat(banco.mensagemDao().buscar(302)!!.paraModelo().confirmacao!!.total).isEqualTo(2)
    }

    // --- Postagem em grupo (7.13) ---

    @Test
    fun `postagem nova vai com o titulo e a otimista ja e uma postagem sem respostas`() = runTest {
        val envio = envio()

        val idLocal = envio.enviar(42, "Pauta no anexo", emptyList(), postagem = PostagemEnvio.Nova("Reunião"))

        val otimista = banco.mensagemDao().buscar(idLocal)!!.paraModelo()
        assertThat(otimista.postagem).isEqualTo(Postagem("Reunião", 0, 0, emptyList()))
        assertThat(classificarMensagem(otimista)).isEqualTo(TipoExibicao.POSTAGEM)
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 510, conversaId = 42)
        coEvery { api.mensagens(42, 510, 0, 0) } throws java.io.IOException("sem rede")
        envio.processarPendentes()
        coVerify {
            api.enviarMensagem(
                EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "Pauta no anexo")), postagem = PostagemEnvioDto("Reunião")),
            )
        }
    }

    @Test
    fun `resposta de postagem fica embaixo dela ate sair, vai com postagem_id e rele a postagem`() = runTest {
        val envio = envio()
        val repo = repo()
        val postagem = msg(104, "aviso").copy(postagem = PostagemDto("Aviso"))
        banco.mensagemDao().salvarCompletas(listOf(postagem.paraEntidade()))

        val idLocal = envio.enviar(42, "eu vou", emptyList(), postagem = PostagemEnvio.Resposta(104))

        // Fora do fluxo do chat: só embaixo da postagem, enquanto sai.
        assertThat(repo.observar(42).first().map { it.id }).containsExactly(104L)
        assertThat(repo.observarRespostasLocais(42).first()[104L]!!.single().id).isEqualTo(idLocal)

        val resposta = msg(511, "eu vou", remetente = 7).copy(postagemId = 104)
        coEvery { api.enviarMensagem(any()) } returns MensagemCriadaDto(id = 511, conversaId = 42)
        coEvery { api.mensagens(42, 104, 0, 0) } returns
            listOf(postagem.copy(postagem = PostagemDto("Aviso", respostas = 1, ultimas = listOf(resposta))))
        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)

        coVerify { api.enviarMensagem(EnviarMensagemRequisicao(42, listOf(ConteudoEnvioDto(1, 1, "eu vou")), postagemId = 104)) }
        assertThat(banco.mensagemDao().buscar(idLocal)).isNull()
        // A resposta não vira linha do chat: chega nas últimas da postagem.
        assertThat(banco.mensagemDao().buscar(511)).isNull()
        assertThat(repo.observarRespostasLocais(42).first()).isEmpty()
        val ultimas = repo.observar(42).first().single().postagem!!.ultimas
        assertThat(ultimas.map { it.id to it.postagemId }).containsExactly(511L to 104L)
    }

    @Test
    fun `ver todas as respostas vem do servidor, da mais antiga a mais nova, sem gravar no Room`() = runTest {
        coEvery { api.respostasPostagem(104) } returns
            listOf(msg(160, "primeira").copy(postagemId = 104), msg(161, "segunda").copy(postagemId = 104))

        val respostas = repo().respostas(104).getOrThrow()

        assertThat(respostas.map { it.id }).containsExactly(160L, 161L).inOrder()
        assertThat(banco.mensagemDao().buscar(160)).isNull()
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

    @Test
    fun `403 sem a permissao - a bolha falha e o motivo do servidor vai para a conversa`() = runTest {
        val envio = envio()
        val id = envio.enviarTexto(42, "oi 😀")
        val motivo = "Você não tem permissão para: Enviar emoji."
        coEvery { api.enviarMensagem(any()) } throws ErroApi.Servidor(403, motivo)
        val recusa = async(start = CoroutineStart.UNDISPATCHED) { envio.recusas.first() }

        assertThat(envio.processarPendentes()).isEqualTo(ResultadoEnvio.CONCLUIDO)

        assertThat(banco.mensagemDao().buscar(id)!!.mensagem.falhou).isTrue()
        assertThat(recusa.await()).isEqualTo(RecusaEnvio(42, motivo))
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

    @Test
    fun `pesquisar devolve as mais recentes primeiro, inclusive do chat de uma ligacao, sem gravar no Room`() = runTest {
        // A 5 é do chat de uma ligação feita nesta conversa (servidor 6d20ff7): não se filtra por conversa.
        coEvery { api.pesquisar("bolo", 42) } returns listOf(msg(3, "bolo de cenoura"), msg(9, "mais bolo"), msg(5).copy(conversaId = 7))

        val resultados = repo().pesquisar(" bolo ", 42).getOrThrow()

        assertThat(resultados.map { it.id }).containsExactly(9L, 5L, 3L).inOrder()
        assertThat(banco.mensagemDao().buscar(9)).isNull()
    }
}
