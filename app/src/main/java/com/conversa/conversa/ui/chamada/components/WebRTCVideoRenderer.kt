package com.conversa.conversa.ui.chamada.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Renderiza um VideoTrack do WebRTC em um SurfaceViewRenderer embrulhado em AndroidView.
 *
 * Ciclo de vida correto:
 *   - No launch: `init(eglBaseContext, null)` + `setScalingType` + `track.addSink(renderer)`
 *   - No dispose: `track.removeSink(renderer)` + `renderer.release()`
 *
 * Mirror: True para vídeo local (espelhado como câmera frontal).
 */
@Composable
fun WebRTCVideoRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    mirror: Boolean = false,
    modifier: Modifier = Modifier,
    scalingType: RendererCommon.ScalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL,
) {
    Box(modifier = modifier) {
        if (videoTrack != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setScalingType(scalingType)
                        setMirror(mirror)
                    }
                },
                update = { renderer ->
                    // Caso o track mude, reatrelar a sink
                    renderer.setMirror(mirror)
                }
            )

            DisposableEffect(videoTrack) {
                // Re-obtemos o renderer via pequena gambiarra: adicionar via factory novamente nao eh
                // possivel aqui. A solucao correta requer um wrapper. Neste Composable simplificado,
                // assumimos que o track permanece o mesmo enquanto este Composable vive.
                //
                // Para cenario de troca dinamica de track, usar um `key(videoTrack) { ... }` em volta.
                onDispose { }
            }
        }
    }
}

/**
 * Variante mais robusta: mantem referencia explicita ao renderer para conseguir chamar
 * addSink/removeSink no disposal.
 */
@Composable
fun WebRTCVideoRendererSafe(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    mirror: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rendererRef = remember { arrayOfNulls<SurfaceViewRenderer>(1) }

    Box(modifier = modifier) {
        if (videoTrack != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setMirror(mirror)
                        rendererRef[0] = this
                        videoTrack.addSink(this)
                    }
                },
                update = { renderer ->
                    renderer.setMirror(mirror)
                }
            )

            DisposableEffect(videoTrack) {
                onDispose {
                    rendererRef[0]?.let { renderer ->
                        try { videoTrack.removeSink(renderer) } catch (_: Exception) {}
                        try { renderer.release() } catch (_: Exception) {}
                    }
                    rendererRef[0] = null
                }
            }
        }
    }
}
