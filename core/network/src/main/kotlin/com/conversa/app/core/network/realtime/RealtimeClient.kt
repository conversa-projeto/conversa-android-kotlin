package com.conversa.app.core.network.realtime

import com.conversa.app.core.network.config.ServerConfigProvider
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber

enum class EstadoConexao {
    /** Sem sessão, sem servidor ou desligado de propósito (app em segundo plano). */
    DESCONECTADO,
    CONECTANDO,

    /** Socket aberto e login enviado. O servidor não confirma o login (contrato §6.2). */
    CONECTADO,

    /** Caiu; esperando para tentar de novo. */
    AGUARDANDO,
}

/**
 * Cliente do WebSocket do Conversa (`<base>/ws/`).
 *
 * - Um único socket ativo; cada conexão tem um número de geração e callbacks de
 *   sockets antigos são ignorados (evita sockets duplicados, problema #16 do app legado).
 * - Vários assinantes podem ouvir [eventos] ao mesmo tempo (problemas #1/#2 do legado).
 * - Reconecta sozinho com espera crescente (1 s → 30 s, com variação) enquanto
 *   houver um token desejado. Quem decide quando conectar é a camada de dados.
 * - Nunca registra o token nem o conteúdo das mensagens em log.
 */
class RealtimeClient(
    okHttp: OkHttpClient,
    private val config: ServerConfigProvider,
    private val escopo: CoroutineScope,
    private val esperaReconexao: (tentativa: Int) -> Long = ::esperaPadrao,
) {
    private val cliente = okHttp

    private val _estado = MutableStateFlow(EstadoConexao.DESCONECTADO)
    val estado: StateFlow<EstadoConexao> = _estado.asStateFlow()

    private val _eventos = MutableSharedFlow<EventoSocket>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val eventos: SharedFlow<EventoSocket> = _eventos.asSharedFlow()

    private val trava = Any()
    private var geracao = 0
    private var socket: WebSocket? = null
    private var tokenDesejado: String? = null
    private var tentativas = 0
    private var reconexao: Job? = null

    /** Liga (ou troca o token). Chamar de novo com o mesmo token não reabre o socket. */
    fun conectar(token: String) {
        synchronized(trava) {
            if (token == tokenDesejado && socket != null) return
            tokenDesejado = token
            tentativas = 0
            abrirTravado()
        }
    }

    /** Desliga e não tenta reconectar. */
    fun desconectar() {
        synchronized(trava) {
            tokenDesejado = null
            reconexao?.cancel()
            reconexao = null
            geracao++
            socket?.close(CODIGO_NORMAL, "desconectado")
            socket = null
            _estado.value = EstadoConexao.DESCONECTADO
        }
    }

    /** Reabre agora (ex.: a rede voltou ou o servidor mudou). */
    fun reconectarAgora() {
        synchronized(trava) {
            if (tokenDesejado == null) return
            tentativas = 0
            abrirTravado()
        }
    }

    /** Sinal da chamada (tipo 57). Devolve `false` se o socket não está aberto. */
    fun enviarSinal(chamadaId: Long, dados: JsonObject): Boolean {
        val mensagem = buildJsonObject {
            put("tipo", TipoSocket.SINAL_CHAMADA)
            put("chamada_id", chamadaId)
            put("dados", dados)
        }
        return synchronized(trava) { socket?.send(mensagem.toString()) ?: false }
    }

    private fun abrirTravado() {
        reconexao?.cancel()
        reconexao = null
        geracao++
        socket?.close(CODIGO_NORMAL, "reabrindo")
        socket = null

        val url = config.atual.value?.ws
        val token = tokenDesejado
        if (url == null || token == null) {
            _estado.value = EstadoConexao.DESCONECTADO
            return
        }
        _estado.value = EstadoConexao.CONECTANDO
        val minhaGeracao = geracao
        socket = cliente.newWebSocket(Request.Builder().url(url).build(), Ouvinte(minhaGeracao, token))
    }

    private fun agendarReconexaoTravado() {
        if (tokenDesejado == null) {
            _estado.value = EstadoConexao.DESCONECTADO
            return
        }
        _estado.value = EstadoConexao.AGUARDANDO
        val espera = esperaReconexao(tentativas++)
        val minhaGeracao = geracao
        reconexao?.cancel()
        reconexao = escopo.launch {
            delay(espera)
            synchronized(trava) {
                if (geracao == minhaGeracao && tokenDesejado != null) abrirTravado()
            }
        }
    }

    private inner class Ouvinte(private val minhaGeracao: Int, private val token: String) : WebSocketListener() {
        private fun atual() = minhaGeracao == geracao

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(trava) {
                if (!atual()) {
                    webSocket.close(CODIGO_NORMAL, "obsoleto")
                    return
                }
                val login = buildJsonObject {
                    put("tipo", TipoSocket.LOGIN)
                    put("token", JsonPrimitive(token))
                }
                webSocket.send(login.toString())
                tentativas = 0
                _estado.value = EstadoConexao.CONECTADO
            }
            Timber.d("WebSocket conectado")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!synchronized(trava) { atual() }) return
            val evento = EventoSocketParser.ler(text)
            if (evento is EventoSocket.ErroLogin) {
                // Token recusado: não adianta reconectar com o mesmo token.
                synchronized(trava) {
                    if (atual()) {
                        tokenDesejado = null
                        geracao++
                        socket = null
                        _estado.value = EstadoConexao.DESCONECTADO
                    }
                }
                webSocket.close(CODIGO_NORMAL, "login recusado")
            }
            if (evento is EventoSocket.Desconhecido) Timber.d("Evento WebSocket desconhecido: tipo %s", evento.tipo)
            _eventos.tryEmit(evento)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(CODIGO_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(trava) {
                if (!atual()) return
                socket = null
                agendarReconexaoTravado()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.d("WebSocket falhou: %s", t.javaClass.simpleName)
            synchronized(trava) {
                if (!atual()) return
                socket = null
                agendarReconexaoTravado()
            }
        }
    }

    companion object {
        private const val CODIGO_NORMAL = 1000

        /** min(30 s, 1 s × 2^tentativa), com até 20% de variação (como o web, contrato §6.7). */
        fun esperaPadrao(tentativa: Int): Long {
            val base = min(30_000L, 1_000L shl min(tentativa, 5))
            return base + Random.nextLong(0, base / 5 + 1)
        }
    }
}
