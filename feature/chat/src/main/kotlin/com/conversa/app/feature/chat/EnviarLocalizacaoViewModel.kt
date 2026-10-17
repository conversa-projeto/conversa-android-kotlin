package com.conversa.app.feature.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.mapas.MapasRepositorio
import com.conversa.app.core.model.LIMITE_NOME_PONTO
import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.RotaCarro
import com.conversa.app.core.model.formatarCoordenadas
import com.conversa.app.core.model.lerCoordenadas
import com.conversa.app.core.model.montarLocalizacao
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Troca o texto do campo com o cursor no começo: o endereço longo aparece pelo início. */
private fun TextFieldState.trocarTexto(novo: String) = edit {
    replace(0, length, novo)
    placeCursorBeforeCharAt(0)
}

/** Local (um ponto) ou trajeto (origem e destino). */
enum class ModoLocalizacao { LOCAL, TRAJETO }

/** Por que a posição do aparelho não veio (os textos do web). */
enum class FalhaPosicao { NEGADA, INDISPONIVEL, SEM_SENSOR }

/** A linha embaixo do mapa, em vermelho. */
sealed interface AvisoLocalizacao {
    data object NadaEncontrado : AvisoLocalizacao

    data object BuscaFalhou : AvisoLocalizacao

    data class SemPosicao(val falha: FalhaPosicao) : AvisoLocalizacao
}

/** O que a janela pede ao mapa: olhar um ponto (zoom 16) ou enquadrar tudo, como o `MapaLeaflet`. */
sealed interface PedidoMapa {
    data class Centralizar(val ponto: PontoMapa) : PedidoMapa

    data object Ajustar : PedidoMapa
}

/** A posição do aparelho; falha com [SemPosicaoException]. */
fun interface FontePosicao {
    suspend fun posicao(): PontoMapa
}

class SemPosicaoException(val falha: FalhaPosicao) : Exception(falha.name)

data class EstadoEnviarLocalizacao(
    val modo: ModoLocalizacao = ModoLocalizacao.LOCAL,
    /** O local (0), ou a origem (0) e o destino (1). */
    val pontos: List<PontoMapa?> = listOf(null, null),
    /** O campo que recebe o toque no mapa. */
    val ativo: Int = 0,
    val resultados: List<PontoMapa> = emptyList(),
    val campoResultados: Int? = null,
    val buscando: Boolean = false,
    val calculando: Boolean = false,
    /** O campo esperando a posição do aparelho. */
    val localizando: Int? = null,
    val aviso: AvisoLocalizacao? = null,
    val rota: RotaCarro? = null,
) {
    val indices: List<Int> get() = if (modo == ModoLocalizacao.LOCAL) listOf(0) else listOf(0, 1)
    val pontosMarcados: List<PontoMapa> get() = indices.mapNotNull { pontos[it] }
    val pronta: Boolean get() = indices.all { pontos[it] != null } && !calculando

    /** O que o mapa desenha: os pontos marcados e a rota, se houver. */
    val noMapa: Localizacao get() = Localizacao(pontosMarcados, rota?.rota)

    /** O que vai na mensagem; nulo enquanto falta um ponto ou a rota está saindo. */
    fun resultado(): Localizacao? = if (pronta) montarLocalizacao(pontosMarcados, rota.takeIf { modo == ModoLocalizacao.TRAJETO }) else null
}

/**
 * "Enviar localização" (ENV-30, FC-528; web `LocalizacaoModal.vue`): um local ou um trajeto, pela
 * posição do aparelho, pela busca de endereço, por coordenadas digitadas ou tocando no mapa. Sem
 * GPS, as outras formas bastam. O trajeto calcula a rota de carro 300 ms depois do último ponto;
 * sem resposta, vai a distância em linha reta.
 */
