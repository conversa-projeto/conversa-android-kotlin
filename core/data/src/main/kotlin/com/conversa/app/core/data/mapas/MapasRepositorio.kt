package com.conversa.app.core.data.mapas

import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.conversa.app.core.network.di.DespachanteEs
import com.conversa.app.core.network.mapas.NOMINATIM
import com.conversa.app.core.network.mapas.OSRM
import com.conversa.app.core.network.mapas.lerBuscaEndereco
import com.conversa.app.core.network.mapas.lerNomeDoLugar
import com.conversa.app.core.network.mapas.lerRota
import com.conversa.app.core.network.mapas.urlBuscaEndereco
import com.conversa.app.core.network.mapas.urlNomeDoLugar
import com.conversa.app.core.network.mapas.urlRota
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** A busca de endereço não respondeu (o web: "A busca de endereço não respondeu."). */
class BuscaEnderecoFalhou : IOException("A busca de endereço não respondeu")

/**
 * Os serviços públicos de mapa que o web chama direto (ENV-30; web `services/mapas.ts`): a busca
 * de endereço e o nome do lugar no Nominatim, a rota de carro no OSRM. Uso leve, como pedem as
 * regras deles: a busca só sai quando a pessoa pede, e a rota uma vez por par de pontos. O token
 * do servidor nunca vai junto (só a API recebe) e as requisições ficam fora do log.
 */
@Singleton
class MapasRepositorio internal constructor(
    private val okHttp: dagger.Lazy<OkHttpClient>,
    private val io: CoroutineDispatcher,
    private val nominatim: HttpUrl,
    private val osrm: HttpUrl,
) {
    @Inject
    constructor(okHttp: dagger.Lazy<OkHttpClient>, @DespachanteEs io: CoroutineDispatcher) : this(okHttp, io, NOMINATIM, OSRM)

    /** Até 6 lugares com o endereço; falha com [BuscaEnderecoFalhou]. */
    suspend fun buscarEndereco(texto: String): Result<List<PontoMapa>> {
        val corpo = try {
            pedir(urlBuscaEndereco(texto, nominatim))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return corpo?.let { Result.success(lerBuscaEndereco(it)) } ?: Result.failure(BuscaEnderecoFalhou())
    }

    /** O nome do lugar num ponto; sem resposta, fica sem nome. */
    suspend fun nomeDoLugar(ponto: PontoMapa): String? = semFalha { pedir(urlNomeDoLugar(ponto, nominatim))?.let(::lerNomeDoLugar) }

    /** A rota de carro; nula quando o serviço não acha ou não responde (vai a linha reta). */
    suspend fun calcularRota(origem: PontoMapa, destino: PontoMapa): RotaCarro? =
        semFalha { pedir(urlRota(origem, destino, osrm))?.let(::lerRota) }

    private suspend fun <T> semFalha(bloco: suspend () -> T?): T? = try {
        bloco()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** O corpo da resposta (nulo se não for 2xx). Cancelar a coroutine cancela o pedido. */
    private suspend fun pedir(url: HttpUrl): String? = withContext(io) {
        okHttp.get().newCall(Request.Builder().url(url).header("Accept", "application/json").build()).aguardar().use { resposta ->
            if (resposta.isSuccessful) resposta.body.string() else null
        }
    }
}

/** Executa a chamada sem prender a thread; cancelar a coroutine cancela a chamada. */
private suspend fun Call.aguardar(): Response = suspendCancellableCoroutine { continuacao ->
    continuacao.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuacao.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuacao.resume(response) { _, resposta, _ -> resposta.close() }
            }
        },
    )
}
