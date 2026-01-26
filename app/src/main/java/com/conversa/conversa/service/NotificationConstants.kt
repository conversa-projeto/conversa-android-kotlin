package com.conversa.conversa.service

/**
 * Constantes centralizadas para notificações do app.
 * Mantém todos os IDs de notificação e canais em um único lugar.
 *
 * ## Estrutura de IDs para Chamadas
 *
 * Os IDs são organizados em faixas para suportar múltiplas chamadas simultâneas.
 * Como o chamadaId do servidor é um autoincremento (pode ser qualquer valor),
 * usamos arrays internos para mapear chamadaId → posição no array → ID da notificação.
 *
 * Faixas de IDs:
 * - FOREGROUND: 1002 (fixo, único para o serviço)
 * - INCOMING:   2000-2699 (base 2000 + posição no array, máx 700 chamadas recebendo)
 * - ONGOING:    2700-2899 (base 2700 + posição no array, máx 200 chamadas em andamento)
 * - MISSED:     2900-3099 (base 2900 + posição no array, máx 200 chamadas perdidas)
 *
 * Exemplo de fluxo:
 * 1. Chamada 45678 chega → adiciona ao array recebendo → posição 0 → notificação 2000
 * 2. Chamada 89012 chega → adiciona ao array recebendo → posição 1 → notificação 2001
 * 3. Chamada 45678 atendida → move para array em andamento → posição 0 → notificação 2700
 * 4. Chamada 45678 encerrada → remove dos arrays
 * 5. Nova chamada 99999 → reutiliza posição 0 → notificação 2000
 */
object NotificationConstants {

    // ==================== CANAIS ====================

    /** Canal para notificações de chamadas de voz */
    const val CHANNEL_ID_CHAMADAS = "conversa_chamada_channel"

    // ==================== IDs BASE DE NOTIFICAÇÃO - CHAMADAS ====================

    /** Notificação do foreground service de chamadas (fixo) */
    const val NOTIFICATION_ID_CHAMADA_FOREGROUND = 1002

    /** Base para notificação de chamada recebida (2000-2699) */
    const val NOTIFICATION_ID_CHAMADA_INCOMING = 2000

    /** Base para notificação de chamada em andamento (2700-2899) */
    const val NOTIFICATION_ID_CHAMADA_ONGOING = 2700

    /** Base para notificação de chamada perdida (2900-3099) */
    const val NOTIFICATION_ID_CHAMADA_MISSED = 2900

    // Limites de cada faixa
    private const val MAX_INCOMING = 700
    private const val MAX_ONGOING = 200
    private const val MAX_MISSED = 200

    // ==================== ARRAYS DE MAPEAMENTO chamadaId → posição ====================

    /** Mapa de chamadaId → posição para chamadas recebendo */
    private val mapaRecebendo = mutableMapOf<Int, Int>()
    private val posicoesLivresRecebendo = mutableListOf<Int>()
    private var proximaPosicaoRecebendo = 0

    /** Mapa de chamadaId → posição para chamadas em andamento */
    private val mapaEmAndamento = mutableMapOf<Int, Int>()
    private val posicoesLivresEmAndamento = mutableListOf<Int>()
    private var proximaPosicaoEmAndamento = 0

    /** Mapa de chamadaId → posição para chamadas perdidas */
    private val mapaPerdidas = mutableMapOf<Int, Int>()
    private val posicoesLivresPerdidas = mutableListOf<Int>()
    private var proximaPosicaoPerdidas = 0

    // ==================== MÉTODOS PARA OBTER ID DA NOTIFICAÇÃO ====================

    /**
     * Obtém o ID da notificação de chamada recebida.
     * Se a chamada ainda não está no mapa, adiciona automaticamente.
     */
    @Synchronized
    fun getNotificationIdIncoming(chamadaId: Int): Int {
        val posicao = mapaRecebendo.getOrPut(chamadaId) {
            alocarPosicaoRecebendo()
        }
        return NOTIFICATION_ID_CHAMADA_INCOMING + posicao
    }

