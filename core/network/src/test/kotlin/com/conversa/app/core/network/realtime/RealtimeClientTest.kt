package com.conversa.app.core.network.realtime

import com.conversa.app.core.network.config.ServerConfig
import com.conversa.app.core.network.config.ServerConfigProvider
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Before
import org.junit.Test

/** WebSocket real contra o MockWebServer: login, eventos, reconexão e socket único. */
class RealtimeClientTest {
    private val servidor = MockWebServer()
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recebidas = LinkedBlockingQueue<String>()
    private val aberturas = AtomicInteger()
    private val socketsServidor = LinkedBlockingQueue<WebSocket>()
    private val todosSocketsServidor = java.util.concurrent.CopyOnWriteArrayList<WebSocket>()
    private val okHttp = OkHttpClient()
    private lateinit var cliente: RealtimeClient

    private val ouvinteServidor = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            aberturas.incrementAndGet()
            socketsServidor.add(webSocket)
            todosSocketsServidor.add(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            recebidas.add(text)
        }
    }

    private fun aceitarSocket() = servidor.enqueue(MockResponse.Builder().webSocketUpgrade(ouvinteServidor).build())

    @Before
    fun preparar() {
        servidor.start()
        val config = MutableStateFlow(ServerConfig.aPartirDe(servidor.url("/").toString()))
        val provedor = object : ServerConfigProvider {
            override val atual = config
        }
        cliente = RealtimeClient(okHttp, provedor, escopo, esperaReconexao = { 50L })
    }

    @After
    fun encerrar() {
        cliente.desconectar()
        escopo.cancel()
        todosSocketsServidor.forEach { runCatching { it.close(1000, null) } }
        okHttp.dispatcher.executorService.shutdownNow()
        okHttp.connectionPool.evictAll()
        servidor.close()
    }

    @Test
    fun `conecta em ws, envia o login e repassa eventos`() = runBlocking {
        aceitarSocket()
        cliente.conectar("tok")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }

        assertThat(recebidas.poll(5, TimeUnit.SECONDS)).isEqualTo("""{"tipo":1,"token":"tok"}""")
        assertThat(servidor.takeRequest().url.encodedPath).isEqualTo("/ws/")

        val primeiro = escopo.kotlinx_async { cliente.eventos.first() }
        socketsServidor.poll(5, TimeUnit.SECONDS)!!.send("""{"tipo":61}""")
        assertThat(withTimeout(5_000) { primeiro.await() }).isEqualTo(EventoSocket.NovaAtividade)
    }

    @Test
    fun `reconecta sozinho quando o servidor fecha`() = runBlocking {
        aceitarSocket()
        aceitarSocket()
        cliente.conectar("tok")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }
        socketsServidor.poll(5, TimeUnit.SECONDS)!!.close(1001, "reinício")
        withTimeout(5_000) {
            while (aberturas.get() < 2) kotlinx.coroutines.delay(20)
        }
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }
        assertThat(aberturas.get()).isEqualTo(2)
    }

    @Test
    fun `login recusado nao fica reconectando`() = runBlocking {
        aceitarSocket()
        aceitarSocket()
        cliente.conectar("velho")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }
        socketsServidor.poll(5, TimeUnit.SECONDS)!!.send("""{"tipo":0,"message":"Token inválido ou expirado"}""")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.DESCONECTADO } }
        kotlinx.coroutines.delay(300)
        assertThat(aberturas.get()).isEqualTo(1)
    }

    @Test
    fun `conectar de novo com o mesmo token nao abre outro socket`() = runBlocking {
        aceitarSocket()
        aceitarSocket()
        cliente.conectar("tok")
        cliente.conectar("tok")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }
        kotlinx.coroutines.delay(300)
        assertThat(aberturas.get()).isEqualTo(1)
    }

    @Test
    fun `desconectar fecha e para de tentar`() = runBlocking {
        aceitarSocket()
        cliente.conectar("tok")
        withTimeout(5_000) { cliente.estado.first { it == EstadoConexao.CONECTADO } }
        cliente.desconectar()
        assertThat(cliente.estado.value).isEqualTo(EstadoConexao.DESCONECTADO)
        assertThat(cliente.enviarSinal(10, kotlinx.serialization.json.JsonObject(emptyMap()))).isFalse()
    }

    @Test
    fun `espera de reconexao cresce ate 30 s`() {
        assertThat(RealtimeClient.esperaPadrao(0)).isIn(com.google.common.collect.Range.closed(1_000L, 1_200L))
        assertThat(RealtimeClient.esperaPadrao(3)).isIn(com.google.common.collect.Range.closed(8_000L, 9_600L))
        assertThat(RealtimeClient.esperaPadrao(20)).isIn(com.google.common.collect.Range.closed(30_000L, 36_000L))
    }
}

private fun <T> CoroutineScope.kotlinx_async(bloco: suspend () -> T) = async(start = CoroutineStart.UNDISPATCHED) { bloco() }
