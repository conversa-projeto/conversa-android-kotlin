package com.conversa.app.feature.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.conversa.app.core.model.PontoMapa
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** As permissões de localização do aparelho: a aproximada basta; a precisa, se a pessoa der. */
internal val PERMISSOES_LOCALIZACAO = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

internal fun Context.temLocalizacao(): Boolean =
    PERMISSOES_LOCALIZACAO.any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/**
 * A posição do aparelho pelo `LocationManager` (sem Google Play Services: o app funciona sem FCM),
 * como o `getCurrentPosition` do web: vale uma posição de até 60 s; senão, pede uma nova e espera
 * até 15 s. Pede a todos os provedores ligados (GPS, rede, combinado) e fica com a primeira resposta:
 * o combinado sozinho pede precisão "equilibrada", que sem a rede não liga o GPS.
 */
class PosicaoDoAparelho @Inject constructor(@ApplicationContext private val contexto: Context) : FontePosicao {
    @SuppressLint("MissingPermission")
    override suspend fun posicao(): PontoMapa {
        if (!contexto.temLocalizacao()) throw SemPosicaoException(FalhaPosicao.NEGADA)
        val gerente = contexto.getSystemService(LocationManager::class.java) ?: throw SemPosicaoException(FalhaPosicao.SEM_SENSOR)
        val provedores = provedoresLigados(gerente)
        if (provedores.isEmpty()) throw SemPosicaoException(FalhaPosicao.INDISPONIVEL)
        try {
            val recente = provedores.mapNotNull { gerente.getLastKnownLocation(it) }
                .filter { idadeMs(it) <= IDADE_MAXIMA_MS }
                .maxByOrNull { it.elapsedRealtimeNanos }
            val local = recente ?: withTimeoutOrNull(ESPERA_MS) { primeiraPosicao(gerente, provedores) }
            return local?.let { PontoMapa(it.latitude, it.longitude) } ?: throw SemPosicaoException(FalhaPosicao.INDISPONIVEL)
        } catch (_: SecurityException) {
            throw SemPosicaoException(FalhaPosicao.NEGADA)
        }
    }

    /** Os provedores ligados, do mais preciso ao menos. */
    private fun provedoresLigados(gerente: LocationManager): List<String> {
        val ordem = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        return ordem.filter { runCatching { gerente.isProviderEnabled(it) }.getOrDefault(false) }
    }

    private fun idadeMs(local: Location): Long = (SystemClock.elapsedRealtimeNanos() - local.elapsedRealtimeNanos) / 1_000_000

    /** A primeira posição que algum provedor der; os outros pedidos são cancelados. */
    private suspend fun primeiraPosicao(gerente: LocationManager, provedores: List<String>): Location? = channelFlow {
        provedores.forEach { provedor -> launch { send(atual(gerente, provedor)) } }
    }.filterNotNull().firstOrNull()

    @SuppressLint("MissingPermission")
    private suspend fun atual(gerente: LocationManager, provedor: String): Location? = suspendCancellableCoroutine { continuacao ->
        val sinal = CancellationSignal()
        continuacao.invokeOnCancellation { sinal.cancel() }
        LocationManagerCompat.getCurrentLocation(gerente, provedor, sinal, ContextCompat.getMainExecutor(contexto)) { local ->
            if (continuacao.isActive) continuacao.resume(local)
        }
    }

    private companion object {
        const val IDADE_MAXIMA_MS = 60_000L
        const val ESPERA_MS = 15_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PosicaoModulo {
    @Binds
    abstract fun fontePosicao(implementacao: PosicaoDoAparelho): FontePosicao
}
