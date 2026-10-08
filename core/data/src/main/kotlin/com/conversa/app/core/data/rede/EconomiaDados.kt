package com.conversa.app.core.data.rede

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Abaixo disto a conexão é "lenta" (o web bloqueia em 2g e slow-2g, até uns 70 kbps). */
const val LIMITE_CONEXAO_LENTA_KBPS = 150

/**
 * Economizar dados (TODO 4.10, FC-414): imagens e vídeos esperam o toque ("Toque para
 * carregar", como o web). Vale com a conexão lenta (o web usa `effectiveType` 2g/slow-2g)
 * ou com a Economia de dados do Android ligada numa rede medida (dados móveis).
 */
fun deveEconomizar(redeMedida: Boolean, economiaDoAndroid: Boolean, kbpsDescida: Int): Boolean =
    kbpsDescida in 1 until LIMITE_CONEXAO_LENTA_KBPS || (redeMedida && economiaDoAndroid)

/** Acompanha a rede padrão só enquanto alguém observa [ativa] (a tela de uma conversa). */
@Singleton
class EconomiaDados @Inject constructor(@ApplicationContext contexto: Context, @EscopoAplicacao escopo: CoroutineScope) {
    private val conectividade = contexto.getSystemService(ConnectivityManager::class.java)

    val ativa: StateFlow<Boolean> = callbackFlow {
        val ouvinte = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                val economiaDoAndroid = conectividade.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                trySend(
                    deveEconomizar(
                        redeMedida = !capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                        economiaDoAndroid = economiaDoAndroid,
                        kbpsDescida = capacidades.linkDownstreamBandwidthKbps,
                    ),
                )
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        conectividade.registerDefaultNetworkCallback(ouvinte)
        awaitClose { conectividade.unregisterNetworkCallback(ouvinte) }
    }.distinctUntilChanged().stateIn(escopo, SharingStarted.WhileSubscribed(5_000), false)
}
