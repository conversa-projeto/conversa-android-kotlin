package com.conversa.app.core.ui.estado

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Eventos de uma vez só (navegar, mostrar um aviso) emitidos pelo ViewModel.
 * Diferente do estado, não se repetem ao girar a tela.
 */
class EventosUnicos<T> {
    private val canal = Channel<T>(Channel.BUFFERED)
    val fluxo: Flow<T> = canal.receiveAsFlow()

    fun enviar(evento: T) {
        canal.trySend(evento)
    }
}

/** Coleta os eventos enquanto a tela está pelo menos iniciada. */
@Composable
fun <T> ColetarEventos(fluxo: Flow<T>, aoReceber: suspend (T) -> Unit) {
    val dono = LocalLifecycleOwner.current
    LaunchedEffect(fluxo, dono) {
        dono.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fluxo.collect { aoReceber(it) }
        }
    }
}
