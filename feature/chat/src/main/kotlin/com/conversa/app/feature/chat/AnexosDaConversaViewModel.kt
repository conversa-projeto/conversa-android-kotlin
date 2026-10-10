package com.conversa.app.feature.chat

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.ArquivosLocais
import com.conversa.app.core.data.anexos.DownloadsRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.model.AnexoDaConversa
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.DirecaoAnexos
import com.conversa.app.core.model.FiltroAnexos
import com.conversa.app.core.model.acabouAPagina
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.estado.EventosUnicos
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class AnexosUiState(
    val titulo: String = "",
    val direcao: DirecaoAnexos = DirecaoAnexos.TODOS,
    val filtro: FiltroAnexos = FiltroAnexos.TODOS,
    val itens: List<AnexoDaConversa> = emptyList(),
    val carregando: Boolean = true,
    /** Veio menos que uma página: não há mais o que buscar. */
    val fim: Boolean = false,
    val erro: String? = null,
)

sealed interface EventoAnexos {
    data class AbrirArquivo(val arquivo: File, val mime: String?) : EventoAnexos

    data class AbrirPdf(val arquivo: File, val conteudo: Conteudo) : EventoAnexos

    data class Compartilhar(val arquivo: File) : EventoAnexos

    data class Baixando(val nome: String) : EventoAnexos

    /** Android 9: "Salvar como". */
    data class EscolherOndeSalvar(val conteudo: Conteudo) : EventoAnexos

    data class Erro(val mensagem: String) : EventoAnexos
}

private data class Pagina(
    val direcao: DirecaoAnexos = DirecaoAnexos.TODOS,
    val filtro: FiltroAnexos = FiltroAnexos.TODOS,
    val itens: List<AnexoDaConversa> = emptyList(),
    val carregando: Boolean = true,
    val fim: Boolean = false,
    val erro: String? = null,
)

/**
 * Anexos da conversa (8.6, FC-809, ANX-13), como a `AnexosLista.vue` do web: filtros de
 * direção e de tipo, 60 por página (do mais novo ao mais antigo) e as ações do chat
 * (abrir, compartilhar, baixar) para cada anexo.
 */
@HiltViewModel
class AnexosDaConversaViewModel @Inject constructor(
    salvo: SavedStateHandle,
    conversas: ConversasRepositorio,
    private val anexos: AnexosRepositorio,
    private val arquivos: ArquivosLocais,
    private val downloads: DownloadsRepositorio,
) : ViewModel() {
    val conversaId: Long = checkNotNull(salvo["conversaId"])

    private val pagina = MutableStateFlow(Pagina())
    private var busca: Job? = null

    val eventos = EventosUnicos<EventoAnexos>()

    val estado: StateFlow<AnexosUiState> = combine(conversas.observar(conversaId), pagina) { conversa, p ->
        AnexosUiState(
            titulo = conversa?.titulo.orEmpty(),
            direcao = p.direcao,
            filtro = p.filtro,
            itens = p.itens,
            carregando = p.carregando,
            fim = p.fim,
            erro = p.erro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnexosUiState())

    init {
        carregar(mais = false)
    }

    fun mudarDirecao(direcao: DirecaoAnexos) {
        if (direcao == pagina.value.direcao) return
        pagina.update { Pagina(direcao = direcao, filtro = it.filtro) }
        carregar(mais = false)
    }

    fun mudarFiltro(filtro: FiltroAnexos) {
        if (filtro == pagina.value.filtro) return
        pagina.update { Pagina(direcao = it.direcao, filtro = filtro) }
        carregar(mais = false)
    }

    /** Perto do fim da lista: a próxima página, se houver e se nada estiver carregando. */
    fun carregarMais() {
        val p = pagina.value
        if (p.carregando || p.fim || p.erro != null) return
        carregar(mais = true)
    }

    fun tentarDeNovo() {
        pagina.update { it.copy(erro = null) }
        carregar(mais = pagina.value.itens.isNotEmpty())
    }

    /** Filtro novo cancela a busca anterior: a resposta velha não mistura as listas. */
    private fun carregar(mais: Boolean) {
        if (!mais) busca?.cancel()
        val antes = if (mais) pagina.value.itens.lastOrNull()?.anexoId ?: 0 else 0
        val (direcao, filtro) = pagina.value.let { it.direcao to it.filtro }
        pagina.update { it.copy(carregando = true, erro = null) }
        busca = viewModelScope.launch {
            anexos.daConversa(conversaId, direcao, filtro, antes).fold(
                onSuccess = { novos ->
                    pagina.update {
                        it.copy(itens = if (mais) it.itens + novos else novos, carregando = false, fim = acabouAPagina(novos.size))
                    }
                },
                onFailure = { falha -> pagina.update { it.copy(carregando = false, erro = falha.paraErroApi().mensagemAmigavel()) } },
            )
        }
    }

    /** PDF no visualizador do app; os outros, com outro app (baixados para o cache uma vez). */
    fun abrir(conteudo: Conteudo) {
        viewModelScope.launch {
            arquivos.baixar(conteudo.conteudo, nomeDe(conteudo))
                .onSuccess {
                    eventos.enviar(if (ehPdf(conteudo)) EventoAnexos.AbrirPdf(it, conteudo) else EventoAnexos.AbrirArquivo(it, null))
                }
                .onFailure { eventos.enviar(EventoAnexos.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    fun compartilhar(conteudo: Conteudo) {
        viewModelScope.launch {
            arquivos.baixar(conteudo.conteudo, nomeDe(conteudo))
                .onSuccess { eventos.enviar(EventoAnexos.Compartilhar(it)) }
                .onFailure { eventos.enviar(EventoAnexos.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    /** Como o "Baixar" do chat: Android 10+ salva em Downloads/Conversa; o 9 pergunta onde. */
    fun baixar(conteudo: Conteudo) {
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                downloads.salvarEmDownloads(conteudo.conteudo, nomeDe(conteudo), null)
                eventos.enviar(EventoAnexos.Baixando(nomeDe(conteudo)))
            } else {
                eventos.enviar(EventoAnexos.EscolherOndeSalvar(conteudo))
            }
        }
    }

    fun salvarEm(uri: String, conteudo: Conteudo) {
        downloads.salvarEm(uri, conteudo.conteudo, nomeDe(conteudo), null)
        viewModelScope.launch { eventos.enviar(EventoAnexos.Baixando(nomeDe(conteudo))) }
    }

    suspend fun urlDoVideo(conteudo: Conteudo): String? = anexos.url(conteudo.conteudo).getOrNull()

    private fun nomeDe(conteudo: Conteudo) = conteudo.nome.ifBlank { conteudo.conteudo }
}
