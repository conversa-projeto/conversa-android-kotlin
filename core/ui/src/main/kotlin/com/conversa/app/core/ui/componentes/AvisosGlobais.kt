package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Snackbar único do app para erros e avisos (GER-01). Qualquer tela pega com
 * `LocalAvisos.current` e chama `mostrarErro(...)`.
 */
val LocalAvisos = staticCompositionLocalOf<SnackbarHostState> { error("AreaDeAvisos não foi colocada na raiz") }

/**
 * Altura da barra fixa no pé da tela, quando há uma (a de abas da tela principal): os avisos ficam
 * acima dela. A tela que tem a barra põe a altura aqui e volta a zero ao sair.
 */
val LocalBarraDeBaixo = staticCompositionLocalOf<MutableState<Dp>> { mutableStateOf(0.dp) }

/**
 * Coloca o Snackbar global por cima de todo o conteúdo, acima do que estiver mais alto embaixo: o
 * teclado, a barra do sistema ou a barra de abas ([LocalBarraDeBaixo]). Por baixo de um aviso fixo
 * ("… (tentando de novo…)", sem servidor), a barra de abas ficava coberta e não recebia o toque.
 */
@Composable
fun AreaDeAvisos(conteudo: @Composable () -> Unit) {
    val avisos = remember { SnackbarHostState() }
    val barraDeBaixo = remember { mutableStateOf(0.dp) }
    CompositionLocalProvider(LocalAvisos provides avisos, LocalBarraDeBaixo provides barraDeBaixo) {
        Box(Modifier.fillMaxSize()) {
            conteudo()
            val teclado = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            val sistema = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            SnackbarHost(
                hostState = avisos,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = maxOf(teclado, sistema, barraDeBaixo.value)),
            )
        }
    }
}
