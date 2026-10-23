package com.conversa.app.core.data.presenca

import com.conversa.app.core.network.di.EscopoAplicacao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Se a pessoa está usando o app (PRE-02, como a aba parada do web `17c49a4`). Um toque nas telas
 * ou o texto digitado no chat contam como uso; [TEMPO_PARADO_MS] sem nada, o app fica parado e a
 * presença vai como ausente, ainda com a conversa aberta. O teclado não passa pelas telas do app:
 * por isso o chat avisa a cada mudança do texto.
 */
@Singleton
class UsoDoApp @Inject constructor(@EscopoAplicacao private val escopo: CoroutineScope) {
    private val _parado = MutableStateFlow(false)
    val parado: StateFlow<Boolean> = _parado.asStateFlow()

    private var espera: Job? = null

    /** Toque, tecla ou texto digitado (e a volta ao primeiro plano): em uso, e recomeça a contar. */
    fun marcarUso() {
        _parado.value = false
        espera?.cancel()
        espera = escopo.launch {
            delay(TEMPO_PARADO_MS)
            _parado.value = true
        }
    }

    /** Segundo plano: para de contar; a volta começa em uso. */
    fun pausar() {
        espera?.cancel()
        espera = null
        _parado.value = false
    }

    companion object {
        /** O `TEMPO_OCIOSO_MS` do web (`17c49a4`; antes, 5 min). */
        const val TEMPO_PARADO_MS = 30_000L
    }
}