    /**
     * Obtém o ID da notificação de chamada em andamento.
     * Se a chamada ainda não está no mapa, adiciona automaticamente.
     */
    @Synchronized
    fun getNotificationIdOngoing(chamadaId: Int): Int {
        val posicao = mapaEmAndamento.getOrPut(chamadaId) {
            alocarPosicaoEmAndamento()
        }
        return NOTIFICATION_ID_CHAMADA_ONGOING + posicao
    }

    /**
     * Obtém o ID da notificação de chamada perdida.
     * Se a chamada ainda não está no mapa, adiciona automaticamente.
     */
    @Synchronized
    fun getNotificationIdMissed(chamadaId: Int): Int {
        val posicao = mapaPerdidas.getOrPut(chamadaId) {
            alocarPosicaoPerdidas()
        }
        return NOTIFICATION_ID_CHAMADA_MISSED + posicao
    }

    // ==================== MÉTODOS PRIVADOS DE ALOCAÇÃO ====================

    private fun alocarPosicaoRecebendo(): Int {
        return if (posicoesLivresRecebendo.isNotEmpty()) {
            posicoesLivresRecebendo.removeAt(0)
        } else {
            val posicao = proximaPosicaoRecebendo
            proximaPosicaoRecebendo = (proximaPosicaoRecebendo + 1) % MAX_INCOMING
            posicao
        }
    }

    private fun alocarPosicaoEmAndamento(): Int {
        return if (posicoesLivresEmAndamento.isNotEmpty()) {
            posicoesLivresEmAndamento.removeAt(0)
        } else {
            val posicao = proximaPosicaoEmAndamento
            proximaPosicaoEmAndamento = (proximaPosicaoEmAndamento + 1) % MAX_ONGOING
            posicao
        }
    }

    private fun alocarPosicaoPerdidas(): Int {
        return if (posicoesLivresPerdidas.isNotEmpty()) {
            posicoesLivresPerdidas.removeAt(0)
        } else {
            val posicao = proximaPosicaoPerdidas
            proximaPosicaoPerdidas = (proximaPosicaoPerdidas + 1) % MAX_MISSED
            posicao
        }
    }

    // ==================== MÉTODOS PARA GERENCIAR TRANSIÇÕES ====================

    /**
     * Registra uma chamada como recebendo.
     * Chamado quando uma nova chamada chega.
     */
    @Synchronized
    fun adicionarChamadaRecebendo(chamadaId: Int) {
        if (!mapaRecebendo.containsKey(chamadaId)) {
            mapaRecebendo[chamadaId] = alocarPosicaoRecebendo()
        }
    }

    /**
     * Move uma chamada de recebendo para em andamento.
     * Chamado quando o usuário atende a chamada.
     */
    @Synchronized
    fun moverParaEmAndamento(chamadaId: Int) {
        // Remove de recebendo e libera a posição
        mapaRecebendo.remove(chamadaId)?.let { posicao ->
            posicoesLivresRecebendo.add(posicao)
        }
        // Adiciona em andamento
        if (!mapaEmAndamento.containsKey(chamadaId)) {
            mapaEmAndamento[chamadaId] = alocarPosicaoEmAndamento()
        }
    }

    /**
     * Move uma chamada de recebendo para perdida.
     * Chamado quando a chamada não é atendida (timeout).
     */
    @Synchronized
    fun moverParaPerdida(chamadaId: Int) {
        // Remove de recebendo e libera a posição
        mapaRecebendo.remove(chamadaId)?.let { posicao ->
            posicoesLivresRecebendo.add(posicao)
        }
        // Adiciona em perdidas
        if (!mapaPerdidas.containsKey(chamadaId)) {
            mapaPerdidas[chamadaId] = alocarPosicaoPerdidas()
        }
    }

