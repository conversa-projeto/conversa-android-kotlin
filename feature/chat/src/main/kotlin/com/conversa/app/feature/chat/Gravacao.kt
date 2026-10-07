package com.conversa.app.feature.chat

import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.anexos.ArquivosLocais
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.media.GravadorAudio
import com.conversa.app.core.media.PlayerAudio
import com.conversa.app.core.media.juntarTrechos
import com.conversa.app.core.model.TipoConteudo
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gravação de áudio no campo (ANX-11). */
sealed interface EstadoGravacao {
    data object Parada : EstadoGravacao

    /**
     * Gravando. [travada]: com a barra (a pessoa soltou o botão rápido ou arrastou para
     * cima); senão, ainda segurando o microfone. [niveis]: os últimos níveis do microfone.
     */
    data class Gravando(val duracaoMs: Long, val travada: Boolean, val niveis: List<Float> = emptyList()) : EstadoGravacao

    /** Pausada: dá para ouvir (pela chave [chaveOuvir] no player), continuar, enviar ou descartar. */
    data class Pausada(val duracaoMs: Long, val chaveOuvir: String) : EstadoGravacao
}

/**
 * Gravação de áudio de uma conversa (TODO 4.6, ANX-11, ENV-16):
 * - cada pausa fecha um trecho; ouvir e enviar juntam os trechos ([juntarTrechos]);
 * - enquanto a gravação está aberta (gravando ou pausada), avisa "gravando" a cada 2,5 s;
 * - enviada como conteúdo tipo 5 (`audio-<hora>.m4a`); menos de 1 s é descartada.
 */
