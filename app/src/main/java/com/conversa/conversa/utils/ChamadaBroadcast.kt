package com.conversa.conversa.utils

object ChamadaBroadcast {
    
    const val ACTION_CHAMADA_RECEBIDA = "com.conversa.CHAMADA_RECEBIDA"
    const val EXTRA_CHAMADA_ID = "chamada_id"
    const val EXTRA_USUARIO_ID = "usuario_id"
    const val EXTRA_USUARIO_NOME = "usuario_nome"
    
    interface ChamadaListener {
        fun onChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String)
    }
    
    private val listeners = mutableListOf<ChamadaListener>()
    
    fun addListener(listener: ChamadaListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: ChamadaListener) {
        listeners.remove(listener)
    }
    
    fun notifyChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
        listeners.forEach { 
            it.onChamadaRecebida(chamadaId, usuarioId, usuarioNome)
        }
    }
}
