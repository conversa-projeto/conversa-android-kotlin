package com.conversa.app.feature.chamada

import com.conversa.app.core.model.Chamada
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.webrtc.MidiaLocal
import java.time.Instant

/** Fases da chamada (plano §3.4). */
enum class FaseChamada {
    INATIVO,

    /** Liguei e ninguém atendeu ainda. */
    CHAMANDO,

    /** Está tocando aqui (ou buscando os dados para tocar). */
    RECEBENDO,

    /** Atendi: abrindo a mídia, entrando e publicando. */
    CONECTANDO,
    ATIVA,

    /** Desliguei: mídia cortada, avisando o servidor. */
    ENCERRANDO,
}

/** Como os participantes aparecem (como no web): todos lado a lado, um grande com os outros numa faixa, ou só um. */
enum class ModoExibicao { GRADE, DESTAQUE, UNICA }

/** [destaque] é o participante grande (destaque) ou o único (tela única); na grade, nulo. */
data class Exibicao(val modo: ModoExibicao = ModoExibicao.GRADE, val destaque: Long? = null)

/** Alguém ligou o vídeo numa chamada de áudio (WS 56): "Apenas assistir" ou "Transmitir também". */
data class PedidoVideo(val usuarioId: Long, val nome: String)

/** Tudo o que a tela, a notificação e o Telecom precisam saber da chamada. */
data class EstadoChamada(
    val fase: FaseChamada = FaseChamada.INATIVO,
    /** Na chamada recebida existe antes dos [dados] (enquanto busca). */
    val chamadaId: Long? = null,
    val dados: Chamada? = null,
    val tipo: TipoChamada = TipoChamada.AUDIO,
    /** Quem ligou (na efetuada, eu). */
    val remetenteId: Long? = null,
    val midiaLocal: MidiaLocal = MidiaLocal.NENHUMA,
    val microfoneLigado: Boolean = true,
    val cameraLigada: Boolean = false,
    /** Quando ficou ativa: a duração é calculada a partir daqui (tela e cronômetro da notificação). */
    val ativaDesde: Instant? = null,
    val pedidoVideo: PedidoVideo? = null,
    /** Conversa do chat da chamada (`conversa_chat_id` ou WS 57 `{acao:"chat"}`). */
    val conversaChatId: Long? = null,
    /** Modo de exibição (6.11). Quem escolhe "só assistir" abre em tela única em quem transmite. */
    val exibicao: Exibicao = Exibicao(),
) {
    /** Liguei, atendi ou estou desligando (o "ocupado" do web, sem contar o toque). */
    val emChamada: Boolean
        get() = fase == FaseChamada.CHAMANDO ||
            fase == FaseChamada.CONECTANDO ||
            fase == FaseChamada.ATIVA ||
            fase == FaseChamada.ENCERRANDO

    /** Tocando de verdade (os dados já chegaram). */
    val tocando: Boolean get() = fase == FaseChamada.RECEBENDO && dados != null
}

/** Avisos de uma vez para a tela. */
sealed interface AvisoChamada {
    data object JaEmChamada : AvisoChamada

    data object MicrofoneIndisponivel : AvisoChamada

    /** O servidor recusou iniciar ou entrar. */
    data class Falhou(val detalhe: String?) : AvisoChamada
}
