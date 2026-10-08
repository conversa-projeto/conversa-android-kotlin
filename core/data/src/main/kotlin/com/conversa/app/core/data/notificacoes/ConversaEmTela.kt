package com.conversa.app.core.data.notificacoes

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A conversa que está na tela agora (aberta e visível). Mensagens dela não viram
 * notificação nem som (NOT-01), e a notificação dela some ao abrir (NOT-03).
 */
@Singleton
class ConversaEmTela @Inject constructor() {
    private val _id = MutableStateFlow<Long?>(null)
    val id: StateFlow<Long?> = _id.asStateFlow()

    fun apareceu(conversaId: Long) {
        _id.value = conversaId
    }

    /** Só limpa se ainda for esta (a próxima conversa pode ter aparecido antes). */
    fun sumiu(conversaId: Long) {
        _id.update { if (it == conversaId) null else it }
    }
}
