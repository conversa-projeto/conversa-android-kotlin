package com.conversa.app.core.network.http

import com.conversa.app.core.network.json.ConversaJson
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException

/**
 * Erros da API já classificados. O servidor sempre responde `{ "error": "..." }`
 * (contrato §3); o nginx responde HTML quando a API está fora ou no limite de login.
 */
sealed class ErroApi(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa) {
    /** 401 numa requisição autenticada: token vencido ou inválido. */
    class SessaoExpirada(val detalhe: String?) : ErroApi(detalhe ?: "Sessão expirada")

    /** Resposta `{error}` do servidor (400, 401 de login, 403, 404, 409, 500…). */
    class Servidor(val status: Int, val detalhe: String) : ErroApi(detalhe)

    /** nginx sem a API (502/504, HTML) ou serviço fora. */
    class ServidorIndisponivel(val status: Int?) : ErroApi("Servidor indisponível")

    /** Muitas requisições (nginx: mais de 10 logins por minuto). */
    class MuitasTentativas : ErroApi("Muitas tentativas")

    /** Sem rede, DNS, certificado, tempo esgotado. */
    class SemConexao(causa: IOException) : ErroApi(causa.message ?: "Sem conexão", causa)

    /** O servidor ainda não foi configurado no app. */
    class ServidorNaoConfigurado : ErroApi("Servidor não configurado")

    /** Resposta num formato que o app não entende. */
    class RespostaInvalida(causa: Throwable) : ErroApi("Resposta inválida do servidor", causa)

    class Desconhecido(causa: Throwable) : ErroApi(causa.message ?: "Erro desconhecido", causa)
}

/** Texto para mostrar ao usuário (sem detalhes técnicos). */
fun ErroApi.mensagemAmigavel(): String = when (this) {
    is ErroApi.SessaoExpirada -> "Sua sessão expirou. Entre novamente."
    is ErroApi.Servidor -> if (status >= 500) "O servidor encontrou um erro. Tente de novo." else detalhe
    is ErroApi.ServidorIndisponivel -> "Servidor indisponível. Tente de novo em instantes."
    is ErroApi.MuitasTentativas -> "Muitas tentativas. Aguarde um minuto e tente de novo."
    is ErroApi.SemConexao -> "Sem conexão com o servidor. Verifique a internet e o endereço."
    is ErroApi.ServidorNaoConfigurado -> "Configure o endereço do servidor."
    is ErroApi.RespostaInvalida -> "Resposta inesperada do servidor."
    is ErroApi.Desconhecido -> "Algo deu errado. Tente de novo."
}

/** Converte qualquer falha de chamada de API em [ErroApi]. */
fun Throwable.paraErroApi(): ErroApi = when (this) {
    is ErroApi -> this
    is ServidorNaoConfiguradoException -> ErroApi.ServidorNaoConfigurado()
    is HttpException -> erroDeResposta(code(), response()?.errorBody()?.string(), response()?.errorBody()?.contentType()?.subtype)
    is IOException -> ErroApi.SemConexao(this)
    is SerializationException, is IllegalArgumentException -> ErroApi.RespostaInvalida(this)
    else -> ErroApi.Desconhecido(this)
}

internal fun erroDeResposta(status: Int, corpo: String?, subtipo: String?): ErroApi {
    val detalhe = corpo?.let(::lerCampoError)
    return when {
        detalhe == null && (subtipo == "html" || corpo?.trimStart()?.startsWith("<") == true) ->
            if (status == 429 || status == 503) ErroApi.MuitasTentativas() else ErroApi.ServidorIndisponivel(status)
        status == 429 -> ErroApi.MuitasTentativas()
        status in listOf(502, 503, 504) && detalhe == null -> ErroApi.ServidorIndisponivel(status)
        // 401 de token (contrato §2.4) = sessão expirada; 401 do login (senha errada etc.) é erro comum.
        status == 401 && (detalhe == null || detalhe.startsWith("Token")) -> ErroApi.SessaoExpirada(detalhe)
        else -> ErroApi.Servidor(status, detalhe ?: "Erro $status")
    }
}

private fun lerCampoError(corpo: String): String? = try {
    ConversaJson.parseToJsonElement(corpo).jsonObject["error"]?.jsonPrimitive?.content
} catch (_: Exception) {
    null
}

/**
 * Executa uma chamada de API e devolve [Result] com [ErroApi] na falha.
 * Cancelamento de coroutine não é capturado.
 */
suspend fun <T> chamarApi(bloco: suspend () -> T): Result<T> = try {
    Result.success(bloco())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e.paraErroApi())
}
