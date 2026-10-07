package com.conversa.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.data.sessao.LimpezaSessao
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient
import timber.log.Timber

@HiltAndroidApp
class ConversaApplication :
    Application(),
    SingletonImageLoader.Factory {
    @Inject lateinit var primeiroPlano: MonitorPrimeiroPlano

    @Inject lateinit var conexaoTempoReal: ConexaoTempoReal

    @Inject lateinit var sincronizacao: SyncManager

    @Inject lateinit var presenca: PresencaRepositorio

    @Inject lateinit var iniciadorSessao: IniciadorSessao

    @Inject lateinit var limpezaSessao: LimpezaSessao

    @Inject lateinit var okHttp: Lazy<OkHttpClient>

    override fun onCreate() {
        super.onCreate()
        // Log só no debug; no release nada vai para o logcat (problema #4 do legado).
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        primeiroPlano.iniciar()
        limpezaSessao.iniciar()
        presenca.iniciar()
        sincronizacao.iniciar()
        iniciadorSessao.iniciar()
        conexaoTempoReal.iniciar()
    }

    /** Coil usa o mesmo OkHttp do app (mesmas regras de TLS e de log). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp.get() })) }
        .build()
}