    /**
     * Remove uma chamada de recebendo (recusada ou cancelada).
     */
    @Synchronized
    fun removerChamadaRecebendo(chamadaId: Int) {
        mapaRecebendo.remove(chamadaId)?.let { posicao ->
            posicoesLivresRecebendo.add(posicao)
        }
    }

    /**
     * Remove uma chamada de em andamento (encerrada).
     */
    @Synchronized
    fun removerChamadaEmAndamento(chamadaId: Int) {
        mapaEmAndamento.remove(chamadaId)?.let { posicao ->
            posicoesLivresEmAndamento.add(posicao)
        }
    }

    /**
     * Remove uma chamada de perdidas (usuário visualizou/descartou).
     */
    @Synchronized
    fun removerChamadaPerdida(chamadaId: Int) {
        mapaPerdidas.remove(chamadaId)?.let { posicao ->
            posicoesLivresPerdidas.add(posicao)
        }
    }

    /**
     * Limpa uma chamada de todas as listas.
     * Chamado quando a chamada é finalizada completamente.
     */
    @Synchronized
    fun limparChamada(chamadaId: Int) {
        mapaRecebendo.remove(chamadaId)?.let { posicao ->
            posicoesLivresRecebendo.add(posicao)
        }
        mapaEmAndamento.remove(chamadaId)?.let { posicao ->
            posicoesLivresEmAndamento.add(posicao)
        }
        mapaPerdidas.remove(chamadaId)?.let { posicao ->
            posicoesLivresPerdidas.add(posicao)
        }
    }

    /**
     * Limpa todas as chamadas de todas as listas.
     * Chamado ao resetar o serviço.
     */
    @Synchronized
    fun limparTodasChamadas() {
        mapaRecebendo.clear()
        mapaEmAndamento.clear()
        mapaPerdidas.clear()
        posicoesLivresRecebendo.clear()
        posicoesLivresEmAndamento.clear()
        posicoesLivresPerdidas.clear()
        proximaPosicaoRecebendo = 0
        proximaPosicaoEmAndamento = 0
        proximaPosicaoPerdidas = 0
    }

    // ==================== MÉTODOS DE CONSULTA ====================

    /** Retorna set de chamadaIds recebendo */
    @Synchronized
    fun getChamadasRecebendo(): Set<Int> = mapaRecebendo.keys.toSet()

    /** Retorna set de chamadaIds em andamento */
    @Synchronized
    fun getChamadasEmAndamento(): Set<Int> = mapaEmAndamento.keys.toSet()

    /** Retorna set de chamadaIds perdidas */
    @Synchronized
    fun getChamadasPerdidas(): Set<Int> = mapaPerdidas.keys.toSet()

    /** Verifica se há alguma chamada ativa (recebendo ou em andamento) */
    @Synchronized
    fun temChamadaAtiva(): Boolean {
        return mapaRecebendo.isNotEmpty() || mapaEmAndamento.isNotEmpty()
    }

    /** Verifica se uma chamada específica está em alguma lista */
    @Synchronized
    fun chamadaExiste(chamadaId: Int): Boolean {
        return mapaRecebendo.containsKey(chamadaId) ||
                mapaEmAndamento.containsKey(chamadaId) ||
                mapaPerdidas.containsKey(chamadaId)
    }

    /**
     * Retorna todos os IDs de notificação que podem estar ativos para uma chamada.
     * Útil para cancelar todas as notificações de uma chamada.
     */
    @Synchronized
    fun getAllNotificationIds(chamadaId: Int): List<Int> {
        val ids = mutableListOf<Int>()
        mapaRecebendo[chamadaId]?.let { ids.add(NOTIFICATION_ID_CHAMADA_INCOMING + it) }
        mapaEmAndamento[chamadaId]?.let { ids.add(NOTIFICATION_ID_CHAMADA_ONGOING + it) }
        mapaPerdidas[chamadaId]?.let { ids.add(NOTIFICATION_ID_CHAMADA_MISSED + it) }
        return ids
    }
}
