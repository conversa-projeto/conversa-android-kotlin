package com.conversa.app.core.network.http

import android.util.Log
import com.conversa.app.core.network.mapas.ehServicoDeMapa
import com.conversa.app.core.registro.resumoDoErro
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

/**
 * Uma linha no registro do app por requisição HTTP (REST, anexos, imagens, WHIP/WHEP; o WebSocket
 * fica com o `RealtimeClient`, e o OkHttp não manda os eventos dele): método, endereço sem
 * segredos ([resumoDaUrl]), status, tempo e tamanhos. Numa conexão nova ou numa falha, também
 * quanto levou cada etapa (DNS, TCP, TLS), com que endereço e onde parou.
 *
 * Um objeto por chamada ([FABRICA]); os eventos de uma chamada vêm em sequência.
 */
class RegistroHttp private constructor() : EventListener() {
    private val inicio = System.nanoTime()
    private var metodo = ""
    private var url: HttpUrl? = null
    private var status: Int? = null
    private var cancelada = false
    private var conexaoNova = false
    private var inicioDns = 0L
    private var msDns: Long? = null
    private var enderecosDns = 0
    private var inicioConexao = 0L
    private var msConexao: Long? = null
    private var inicioTls = 0L
    private var msTls: Long? = null
    private var versaoTls: String? = null
    private var protocolo: Protocol? = null
    private var endereco: InetSocketAddress? = null
    private var msPrimeiroByte: Long? = null
    private var enviados = 0L
    private var recebidos = 0L
    private val falhasDeConexao = mutableListOf<String>()

    override fun callStart(call: Call) {
        metodo = call.request().method
        url = call.request().url
    }

    override fun dnsStart(call: Call, domainName: String) {
        conexaoNova = true
        inicioDns = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        msDns = desde(inicioDns)
        enderecosDns = inetAddressList.size
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        conexaoNova = true
        inicioConexao = System.nanoTime()
        endereco = inetSocketAddress
    }

    override fun secureConnectStart(call: Call) {
        inicioTls = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        msTls = desde(inicioTls)
        versaoTls = handshake?.tlsVersion?.javaName
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        msConexao = desde(inicioConexao)
        protocolo = protocol
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?, ioe: IOException) {
        falhasDeConexao += "${textoDoEndereco(inetSocketAddress)} falhou em ${desde(inicioConexao)} ms (${ioe.resumoDoErro()})"
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        protocolo = connection.protocol()
        if (endereco == null) endereco = connection.route().socketAddress
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        // O endereço de verdade: o da chamada ainda é a base fictícia (o EnderecoInterceptor troca depois).
        url = request.url
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        enviados += byteCount
    }

    override fun responseHeadersStart(call: Call) {
        if (msPrimeiroByte == null) msPrimeiroByte = desde(inicio)
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        status = response.code
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        recebidos += byteCount
    }

    override fun canceled(call: Call) {
        cancelada = true
    }

    override fun callEnd(call: Call) = registrar(null)

    override fun callFailed(call: Call, ioe: IOException) = registrar(ioe)

    private fun registrar(erro: IOException?) {
        val alvo = url
        val mapa = alvo != null && ehServicoDeMapa(alvo.host)
        val texto = buildString {
            append(metodo).append(' ').append(alvo?.let(::resumoDaUrl) ?: "?")
            when {
                cancelada -> append(" cancelada")
                erro != null -> append(" ✗ ").append(erro.resumoDoErro())
                else -> append(" → ").append(status ?: "?")
            }
            append(" em ").append(desde(inicio)).append(" ms")
            if (enviados > 0) append(" · enviou ").append(tamanho(enviados))
            if (recebidos > 0) append(" · recebeu ").append(tamanho(recebidos))
            if (!mapa && (conexaoNova || erro != null)) append(" · ").append(detalhesDaConexao())
            if (!mapa) falhasDeConexao.forEach { append(" · ").append(it) }
        }
        val prioridade = when {
            cancelada -> Log.DEBUG
            erro != null -> Log.WARN
            (status ?: 0) >= 500 -> Log.WARN
            (status ?: 0) >= 400 -> Log.INFO
            else -> Log.DEBUG
        }
        Timber.tag(TAG).log(prioridade, texto)
    }

    private fun detalhesDaConexao(): String {
        val etapas = listOfNotNull(
            msDns?.let { "dns $it ms ($enderecosDns ${if (enderecosDns == 1) "endereço" else "endereços"})" },
            msConexao?.let { "tcp ${it - (msTls ?: 0)} ms" },
            msTls?.let { "tls $it ms" + versaoTls?.let { v -> " $v" }.orEmpty() },
            msPrimeiroByte?.let { "1º byte em $it ms" },
            protocolo?.toString(),
        )
        val onde = endereco?.let { " com ${textoDoEndereco(it)}" }.orEmpty()
        val tipo = if (conexaoNova) "conexão nova" else "conexão reaproveitada"
        return "$tipo$onde" + if (etapas.isEmpty()) "" else " (${etapas.joinToString(", ")})"
    }

    companion object {
        const val TAG = "Http"

        val FABRICA = EventListener.Factory { RegistroHttp() }

        private fun desde(inicio: Long) = (System.nanoTime() - inicio) / 1_000_000

        private fun textoDoEndereco(endereco: InetSocketAddress): String =
            "${endereco.address?.hostAddress ?: endereco.hostString}:${endereco.port}"
    }
}

/** Valores que aparecem no registro: números, listas de números e sim/não. O resto (busca, assinatura) vira "…". */
private fun valorVisivel(valor: String): Boolean =
    valor == "true" || valor == "false" || valor.all { it.isDigit() || it == '-' || it == ',' }

private const val MAXIMO_VALOR = 60

/**
 * O endereço como vai para o registro: esquema, servidor, porta (se não for a padrão) e caminho,
 * com o nome de cada parâmetro e o valor só quando é número ([valorVisivel]): o texto pesquisado e
 * as assinaturas das URLs de anexo ficam de fora. Os serviços de mapa, só o nome (o endereço
 * mostra onde a pessoa está olhando).
 */
fun resumoDaUrl(url: HttpUrl): String {
    if (ehServicoDeMapa(url.host)) return "${url.host} (mapa)"
    val porta = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
    val consulta = (0 until url.querySize).joinToString("&") { i ->
        val valor = url.queryParameterValue(i).orEmpty()
        val visivel = when {
            valor.isEmpty() -> ""
            valorVisivel(valor) -> valor.take(MAXIMO_VALOR) + if (valor.length > MAXIMO_VALOR) "…" else ""
            else -> "…"
        }
        "${url.queryParameterName(i)}=$visivel"
    }
    return "${url.scheme}://${url.host}$porta${url.encodedPath}" + if (consulta.isEmpty()) "" else "?$consulta"
}

/** "512 B", "2,3 kB", "4,1 MB". */
internal fun tamanho(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f kB", bytes / 1024.0)
    else -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / (1024.0 * 1024))
}