class ControleGravacao(
    private val escopo: CoroutineScope,
    private val conversaId: Long,
    private val gravador: GravadorAudio,
    private val arquivos: ArquivosLocais,
    private val envio: EnvioMensagens,
    private val mensagens: MensagensRepositorio,
    private val player: PlayerAudio,
    private val relogio: Clock,
    private val es: CoroutineDispatcher,
    private val avisar: suspend (EventoChat) -> Unit,
    private val juntar: (List<File>, File) -> Boolean = ::juntarTrechos,
) {
    private val _estado = MutableStateFlow<EstadoGravacao>(EstadoGravacao.Parada)
    val estado: StateFlow<EstadoGravacao> = _estado.asStateFlow()

    /** No player, o "ouvir" fica entre os áudios desta conversa (ver [chaveDoAudio]). */
    private val chaveOuvir = "$conversaId:gravacao"

    private var pasta: File? = null
    private val trechos = mutableListOf<File>()
    private var duracaoFechadaMs = 0L
    private var inicioTrechoMs = 0L
    private var acompanhamento: Job? = null
    private var aviso: Job? = null

    /** Apertou o microfone (a permissão já foi dada). */
    fun iniciar() {
        if (_estado.value != EstadoGravacao.Parada) return
        player.parar()
        pasta = arquivos.novaPastaGravacao()
        duracaoFechadaMs = 0
        if (!abrirTrecho()) {
            limparArquivos()
            escopo.launch { avisar(EventoChat.MicrofoneIndisponivel) }
            return
        }
        _estado.value = EstadoGravacao.Gravando(0, travada = false)
        aviso = escopo.launch {
            while (isActive) {
                mensagens.avisarGravando(conversaId)
                delay(INTERVALO_AVISO_MS)
            }
        }
        acompanhar()
    }

    fun travar() {
        val atual = _estado.value as? EstadoGravacao.Gravando ?: return
        _estado.value = atual.copy(travada = true)
    }

    fun pausar() {
        if (_estado.value !is EstadoGravacao.Gravando) return
        fecharTrecho()
        _estado.value = EstadoGravacao.Pausada(duracaoFechadaMs, chaveOuvir)
    }

    fun continuar() {
        if (_estado.value !is EstadoGravacao.Pausada) return
        pararOuvir()
        if (!abrirTrecho()) {
            escopo.launch { avisar(EventoChat.MicrofoneIndisponivel) }
            return
        }
        _estado.value = EstadoGravacao.Gravando(duracaoFechadaMs, travada = true)
        acompanhar()
    }

    /** Pausada: ouve o que já foi gravado (de novo = pausa/continua). */
    fun ouvir() {
        if (_estado.value !is EstadoGravacao.Pausada) return
        val noPlayer = player.estado.value
        if (noPlayer.chave == chaveOuvir) {
            if (noPlayer.tocando) player.pausar() else player.continuar()
            return
        }
        val destino = File(pasta ?: return, "ouvir.m4a")
        val lista = trechos.toList()
        escopo.launch {
            val pronto = withContext(es) { juntar(lista, destino) }
            if (pronto && _estado.value is EstadoGravacao.Pausada) player.tocar(chaveOuvir, destino.toURI().toString())
        }
    }

    fun enviar() {
        val atual = _estado.value
        if (atual == EstadoGravacao.Parada) return
        if (atual is EstadoGravacao.Gravando) fecharTrecho()
        encerrar()
        val duracao = duracaoFechadaMs
        val lista = trechos.toList()
        val pastaAtual = pasta ?: return
        trechos.clear()
        pasta = null
        _estado.value = EstadoGravacao.Parada
        if (duracao < MINIMO_MS || lista.isEmpty()) {
            pastaAtual.deleteRecursively()
            escopo.launch { avisar(EventoChat.GravacaoCurta) }
            return
        }
        escopo.launch {
            val final = File(pastaAtual, "audio-${relogio.millis()}.m4a")
            if (!withContext(es) { juntar(lista, final) }) {
                pastaAtual.deleteRecursively()
                avisar(EventoChat.GravacaoFalhou)
                return@launch
            }
            withContext(es) { lista.forEach { it.delete() } }
            val anexo = AnexoLocal(arquivos.uriCompartilhado(final), final.name, final.length(), MIME, TipoConteudo.GRAVACAO_AUDIO)
            envio.enviar(conversaId, "", listOf(anexo))
            avisar(EventoChat.RolarAoFim)
        }
    }

    /** Descartar, arrastar para o lado ou sair da conversa: apaga tudo, nada é enviado. */
    fun descartar() {
        val atual = _estado.value
        if (atual == EstadoGravacao.Parada) return
        if (atual is EstadoGravacao.Gravando) gravador.parar()
        encerrar()
        limparArquivos()
        _estado.value = EstadoGravacao.Parada
    }

    private fun abrirTrecho(): Boolean {
        val arquivo = File(pasta ?: return false, "trecho-${trechos.size}.m4a")
        if (!gravador.iniciar(arquivo)) return false
        trechos += arquivo
        inicioTrechoMs = relogio.millis()
        return true
    }

    /** Fecha o trecho em gravação; um trecho sem dados (curto demais) é jogado fora. */
    private fun fecharTrecho() {
        acompanhamento?.cancel()
        if (gravador.parar()) {
            duracaoFechadaMs += relogio.millis() - inicioTrechoMs
        } else {
            trechos.removeLastOrNull()?.delete()
        }
    }

    /** Tempo e nível do microfone para a tela, a cada [INTERVALO_TELA_MS]. */
    private fun acompanhar() {
        acompanhamento?.cancel()
        acompanhamento = escopo.launch {
            while (isActive) {
                delay(INTERVALO_TELA_MS)
                val atual = _estado.value as? EstadoGravacao.Gravando ?: break
                _estado.value = atual.copy(
                    duracaoMs = duracaoFechadaMs + relogio.millis() - inicioTrechoMs,
                    niveis = (atual.niveis + gravador.nivel()).takeLast(MAX_NIVEIS),
                )
            }
        }
    }

    private fun pararOuvir() {
        if (player.estado.value.chave == chaveOuvir) player.parar()
    }

    private fun encerrar() {
        acompanhamento?.cancel()
        aviso?.cancel()
        pararOuvir()
    }

    private fun limparArquivos() {
        pasta?.deleteRecursively()
        pasta = null
        trechos.clear()
    }

    companion object {
        /** Igual ao "digitando" (web: `setInterval(enviarGravando, 2500)`). */
        const val INTERVALO_AVISO_MS = 2_500L
        const val INTERVALO_TELA_MS = 100L

        /** Como o app legado: menos de 1 s não vale a pena enviar. */
        const val MINIMO_MS = 1_000L
        const val MAX_NIVEIS = 32
        const val MIME = "audio/mp4"
    }
}
