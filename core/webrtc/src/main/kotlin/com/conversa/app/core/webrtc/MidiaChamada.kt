package com.conversa.app.core.webrtc

/** O que o aparelho conseguiu abrir para transmitir. */
enum class MidiaLocal {
    /** Nem microfone: só assiste ("somente recepção"). */
    NENHUMA,
    AUDIO,
    AUDIO_VIDEO,
}

/**
 * Mídia de uma chamada pelo MediaMTX (contrato §9.9): publica a minha (WHIP) e
 * assina a de cada participante (WHEP). Quem decide **quando** é o gerenciador de
 * chamadas; esta interface só executa.
 *
 * [encerrar] é idempotente e aborta o que estiver em andamento: um [abrirLocal] ou
 * [publicar] que termine depois dele não deixa nada aberto.
 */
interface MidiaChamada {
    /**
     * Abre o microfone e, se [video], a câmera. Sem câmera, fica só com áudio;
     * sem microfone, [MidiaLocal.NENHUMA].
     */
    suspend fun abrirLocal(video: Boolean): MidiaLocal

    /** Publica a mídia local em `call-<chamada>-u-<eu>`. Lança exceção se falhar. */
    suspend fun publicar(chamadaId: Long, eu: Long)

    /**
     * Deixa assinados exatamente estes [participantes]: assina quem falta, solta
     * quem saiu e refaz quem caiu ou está sem trilha.
     */
    suspend fun sincronizar(chamadaId: Long, participantes: Set<Long>, comVideo: Boolean)

    /** Refaz a assinatura de um participante (ele republicou, ex.: ligou o vídeo). */
    suspend fun reassinar(usuarioId: Long)

    fun desconectar(usuarioId: Long)

    /**
     * Passa a chamada para vídeo: abre a câmera e republica. Com [transmitir] falso,
     * a câmera fica desligada (só assiste). Devolve se conseguiu a câmera. [aoEtapa]
     * conta o andamento (FC-728); pode ser chamada fora da vez de quem pediu.
     */
    suspend fun ativarVideo(transmitir: Boolean, aoEtapa: (EtapaVideo) -> Unit = {}): Boolean

    fun microfone(ligado: Boolean)

    fun camera(ligada: Boolean)

    /** `DELETE` dos recursos WHIP/WHEP e `dispose()` de trilhas e conexões. */
    fun encerrar()
}

/** Etapas de ligar o vídeo numa chamada de áudio, para a tela mostrar o andamento (FC-728, como o web). */
enum class EtapaVideo { CAMERA, ENVIANDO, RECEBENDO }
