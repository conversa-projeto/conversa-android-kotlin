package com.conversa.app.core.network.http

import com.conversa.app.core.network.auth.EventosSessao
import com.conversa.app.core.network.auth.TokenProvider
import com.conversa.app.core.network.config.ServerConfigProvider
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Endereço fictício usado como base do Retrofit; o [EnderecoInterceptor] troca pelo real. */
internal val BASE_FICTICIA: HttpUrl = "http://conversa.invalid/api/".toHttpUrl()

/** Servidor ainda não configurado: o app precisa passar pela tela "Servidor". */
class ServidorNaoConfiguradoException : IOException("Servidor não configurado")

/**
 * Troca a base fictícia pela configuração atual a cada requisição. Assim, mudar
 * o servidor nas configurações vale na hora, sem recriar o Retrofit.
 */
class EnderecoInterceptor(private val config: ServerConfigProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requisicao = chain.request()
        val url = requisicao.url
        if (url.host != BASE_FICTICIA.host) return chain.proceed(requisicao)

        val atual = config.atual.value ?: throw ServidorNaoConfiguradoException()
        val relativo = url.encodedPath.removePrefix(BASE_FICTICIA.encodedPath)
        val nova = atual.api.newBuilder()
            .encodedPath(atual.api.encodedPath + relativo)
            .encodedQuery(url.encodedQuery)
            .build()
        return chain.proceed(requisicao.newBuilder().url(nova).build())
    }
}

/**
 * Coloca `Authorization: Bearer <token>` e transforma 401 em "sessão expirada".
 * O 401 do próprio login (senha errada) não conta.
 */
class AutenticacaoInterceptor(private val tokens: TokenProvider, private val eventos: EventosSessao) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requisicao = chain.request()
        val publica = requisicao.header(CABECALHO_PUBLICA) != null
        val token = if (publica) null else tokens.token()
        val enviada = requisicao.newBuilder()
            .removeHeader(CABECALHO_PUBLICA)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        val resposta = chain.proceed(enviada)
        if (resposta.code == 401 && token != null) eventos.notificarSessaoExpirada()
        return resposta
    }

    companion object {
        /** Marca rotas públicas (login, cadastro): não leva token e 401 não encerra a sessão. */
        const val CABECALHO_PUBLICA = "X-Conversa-Publica"
    }
}
