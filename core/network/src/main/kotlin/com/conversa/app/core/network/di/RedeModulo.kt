package com.conversa.app.core.network.di

import com.conversa.app.core.network.BuildConfig
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.auth.EventosSessao
import com.conversa.app.core.network.auth.TokenProvider
import com.conversa.app.core.network.config.ServerConfigProvider
import com.conversa.app.core.network.http.AutenticacaoInterceptor
import com.conversa.app.core.network.http.BASE_FICTICIA
import com.conversa.app.core.network.http.EnderecoInterceptor
import com.conversa.app.core.network.json.ConversaJson
import com.conversa.app.core.network.mapas.ehServicoDeMapa
import com.conversa.app.core.network.realtime.RealtimeClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Escopo de coroutines que vive enquanto o processo do app vive. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EscopoAplicacao

/** Dispatcher de E/S (rede, disco). Injetado para os testes trocarem pelo relógio virtual. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DespachanteEs

@Module
@InstallIn(SingletonComponent::class)
object RedeModulo {
    @Provides
    @Singleton
    @EscopoAplicacao
    fun escopoAplicacao(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @DespachanteEs
    fun despachanteEs(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun json(): Json = ConversaJson

    /**
     * O único OkHttpClient do app (REST, WebSocket, WHIP/WHEP, imagens).
     * Log só no debug e sem o cabeçalho Authorization (problema #4 do legado).
     */
    @Provides
    @Singleton
    fun okHttp(config: ServerConfigProvider, tokens: TokenProvider, eventos: EventosSessao): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            // Os tiles do OSM, o Nominatim e o OSRM públicos recusam o agente padrão das bibliotecas (localização, `ac710eb`).
            .addInterceptor { cadeia -> cadeia.proceed(cadeia.request().newBuilder().header("User-Agent", AGENTE_DO_APP).build()) }
            .addInterceptor(EnderecoInterceptor(config))
            .addInterceptor(AutenticacaoInterceptor(tokens, eventos, config))
            // Os mapas ficam fora do log: o endereço buscado e os tiles mostram onde a pessoa está olhando.
            .addInterceptor { cadeia ->
                if (ehServicoDeMapa(cadeia.request().url.host)) cadeia.proceed(cadeia.request()) else log.intercept(cadeia)
            }
            .build()
    }

    @Provides
    @Singleton
    fun retrofit(okHttp: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_FICTICIA)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun conversaApi(retrofit: Retrofit): ConversaApi = retrofit.create(ConversaApi::class.java)

    @Provides
    @Singleton
    fun realtimeClient(
        okHttp: OkHttpClient,
        config: ServerConfigProvider,
        @EscopoAplicacao escopo: CoroutineScope,
    ): RealtimeClient = RealtimeClient(okHttp, config, escopo)
}

/** Como o app se identifica nas requisições (exigido pelos serviços públicos de mapa). */
const val AGENTE_DO_APP = "ConversaAndroid/2.0 (app de mensagens do Conversa)"
