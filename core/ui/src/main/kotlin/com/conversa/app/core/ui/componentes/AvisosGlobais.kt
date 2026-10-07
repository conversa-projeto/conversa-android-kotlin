package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Snackbar único do app para erros e avisos (GER-01). Qualquer tela pega com
 * `LocalAvisos.current` e chama `mostrarErro(...)`.
 */
val LocalAvisos = staticCompositionLocalOf<SnackbarHostState> { error("AreaDeAvisos não foi colocada na raiz") }

/** Coloca o Snackbar global por cima de todo o conteúdo, acima da barra de navegação e do teclado. */
@Composable
fun AreaDeAvisos(conteudo: @Composable () -> Unit) {
    val avisos = remember { SnackbarHostState() }
    CompositionLocalProvider(LocalAvisos provides avisos) {
        Box(Modifier.fillMaxSize()) {
            conteudo()
            SnackbarHost(
                hostState = avisos,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
            )
        }
    }
}
