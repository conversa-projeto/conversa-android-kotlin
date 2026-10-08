package com.conversa.app.core.webrtc

/**
 * Quem está falando (TODO 6.13, como o `useFalaChamada` do web): o volume passou de
 * [limiar] e a pessoa continua marcada por [seguraMs] depois de parar, para o indicador
 * não piscar entre as palavras.
 */
internal class DetectorDeFala(private val limiar: Double = LIMIAR, private val seguraMs: Long = SEGURA_MS) {
    private var ultimaFala: Long? = null

    /** Uma medida do volume (0 a 1) no instante [agoraMs]; devolve se está falando. */
    fun medir(nivel: Double, agoraMs: Long): Boolean {
        if (nivel > limiar) ultimaFala = agoraMs
        val ultima = ultimaFala ?: return false
        return agoraMs - ultima < seguraMs
    }

    companion object {
        const val LIMIAR = 0.02
        const val SEGURA_MS = 400L
    }
}
