package com.conversa.app

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.sessao.IniciadorSessao
import com.conversa.app.core.data.sessao.LimpezaSessao
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.ConexaoTempoReal
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    @Inject lateinit var sessao: SessaoRepositorio

    @Inject @EscopoAplicacao
    lateinit var escopo: CoroutineScope

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
        limparAoSair()
    }

    /**
     * Fim da sessão (logout ou 401): além do banco (LimpezaSessao), some com as
     * notificações e com as imagens em cache (fotos de outra conta não ficam no aparelho).
     */
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    private fun limparAoSair() {
        escopo.launch {
            sessao.fim.collect {
                NotificationManagerCompat.from(this@ConversaApplication).cancelAll()
                val imagens = SingletonImageLoader.get(this@ConversaApplication)
                imagens.memoryCache?.clear()
                withContext(Dispatchers.IO) { imagens.diskCache?.clear() }
            }
        }
    }

    /** Coil usa o mesmo OkHttp do app (mesmas regras de TLS e de log). */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttp.get() })) }
        .build()
}
