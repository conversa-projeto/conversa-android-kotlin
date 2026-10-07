package com.conversa.app.feature.chat

import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.anexos.ArquivosLocais
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.media.EstadoAudio
import com.conversa.app.core.media.GravadorAudio
import com.conversa.app.core.media.PlayerAudio
import com.conversa.app.core.model.TipoConteudo
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Gravação de áudio (4.6): trechos, pausa, ouvir, enviar como tipo 5, descartar e "gravando". */
class GravacaoTest {
    private var agora = Instant.parse("2026-10-07T12:00:00Z")
    private val relogio = object : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?) = this

        override fun instant(): Instant = agora
    }
    private val pastaBase: File = Files.createTempDirectory("gravacoes").toFile()
    private val gravador = GravadorFalso()
    private val player = PlayerFalso()
    private val envio = mockk<EnvioMensagens>(relaxed = true)
    private val mensagens = mockk<MensagensRepositorio>(relaxed = true)
    private val arquivos = mockk<ArquivosLocais> {
        every { novaPastaGravacao() } answers { File(pastaBase, "g${System.nanoTime()}").apply { mkdirs() } }
        every { uriCompartilhado(any()) } answers { "content://app.arquivos/gravacoes/" + firstArg<File>().name }
    }
    private val avisos = mutableListOf<EventoChat>()
    private val juntados = mutableListOf<List<String>>()

    private fun TestScope.controle() = ControleGravacao(
        escopo = backgroundScope,
        conversaId = 42,
        gravador = gravador,
        arquivos = arquivos,
        envio = envio,
        mensagens = mensagens,
        player = player,
        relogio = relogio,
        es = StandardTestDispatcher(testScheduler),
        avisar = { avisos += it },
        juntar = { trechos, destino ->
            juntados += trechos.map { it.name }
            destino.writeText(trechos.joinToString("+") { it.readText() })
            true
        },
    )

    private fun passar(ms: Long) {
        agora = agora.plusMillis(ms)
    }

    @Test
    fun `segurar e soltar envia uma gravacao tipo 5 em m4a e para o aviso de gravando`() = runTest {
        val controle = controle()
        controle.iniciar()
        runCurrent()
        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Gravando(0, travada = false))
        coVerify(exactly = 1) { mensagens.avisarGravando(42) }

        passar(1_500)
        advanceTimeBy(ControleGravacao.INTERVALO_AVISO_MS + 10)
        coVerify(exactly = 2) { mensagens.avisarGravando(42) }
        assertThat((controle.estado.value as EstadoGravacao.Gravando).duracaoMs).isEqualTo(1_500)

        val anexos = slot<List<AnexoLocal>>()
        coEvery { envio.enviar(42, "", capture(anexos)) } returns -1
        controle.enviar()
        runCurrent()

        val anexo = anexos.captured.single()
        assertThat(anexo.tipo).isEqualTo(TipoConteudo.GRAVACAO_AUDIO)
        assertThat(anexo.mime).isEqualTo("audio/mp4")
        assertThat(anexo.nome).matches("audio-\\d+\\.m4a")
        assertThat(anexo.uri).startsWith("content://")
        assertThat(juntados.single()).containsExactly("trecho-0.m4a")
        assertThat(avisos).containsExactly(EventoChat.RolarAoFim)
        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Parada)

        // Fechou: não avisa mais "gravando".
        advanceTimeBy(10_000)
        coVerify(exactly = 2) { mensagens.avisarGravando(42) }
    }

    @Test
    fun `menos de 1 s nao envia e apaga os arquivos`() = runTest {
        val controle = controle()
        controle.iniciar()
        passar(500)
        controle.enviar()
        runCurrent()

        coVerify(exactly = 0) { envio.enviar(any(), any(), any()) }
        assertThat(avisos).containsExactly(EventoChat.GravacaoCurta)
        assertThat(pastaBase.listFiles()!!.flatMap { it.listFiles()!!.toList() }).isEmpty()
    }

    @Test
    fun `pausar, ouvir, continuar e enviar junta os trechos em ordem`() = runTest {
        val controle = controle()
        controle.iniciar()
        passar(1_000)
        controle.pausar()
        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Pausada(1_000, "42:gravacao"))
        // Pausada continua avisando "gravando" (a gravação está aberta, como no web).
        advanceTimeBy(ControleGravacao.INTERVALO_AVISO_MS + 10)
        coVerify(exactly = 2) { mensagens.avisarGravando(42) }

        controle.ouvir()
        runCurrent()
        assertThat(player.estado.value.chave).isEqualTo("42:gravacao")
        assertThat(juntados.last()).containsExactly("trecho-0.m4a")

        controle.continuar()
        assertThat(player.estado.value.chave).isNull()
        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Gravando(1_000, travada = true))
        passar(1_500)
        controle.enviar()
        runCurrent()

        assertThat(juntados.last()).containsExactly("trecho-0.m4a", "trecho-1.m4a").inOrder()
        coVerify(exactly = 1) { envio.enviar(42, "", any()) }
    }

    @Test
    fun `trecho sem dados e jogado fora e nao conta tempo`() = runTest {
        val controle = controle()
        controle.iniciar()
        passar(2_000)
        controle.pausar()
        controle.continuar()
        gravador.proximoParadaValida = false
        passar(100)
        controle.pausar()

        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Pausada(2_000, "42:gravacao"))
        controle.enviar()
        runCurrent()
        assertThat(juntados.last()).containsExactly("trecho-0.m4a")
    }

    @Test
    fun `descartar apaga tudo e nada e enviado`() = runTest {
        val controle = controle()
        controle.iniciar()
        passar(3_000)
        controle.descartar()
        runCurrent()

        assertThat(gravador.gravando).isFalse()
        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Parada)
        coVerify(exactly = 0) { envio.enviar(any(), any(), any()) }
        assertThat(pastaBase.listFiles()!!.flatMap { it.listFiles()!!.toList() }).isEmpty()
    }

    @Test
    fun `microfone que nao abre avisa e nao fica gravando`() = runTest {
        gravador.abre = false
        val controle = controle()
        controle.iniciar()
        runCurrent()

        assertThat(controle.estado.value).isEqualTo(EstadoGravacao.Parada)
        assertThat(avisos).containsExactly(EventoChat.MicrofoneIndisponivel)
        coVerify(exactly = 0) { mensagens.avisarGravando(any()) }
    }

    @Test
    fun `comecar a gravar para o audio que estava tocando`() = runTest {
        player.tocar("42:7:1", "file:/x")
        controle().iniciar()
        assertThat(player.estado.value.chave).isNull()
    }

    private class GravadorFalso : GravadorAudio {
        var abre = true
        var gravando = false
        var proximoParadaValida = true

        override fun iniciar(arquivo: File): Boolean {
            if (!abre) return false
            arquivo.writeText(arquivo.name)
            gravando = true
            return true
        }

        override fun parar(): Boolean {
            gravando = false
            return proximoParadaValida.also { proximoParadaValida = true }
        }

        override fun nivel() = 0.5f
    }

    private class PlayerFalso : PlayerAudio {
        override val estado = MutableStateFlow(EstadoAudio())
        override val falhas = MutableSharedFlow<String>()

        override fun tocar(chave: String, uri: String) {
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
