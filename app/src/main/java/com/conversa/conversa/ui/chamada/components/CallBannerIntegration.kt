package com.conversa.conversa.ui.chamada.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import com.conversa.conversa.service.ChamadaService
import com.conversa.conversa.utils.ChamadaServiceObserver

/**
 * Extension function para configurar o CallBanner em qualquer Activity com ComposeView.
 *
 * Esta função facilita a integração do CallBanner em Activities XML-based,
 * encapsulando a lógica de observação do estado da chamada e atualização do banner.
 *
 * Uso:
 * ```kotlin
 * // No layout XML:
 * <androidx.compose.ui.platform.ComposeView
 *     android:id="@+id/callBannerComposeView"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content" />
 *
 * // Na Activity:
 * binding.callBannerComposeView.setupCallBanner(
 *     chamadaObserver = chamadaObserver,
 *     meuUsuarioId = userPreferences.getUsuarioId(),
 *     onBannerClick = {
 *         ChamadaNavigator.voltarParaChamadaAtiva(this)
 *     }
 * )
 * ```
 *
 * @param chamadaObserver O observador do ChamadaService que fornece os StateFlows
 * @param meuUsuarioId ID do usuário logado (para determinar nome do outro participante)
 * @param onBannerClick Callback quando o banner é clicado (deve navegar para ChamadaActivity)
 */
fun ComposeView.setupCallBanner(
    chamadaObserver: ChamadaServiceObserver,
    meuUsuarioId: Int,
    onBannerClick: () -> Unit
) {
    // Dispõe o Compose quando a View é destruída para evitar memory leaks
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

    setContent {
        CallBannerContent(
            chamadaObserver = chamadaObserver,
            meuUsuarioId = meuUsuarioId,
            onBannerClick = onBannerClick
        )
    }
}

/**
 * Composable interno que observa o estado da chamada e renderiza o CallBanner.
 */
@Composable
private fun CallBannerContent(
    chamadaObserver: ChamadaServiceObserver,
    meuUsuarioId: Int,
    onBannerClick: () -> Unit
) {
    val estado by chamadaObserver.estadoFlow.collectAsState()
    val chamada by chamadaObserver.chamadaAtualFlow.collectAsState()
    val timer by chamadaObserver.timerFlow.collectAsState()

    // O banner só é visível quando há chamada em andamento
    val isVisible = estado == ChamadaService.EstadoChamadaService.EM_CHAMADA

    // Obtém o nome do contato/grupo
    val callerName = chamadaObserver.getCallerName(meuUsuarioId)

    CallBanner(
        isVisible = isVisible,
        callerName = callerName,
        callDuration = timer,
        onBannerClick = onBannerClick
    )
}
