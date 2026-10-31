package com.conversa.app.core.network.realtime

import android.util.Log
import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.registro.resumoDoErro
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
import kotlinx.serialization.json.JsonNull
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
 * - Nunca registra o token nem o conteúdo das mensagens em log. No registro do app (tag
 *   [TAG]) ficam a abertura, o login, cada evento ([resumoParaRegistro]), o que o app manda, as
 *   quedas (código, motivo, quanto tempo ficou no ar) e as esperas da reconexão.
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
            Timber.tag(TAG).i(if (tokenDesejado == null) "Ligar o tempo real" else "Ligar o tempo real com outra sessão")
            tokenDesejado = token
            tentativas = 0
            abrirTravado()
        }
    }

    /** Desliga e não tenta reconectar. */
    fun desconectar() {
        synchronized(trava) {
            if (tokenDesejado != null) Timber.tag(TAG).i("Desligar o tempo real (estado: %s)", _estado.value)
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
            Timber.tag(TAG).i("Reconectar agora (estado: %s)", _estado.value)
            tentativas = 0
            abrirTravado()
        }
    }

    /**
     * A minha presença (tipo 63, PRE-02): [ativo] com o app na frente e a conversa aberta. Devolve
     * `false` se o socket não está aberto.
     */
    fun enviarPresenca(ativo: Boolean, conversaId: Long?): Boolean {
        val mensagem = buildJsonObject {
            put("tipo", TipoSocket.PRESENCA)
            put("ativo", ativo)
            put("conversa_id", conversaId?.let(::JsonPrimitive) ?: JsonNull)
        }
        val enviou = synchronized(trava) { socket?.send(mensagem.toString()) ?: false }
        registrarEnvio(enviou, "presença ativo=$ativo conversa=${conversaId ?: "-"}")
        return enviou
    }

    /** Sinal da chamada (tipo 57). Devolve `false` se o socket não está aberto. */
    fun enviarSinal(chamadaId: Long, dados: JsonObject): Boolean {
        val mensagem = buildJsonObject {
            put("tipo", TipoSocket.SINAL_CHAMADA)
            put("chamada_id", chamadaId)
            put("dados", dados)
        }
        val enviou = synchronized(trava) { socket?.send(mensagem.toString()) ?: false }
        registrarEnvio(enviou, "sinal ${acaoDoSinal(dados)} na chamada $chamadaId")
        return enviou
    }

    private fun registrarEnvio(enviou: Boolean, oQue: String) {
        if (enviou) Timber.tag(TAG).d("Enviado: %s", oQue) else Timber.tag(TAG).w("Não enviado (socket fechado): %s", oQue)
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
            if (token != null) Timber.tag(TAG).w("Sem servidor configurado: o tempo real fica desligado")
            _estado.value = EstadoConexao.DESCONECTADO
            return
        }
        _estado.value = EstadoConexao.CONECTANDO
        val minhaGeracao = geracao
        Timber.tag(TAG).i("Abrindo %s (conexão %d, tentativa %d)", url, minhaGeracao, tentativas + 1)
        socket = cliente.newWebSocket(Request.Builder().url(url).build(), Ouvinte(minhaGeracao, token))
    }

    private fun agendarReconexaoTravado() {
        if (tokenDesejado == null) {
            _estado.value = EstadoConexao.DESCONECTADO
            return
        }
        _estado.value = EstadoConexao.AGUARDANDO
        val espera = esperaReconexao(tentativas++)
        Timber.tag(TAG).i("Nova tentativa em %d ms (falhas seguidas: %d)", espera, tentativas)
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
        private val abertoEm = System.nanoTime()
        private var conectadoEm = 0L

        private fun atual() = minhaGeracao == geracao

        /** "há 2,5 s" desde a abertura, ou "conectado por 3 min" se chegou a conectar. */
        private fun duracao(): String {
            val agora = System.nanoTime()
            return if (conectadoEm == 0L) {
                "depois de ${(agora - abertoEm) / 1_000_000} ms tentando"
            } else {
                "depois de ${(agora - conectadoEm) / 1_000_000_000} s conectado"
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(trava) {
                if (!atual()) {
                    Timber.tag(TAG).d("Conexão %d abriu depois de substituída; fechando", minhaGeracao)
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
            conectadoEm = System.nanoTime()
            Timber.tag(TAG).i(
                "Conectado em %d ms (HTTP %d, %s); login do socket enviado",
                (conectadoEm - abertoEm) / 1_000_000,
                response.code,
                response.protocol,
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!synchronized(trava) { atual() }) return
            val evento = EventoSocketParser.ler(text)
            val prioridade = if (evento is EventoSocket.ErroLogin || evento is EventoSocket.ErroLeitura) Log.WARN else Log.DEBUG
            Timber.tag(TAG).log(prioridade, "Recebido: %s", evento.resumoParaRegistro())
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
            _eventos.tryEmit(evento)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (atual()) Timber.tag(TAG).i("O servidor está fechando: código %d %s, %s", code, reason.ifEmpty { "(sem motivo)" }, duracao())
            webSocket.close(CODIGO_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            synchronized(trava) {
                if (!atual()) return
                Timber.tag(TAG).i("Fechado: código %d %s, %s", code, reason.ifEmpty { "(sem motivo)" }, duracao())
                socket = null
                agendarReconexaoTravado()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            synchronized(trava) {
                if (!atual()) {
                    Timber.tag(TAG).d("Conexão antiga %d falhou: %s", minhaGeracao, t.resumoDoErro())
                    return
                }
                Timber.tag(TAG).w(
                    "Falhou %s: %s%s",
                    duracao(),
                    t.resumoDoErro(),
                    response?.let { " (HTTP ${it.code})" }.orEmpty(),
                )
                socket = null
                agendarReconexaoTravado()
            }
        }
    }

    companion object {
        const val TAG = "TempoReal"
        private const val CODIGO_NORMAL = 1000

        /** min(30 s, 1 s × 2^tentativa), com até 20% de variação (como o web, contrato §6.7). */
        fun esperaPadrao(tentativa: Int): Long {
            val base = min(30_000L, 1_000L shl min(tentativa, 5))
            return base + Random.nextLong(0, base / 5 + 1)
        }
    }
}
