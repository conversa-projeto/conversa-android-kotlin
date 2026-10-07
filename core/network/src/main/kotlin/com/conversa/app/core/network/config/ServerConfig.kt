package com.conversa.app.core.network.config

import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Endereço do servidor Conversa. Tudo passa pela mesma origem (nginx), então
 * uma única URL base dá os três caminhos usados pelo app (contrato §1.2):
 * - REST em `<base>/api/`
 * - WebSocket em `<base>/ws/` (a barra final é obrigatória)
 * - WebRTC (MediaMTX, WHIP/WHEP) em `<base>/webrtc/`
 */
class ServerConfig private constructor(
    /** Sempre termina em "/". */
    val base: HttpUrl,
) {
    val api: HttpUrl = base.resolve("api/")!!

    val ws: String = base.resolve("ws/")!!.toString().let { url ->
        when {
            url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
            else -> "ws://" + url.removePrefix("http://")
        }
    }

    val webrtc: HttpUrl = base.resolve("webrtc/")!!

    override fun equals(other: Any?): Boolean = other is ServerConfig && other.base == base

    override fun hashCode(): Int = base.hashCode()

    override fun toString(): String = base.toString()

    companion object {
        /**
         * Interpreta o que a pessoa digitou. Aceita sem esquema (usa https),
         * com ou sem barra final e com o `/api` no fim (que é removido).
         * Devolve `null` se não for um endereço http(s) válido.
         */
        fun aPartirDe(texto: String): ServerConfig? {
            val limpo = texto.trim()
            if (limpo.isEmpty() || limpo.any { it.isWhitespace() }) return null
            val comEsquema = if ("://" in limpo) limpo else "https://$limpo"
            val url = comEsquema.toHttpUrlOrNull() ?: return null
            if (url.scheme != "http" && url.scheme != "https") return null
            if (url.host.isBlank()) return null

            val segmentos = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
            if (segmentos.lastOrNull().equals("api", ignoreCase = true)) segmentos.removeAt(segmentos.lastIndex)

            val base = url.newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/")
                .apply { segmentos.forEach { addPathSegment(it) } }
                .addPathSegment("")
                .build()
            return ServerConfig(base)
        }
    }
}

/** Fonte da configuração atual do servidor (implementada na camada de dados, a partir do DataStore). */
interface ServerConfigProvider {
    val atual: StateFlow<ServerConfig?>
}