@HiltViewModel
class EnviarLocalizacaoViewModel @Inject constructor(private val mapas: MapasRepositorio, private val posicao: FontePosicao) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEnviarLocalizacao())
    val estado: StateFlow<EstadoEnviarLocalizacao> = _estado.asStateFlow()

    /** O texto de cada campo, no estado do próprio campo (síncrono, o cursor não pula). */
    val textos = listOf(TextFieldState(), TextFieldState())

    private val pedidosMapa = Channel<PedidoMapa>(Channel.BUFFERED)
    val mapa: Flow<PedidoMapa> = pedidosMapa.receiveAsFlow()

    private var aberta = false
    private val pedidosNome = arrayOfNulls<Job>(2)
    private var busca: Job? = null
    private var calculoRota: Job? = null
    private var localizacao: Job? = null

    /** A janela abriu: começa do zero (girar a tela não conta). Devolve se é uma abertura nova. */
    fun abrir(): Boolean {
        if (aberta) return false
        aberta = true
        cancelarPedidos()
        _estado.value = EstadoEnviarLocalizacao()
        textos.forEach { it.clearText() }
        return true
    }

    fun fechar() {
        aberta = false
        cancelarPedidos()
    }

    private fun cancelarPedidos() {
        pedidosNome.forEach { it?.cancel() }
        pedidosNome.fill(null)
        busca?.cancel()
        calculoRota?.cancel()
        localizacao?.cancel()
    }

    fun trocarModo(modo: ModoLocalizacao) {
        _estado.update { atual ->
            val ativo = when {
                modo == ModoLocalizacao.LOCAL -> 0
                atual.pontos[0] != null && atual.pontos[1] == null -> 1
                else -> atual.ativo
            }
            atual.copy(modo = modo, ativo = ativo, resultados = emptyList(), aviso = null)
        }
        recalcularRota()
        pedidosMapa.trySend(PedidoMapa.Ajustar)
    }

    /** O campo ganhou o foco: o próximo toque no mapa é dele. */
    fun ativar(indice: Int) = _estado.update { it.copy(ativo = indice) }

    fun fecharResultados() = _estado.update { it.copy(resultados = emptyList(), campoResultados = null) }

    /**
     * Marca o ponto do campo [indice]. Com [buscarNome] (posição, toque, coordenadas), pede o nome
     * do lugar; o pedido antigo do mesmo campo é cancelado. Marcou a origem com o destino vazio:
     * o próximo toque é o destino.
     */
    fun definirPonto(indice: Int, ponto: PontoMapa, buscarNome: Boolean) {
        val nome = ponto.nome?.trim()?.takeIf { it.isNotEmpty() }?.take(LIMITE_NOME_PONTO)
        val novo = PontoMapa(ponto.lat, ponto.lng, nome)
        textos[indice].trocarTexto(nome ?: formatarCoordenadas(ponto.lat, ponto.lng))
        pedidosNome[indice]?.cancel()
        pedidosNome[indice] = null
        _estado.update { atual ->
            val pontos = atual.pontos.toMutableList().also { it[indice] = novo }
            val ativo = if (atual.modo == ModoLocalizacao.TRAJETO && indice == 0 && pontos[1] == null) 1 else atual.ativo
            atual.copy(pontos = pontos, ativo = ativo, resultados = emptyList(), campoResultados = null, aviso = null)
        }
        if (buscarNome) {
            pedidosNome[indice] = viewModelScope.launch {
                val achado = mapas.nomeDoLugar(novo) ?: return@launch
                _estado.update { atual ->
                    val marcado = atual.pontos[indice] ?: return@update atual
                    atual.copy(pontos = atual.pontos.toMutableList().also { it[indice] = marcado.copy(nome = achado) })
                }
                textos[indice].trocarTexto(achado)
            }
        }
        recalcularRota()
    }

    /** Enter ou a lupa: coordenadas marcam na hora; o resto vai para a busca de endereço. */
    fun buscar(indice: Int) {
        val texto = textos[indice].text.toString().trim()
        if (texto.isEmpty()) return
        _estado.update { it.copy(ativo = indice) }
        lerCoordenadas(texto)?.let { ponto ->
            definirPonto(indice, ponto, buscarNome = true)
            pedidosMapa.trySend(PedidoMapa.Centralizar(ponto))
            return
        }
        busca?.cancel()
        _estado.update { it.copy(buscando = true, aviso = null) }
        busca = viewModelScope.launch {
            val resposta = mapas.buscarEndereco(texto)
            _estado.update { atual ->
                resposta.fold(
                    onSuccess = { lista ->
                        atual.copy(
                            buscando = false,
                            resultados = lista,
                            campoResultados = indice,
                            aviso = if (lista.isEmpty()) AvisoLocalizacao.NadaEncontrado else null,
                        )
                    },
                    onFailure = { atual.copy(buscando = false, aviso = AvisoLocalizacao.BuscaFalhou) },
                )
            }
        }
    }

    fun escolherResultado(indice: Int, resultado: PontoMapa) {
        definirPonto(indice, resultado, buscarNome = false)
        val atual = _estado.value
        if (atual.modo == ModoLocalizacao.LOCAL || atual.pontos[if (indice == 0) 1 else 0] == null) {
            pedidosMapa.trySend(PedidoMapa.Centralizar(resultado))
        }
    }

    /**
     * "Usar minha posição" (com a permissão já dada). [silencioso]: a tentativa ao abrir, que não
     * avisa quando falha; a dica já explica as outras formas.
     */
    fun usarMinhaPosicao(indice: Int, silencioso: Boolean = false) {
        localizacao?.cancel()
        _estado.update { it.copy(ativo = indice, localizando = indice) }
        localizacao = viewModelScope.launch {
            try {
                val ponto = posicao.posicao()
                _estado.update { it.copy(localizando = null) }
                definirPonto(indice, ponto, buscarNome = true)
                pedidosMapa.trySend(PedidoMapa.Centralizar(ponto))
            } catch (e: SemPosicaoException) {
                _estado.update { it.copy(localizando = null, aviso = if (silencioso) it.aviso else AvisoLocalizacao.SemPosicao(e.falha)) }
            }
        }
    }

    /** A pessoa negou a permissão de localização do aparelho. */
    fun posicaoNegada(indice: Int) = _estado.update {
        it.copy(ativo = indice, localizando = null, aviso = AvisoLocalizacao.SemPosicao(FalhaPosicao.NEGADA))
    }

    /** Rota pelas ruas quando há origem e destino; o pedido anterior é cancelado. */
    private fun recalcularRota() {
        calculoRota?.cancel()
        val atual = _estado.value
        val origem = atual.pontos[0]
        val destino = atual.pontos[1]
        if (atual.modo != ModoLocalizacao.TRAJETO || origem == null || destino == null) {
            _estado.update { it.copy(rota = null, calculando = false) }
            return
        }
        // A linha antiga sai: os marcadores já mudaram.
        _estado.update { it.copy(rota = null, calculando = true) }
        calculoRota = viewModelScope.launch {
            delay(ESPERA_ROTA_MS)
            val rota = mapas.calcularRota(origem, destino)
            _estado.update { it.copy(rota = rota, calculando = false) }
            pedidosMapa.trySend(PedidoMapa.Ajustar)
        }
    }

    companion object {
        /** Como o web: espera os pontos pararem de mudar antes de pedir a rota. */
        const val ESPERA_ROTA_MS = 300L
    }
}
