package com.conversa.app.feature.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.AnexoLocal
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.ArquivosLocais
import com.conversa.app.core.data.anexos.Compartilhamentos
import com.conversa.app.core.data.anexos.DownloadsRepositorio
import com.conversa.app.core.data.anexos.FontesArquivo
import com.conversa.app.core.data.anexos.ResultadoDownload
import com.conversa.app.core.data.anexos.TranscricoesRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.data.mensagens.ReferenciaPendente
import com.conversa.app.core.data.notificacoes.ConversaEmTela
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.rascunhos.Rascunho
import com.conversa.app.core.data.rascunhos.RascunhosRepositorio
import com.conversa.app.core.data.rede.EconomiaDados
import com.conversa.app.core.media.GravadorAudio
import com.conversa.app.core.media.PlayerAudio
import com.conversa.app.core.model.AtividadeConversa
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.ItemChat
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.MensagensDoChat
import com.conversa.app.core.model.PREFIXO_LOCAL
import com.conversa.app.core.model.StatusDestinatario
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.atividadeDaConversa
import com.conversa.app.core.model.local
import com.conversa.app.core.model.montarItensChat
import com.conversa.app.core.model.podeReagir
import com.conversa.app.core.model.separarAgendadas
import com.conversa.app.core.network.http.mensagemAmigavel
import com.conversa.app.core.network.http.paraErroApi
import com.conversa.app.core.ui.estado.EventosUnicos
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ChatUiState(
    val conversa: Conversa? = null,
    val online: Boolean = false,
    /** Grupo: nomes dos membros (some quando alguém digita). */
    val membros: String = "",
    val atividade: AtividadeConversa = AtividadeConversa.Nenhuma,
    /** Em ordem cronológica (a tela desenha de baixo para cima). */
    val itens: List<ItemChat> = emptyList(),
    /** Minhas agendadas que ainda não saíram, por horário: o relógio ao lado do microfone (🆕 web `7322e83`). */
    val agendadas: List<Mensagem> = emptyList(),
    val eu: Long = 0,
    /** Ainda não há nada no Room e a primeira carga não terminou. */
    val carregando: Boolean = true,
    val carregandoAnteriores: Boolean = false,
    val chegouAoInicio: Boolean = false,
    /** A primeira não lida já foi decidida: a tela pode se posicionar (MSG-05). */
    val pronto: Boolean = false,
    /** Anexos escolhidos, esperando o Enviar (ANX-03). */
    val fila: List<AnexoLocal> = emptyList(),
    /** Mensagem otimista → fração enviada dos anexos. */
    val progresso: Map<Long, Float> = emptyMap(),
    /** Texto compartilhado por outro app: o campo usa uma vez ([ChatViewModel.textoUsado]). */
    val textoParaCampo: String? = null,
    /** O campo pega o foco uma vez ([ChatViewModel.campoFocado]). */
    val focarCampo: Boolean = false,
    /** Resposta (7.3) ou encaminhada do "Responder no privado" (7.6): a barra acima do campo; vai no próximo envio. */
    val respondendo: ReferenciaPendente? = null,
    /** "Ir para a mensagem" (7.5): a tela rola até ela e avisa ([ChatViewModel.chegouNaMensagem]). */
    val irPara: Long? = null,
    /** Mensagem destacada por 1,2 s depois do salto. */
    val destaque: Long? = null,
    /** Trazendo as mensagens até a original (pode levar algumas páginas): a barra de progresso. */
    val buscandoMensagem: Boolean = false,
    /** As conversas de que participo: a citação de uma encaminhada só abre a original se for uma delas. */
    val minhasConversas: Set<Long> = emptySet(),
) {
    val grupo: Boolean get() = conversa?.tipo == TipoConversa.GRUPO
}

sealed interface EventoChat {
    data class Erro(val mensagem: String) : EventoChat

    /** Depois de enviar: descer até a última mensagem. */
    data object RolarAoFim : EventoChat

    /**
     * Abrir outra conversa: a direta de uma menção (CON-06), a de uma encaminhada (7.5, com a
     * [mensagemId]), o destino de um encaminhamento ou a direta do "Responder no privado"
     * (7.6, com a mensagem pendente em [encaminharDe]).
     */
    data class AbrirConversa(val conversaId: Long, val mensagemId: Long = 0, val encaminharDe: Long = 0) : EventoChat

    /** O encaminhamento não saiu (a direta com o contato não foi criada): "Erro ao encaminhar mensagem". */
    data object EncaminharFalhou : EventoChat

    /** "Ir para a mensagem" não achou a original (7.5). */
    data object MensagemNaoLocalizada : EventoChat

    /** Arquivo acima de 1 GiB: nem entra na fila. */
    data class ArquivoGrande(val nome: String) : EventoChat

    /** Anexo baixado: a tela abre com outro app (ANX-09). */
    data class AbrirArquivo(val arquivo: java.io.File, val mime: String?) : EventoChat

    /** O player não conseguiu tocar o áudio (formato não suportado ou arquivo inválido). */
    data object AudioFalhou : EventoChat

    /** O microfone não abriu (ocupado por outro app, por exemplo). */
    data object MicrofoneIndisponivel : EventoChat

    /** Gravação com menos de 1 s: descartada. */
    data object GravacaoCurta : EventoChat

    /** Não deu para montar o arquivo da gravação. */
    data object GravacaoFalhou : EventoChat

    /** "Baixar" começou (ANX-09); o fim chega como [Salvo] ou [DownloadFalhou]. */
    data class Baixando(val nome: String) : EventoChat

    /** Salvo em Downloads/Conversa: a tela oferece "Abrir". */
    data class Salvo(val nome: String, val uri: String, val mime: String) : EventoChat

    data class DownloadFalhou(val nome: String) : EventoChat

    /** Android 9: a tela abre o "Salvar como" e devolve o URI em [ChatViewModel.salvarEm]. */
    data class EscolherOndeSalvar(val conteudo: com.conversa.app.core.model.Conteudo) : EventoChat

    /** PDF baixado para o cache: a tela abre o visualizador (4.10). */
    data class AbrirPdf(val arquivo: java.io.File, val conteudo: com.conversa.app.core.model.Conteudo) : EventoChat

    /** Anexo baixado para o cache: a tela abre o "Compartilhar" do Android. */
    data class Compartilhar(val arquivo: java.io.File, val mime: String?) : EventoChat

    /** "Copiar" numa mensagem de texto: a tela põe na área de transferência (7.6). */
    data class CopiarTexto(val texto: String) : EventoChat

    /** "Copiar" numa imagem: baixada para o cache, vai como URI do FileProvider (7.6). */
    data class CopiarImagem(val arquivo: java.io.File) : EventoChat

    /** O `DELETE /mensagem` falhou (7.4). */
    data object OcultarFalhou : EventoChat

    /** Já há 5 emojis meus nesta mensagem (7.2). */
    data object LimiteDeReacoes : EventoChat

    /** O `PUT /mensagem/reacao` falhou: "Não foi possível reagir" com o motivo. */
    data class ReagirFalhou(val motivo: String) : EventoChat
}

/** O que o campo recebe além do texto digitado (junto, para o [ChatViewModel.estado] caber num `combine`). */
/** Quanto tempo a mensagem fica destacada depois do "ir para a mensagem" (como o web). */
private const val DESTAQUE_MS = 1_200L

/** Atraso para gravar o rascunho depois da última mudança (o web também espera um pouco). */
private const val RASCUNHO_MS = 500L

/** "Ir para a mensagem": aonde a tela deve rolar e o que fica destacado. */
private data class Salto(val irPara: Long? = null, val destaque: Long? = null, val buscando: Boolean = false)

private data class ExtrasDoCampo(
    val texto: String?,
    val foco: Boolean,
    val respondendo: ReferenciaPendente?,
    val salto: Salto,
    val minhas: Set<Long>,
)

/**
 * Áudio na conversa (ANX-10), lido só pelas bolhas de áudio: a posição muda várias
 * vezes por segundo e não pode recompor a lista inteira.
 */
@Immutable
data class AudioNaConversa(
    /** Áudio carregado no player ([chaveDoAudio]), se for desta conversa. */
    val chave: String? = null,
    val tocando: Boolean = false,
    val posicaoMs: Long = 0,
    /** Áudio sendo baixado antes de tocar. */
    val baixando: String? = null,
    /** Duração de cada áudio que já tocou (a bolha mostra "--:--" até saber). */
    val duracoes: Map<String, Long> = emptyMap(),
    /** O servidor não tem transcritor: os botões "Transcrever" somem (ANX-12). */
    val transcricaoDesligada: Boolean = false,
    /** Identificador → motivo do erro do transcritor. */
    val errosTranscricao: Map<String, String> = emptyMap(),
    /** Pedidos de transcrição em andamento. */
    val pedindoTranscricao: Set<String> = emptySet(),
)

/** Identifica um áudio na tela: a mesma gravação pode estar em duas mensagens (encaminhada). */
fun chaveDoAudio(mensagem: Mensagem, conteudo: com.conversa.app.core.model.Conteudo): String =
    "${mensagem.conversaId}:${mensagem.id}:${conteudo.ordem}"

/**
 * Conversa aberta (CON-09, MSG-01…14, ENV-01, ENV-15). O Room é a fonte; ao abrir,
 * recarrega as recentes da rede (inclusive quando vem de notificação, CON-12).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    salvo: SavedStateHandle,
    private val conversas: ConversasRepositorio,
    private val contatos: ContatosRepositorio,
    presenca: PresencaRepositorio,
    sessao: SessaoRepositorio,
    private val mensagens: MensagensRepositorio,
    private val envio: EnvioMensagens,
    private val anexos: AnexosRepositorio,
    private val arquivos: ArquivosLocais,
    private val downloads: DownloadsRepositorio,
    private val transcricoes: TranscricoesRepositorio,
    private val compartilhamentos: Compartilhamentos,
    economia: EconomiaDados,
    private val emTela: ConversaEmTela,
    private val fontes: FontesArquivo,
    private val player: PlayerAudio,
    gravador: GravadorAudio,
    private val relogio: Clock,
    private val rascunhos: RascunhosRepositorio,
) : ViewModel() {
    val conversaId: Long = checkNotNull(salvo["conversaId"])
    private val eu: Long = sessao.sessao.value?.usuarioId ?: 0
    private val meuNome: String = sessao.sessao.value?.nome.orEmpty()

    private val membros = MutableStateFlow<List<MembroConversa>>(emptyList())
    private val carga = MutableStateFlow(Carga())

    /**
     * Primeira não lida, fixada na abertura (a linha "Últimas" não pula enquanto a
     * pessoa lê, MSG-03). `null` = ainda não decidida; 0 = não há.
     */
    private val primeiraNaoLida = MutableStateFlow<Long?>(null)

    val eventos = EventosUnicos<EventoChat>()

    /** Conexão lenta ou economia de dados do Android: imagens e vídeos esperam o toque (FC-414). */
    val economizarDados: StateFlow<Boolean> = economia.ativa

    private val nomes = combine(contatos.observarOutros(), membros) { pessoas, lista ->
        pessoas.associate { it.id to it.nome } + lista.associate { it.usuarioId to it.nome }
    }

    private val presente = combine(presenca.online, presenca.digitando, presenca.gravando, nomes) { online, digitando, gravando, n ->
        Presente(online, digitando[conversaId].orEmpty(), gravando[conversaId].orEmpty(), n)
    }

    private val fila = MutableStateFlow<List<AnexoLocal>>(emptyList())
    private val textoParaCampo = MutableStateFlow<String?>(null)

    // Rascunho por conversa (FC-519): o texto do campo com as menções cruas.
    private val textoRascunho = MutableStateFlow("")

    /** Visto depois de restaurar; nulo = o rascunho salvo ainda não foi lido (sair antes não grava nada). */
    private var ultimoRascunho: Rascunho? = null

    /** Chat da chamada recém-criado: abre com o cursor no campo, como o web (6.13). */
    private val focarCampo = MutableStateFlow(salvo.get<Boolean>("focar") == true)
    private val respondendo = MutableStateFlow<ReferenciaPendente?>(null)
    private val salto = MutableStateFlow(Salto())
    private val minhasConversas = conversas.observarTodas().map { lista -> lista.mapTo(mutableSetOf()) { it.id } }

    private val extrasDoCampo =
        combine(textoParaCampo, focarCampo, respondendo, salto, minhasConversas) { texto, foco, resposta, s, minhas ->
            ExtrasDoCampo(texto, foco, resposta, s, minhas)
        }

    /**
     * O chat sem as agendadas (🆕 web `7322e83`): elas ficam no relógio ao lado do microfone.
     * Na hora da próxima a lista é refeita e ela entra no chat, sem esperar o Room mudar.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mensagensDoChat = mensagens.observar(conversaId).flatMapLatest { lista ->
        flow<MensagensDoChat> {
            var agora = relogio.instant()
            while (true) {
                val separadas = separarAgendadas(lista, agora)
                emit(separadas)
                val proxima = separadas.agendadas.firstOrNull()?.visivelEm ?: break
                delay(java.time.Duration.between(relogio.instant(), proxima).toMillis().coerceAtLeast(0))
                // Passada a espera, a próxima já conta como visível (mesmo com um relógio parado, nos testes).
                agora = maxOf(relogio.instant(), proxima)
            }
        }
    }

    private val base = combine(
        conversas.observar(conversaId),
        mensagensDoChat,
        presente,
        carga,
        primeiraNaoLida,
    ) { conversa, separadas, p, c, naoLida ->
        val grupo = conversa?.tipo == TipoConversa.GRUPO
        val lista = separadas.visiveis
        ChatUiState(
            conversa = conversa,
            online = conversa?.tipo == TipoConversa.DIRETA && conversa.destinatarioId in p.online,
            membros = if (grupo) membros.value.map { it.nome }.sorted().joinToString(", ") else "",
            atividade = atividadeDaConversa(grupo, p.digitando, p.gravando, p.nomes, eu),
            itens = montarItensChat(lista, naoLida?.takeIf { it > 0 }, grupo, eu, relogio.zone),
            agendadas = separadas.agendadas,
            eu = eu,
            carregando = lista.isEmpty() && separadas.agendadas.isEmpty() && !c.carregou,
            carregandoAnteriores = c.anteriores,
            chegouAoInicio = c.inicio,
            pronto = naoLida != null,
        )
    }

    val estado: StateFlow<ChatUiState> = combine(base, fila, envio.progresso, extrasDoCampo) { b, f, p, campo ->
        b.copy(
            fila = f,
            progresso = p,
            textoParaCampo = campo.texto,
            focarCampo = campo.foco,
            respondendo = campo.respondendo,
            irPara = campo.salto.irPara,
            destaque = campo.salto.destaque,
            buscandoMensagem = campo.salto.buscando,
            minhasConversas = campo.minhas,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(eu = eu))

    init {
        // Veio do "Enviar para…" (AND-10): os arquivos entram na fila e o texto vai para o campo.
        if (salvo.get<Boolean>("comCompartilhamento") == true) {
            compartilhamentos.retirar()?.let { itens ->
                fila.value = itens.anexos
                textoParaCampo.value = itens.texto.ifBlank { null }
            }
        }
        viewModelScope.launch { restaurarEGuardarRascunho() }
        viewModelScope.launch {
            val resultado = mensagens.carregarRecentes(conversaId)
            carga.value = carga.value.copy(carregou = true)
            resultado.onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
            // "Responder no privado" (7.6): a mensagem do grupo fica pendente como encaminhada.
            salvo.get<Long>("encaminharDe")?.takeIf { it > 0 }?.let { id ->
                mensagens.buscar(id)?.let {
                    respondendo.value = ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, it)
                    focarCampo.value = true
                }
            }
            // Veio de um link ou de uma encaminhada com a mensagem (`?mensagem=`): vai até ela.
            salvo.get<Long>("mensagemId")?.takeIf { it > 0 }?.let { irParaMensagem(it, conversaId) }
            // "Últimas": a primeira de outra pessoa ainda não lida, decidida uma vez só.
            val lista = mensagens.observar(conversaId).first()
            primeiraNaoLida.value = lista.firstOrNull { it.remetenteId != eu && !it.visualizada && it.id > 0 && !it.oculta }?.id ?: 0
        }
        viewModelScope.launch {
            // Conversa que ainda não está no aparelho (o grupo do chat da chamada recém-criado):
            // relê a lista, como o web, para o cabeçalho ter nome e membros.
            if (conversas.observar(conversaId).first() == null) conversas.atualizar()
            val conversa = conversas.observar(conversaId).first()
            if (conversa?.tipo == TipoConversa.GRUPO) conversas.membros(conversaId).onSuccess { membros.value = it }
        }
    }

    /** A lista chegou perto do topo. */
    fun carregarAnteriores() {
        val atual = carga.value
        if (atual.anteriores || atual.inicio || !atual.carregou) return
        viewModelScope.launch {
            carga.value = carga.value.copy(anteriores = true)
            mensagens.carregarAnteriores(conversaId)
                .onSuccess { quantas -> carga.value = carga.value.copy(anteriores = false, inicio = quantas == 0) }
                .onFailure {
                    carga.value = carga.value.copy(anteriores = false)
                    eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel()))
                }
        }
    }

    /** Mensagens de outras pessoas que apareceram na tela com o app em primeiro plano (MSG-04). */
    fun marcarLidas(visiveis: List<Mensagem>) {
        val paraLer = visiveis.filter { it.remetenteId != eu && !it.visualizada && it.id > 0 }
        if (paraLer.isEmpty()) return
        viewModelScope.launch { paraLer.forEach { mensagens.marcarLida(conversaId, it.id) } }
    }

    /** Envia e avisa a tela para limpar o campo só depois de gravado no Room. */
    fun enviar(texto: String, aoGravar: () -> Unit) = enviar(texto, null, aoGravar)

    /** "Agendar" (7.10): a mesma mensagem, com `visivel_em` (o diálogo já validou 5 min..1 ano). */
    fun agendar(texto: String, quando: Instant, aoGravar: () -> Unit) = enviar(texto, quando, aoGravar)

    /** "Cancelar" na lista de agendadas: apaga de vez (ninguém a viu). Falso = "Não foi possível cancelar". */
    suspend fun cancelarAgendada(mensagem: Mensagem): Boolean = mensagens.ocultar(mensagem.id).isSuccess

    /** Detalhe do status (7.10): a folha pede quando abre. */
    suspend fun statusDetalhe(mensagemId: Long): Result<List<StatusDestinatario>> = mensagens.statusDetalhe(mensagemId)

    private fun enviar(texto: String, visivelEm: Instant?, aoGravar: () -> Unit) {
        val limpo = texto.trim()
        val anexosNaFila = fila.value
        val resposta = respondendo.value
        // A encaminhada do "Responder no privado" pode ir sem texto: leva os conteúdos dela.
        val encaminhando = resposta?.tipo == TipoReferencia.ENCAMINHAMENTO
        if (limpo.isEmpty() && anexosNaFila.isEmpty() && !encaminhando) return
        viewModelScope.launch {
            envio.enviar(conversaId, limpo, anexosNaFila, resposta, visivelEm = visivelEm)
            fila.value = fila.value - anexosNaFila.toSet()
            if (respondendo.value == resposta) respondendo.value = null
            aoGravar()
            reiniciarDigitando()
            eventos.enviar(EventoChat.RolarAoFim)
        }
    }

    /** Figurinha (7.8): vai na hora, sozinha (como o web com o campo vazio) e como resposta, se houver uma pendente. */
    fun enviarFigurinha(id: String) {
        viewModelScope.launch {
            val resposta = respondendo.value
            envio.enviar(conversaId, "", emptyList(), resposta, figurinha = id)
            if (respondendo.value == resposta) respondendo.value = null
            eventos.enviar(EventoChat.RolarAoFim)
        }
    }

    /** A conversa está visível (RESUMED): sem notificação nem som para ela, e a dela some (NOT-01/03). */
    fun visivel(sim: Boolean) {
        if (sim) emTela.apareceu(conversaId) else emTela.sumiu(conversaId)
    }

    /** Menção tocada: obtém ou cria a direta e abre. */
    fun abrirDireta(usuarioId: Long) {
        if (usuarioId == eu) return
        viewModelScope.launch {
            conversas.obterOuCriarDireta(usuarioId)
                .onSuccess { if (it != conversaId) eventos.enviar(EventoChat.AbrirConversa(it)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    // --- Anexos (ANX-01…14) ---

    /** Arquivos escolhidos (galeria, documento, câmera). [tipo] força o tipo (ex.: foto da câmera). */
    fun adicionarAnexos(uris: List<String>, tipo: TipoConteudo? = null) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val novos = withContext(Dispatchers.IO) { uris.mapNotNull { fontes.descrever(it, tipo) } }
            val grandes = novos.filter { it.tamanho > AnexosRepositorio.LIMITE_BYTES }
            if (grandes.isNotEmpty()) eventos.enviar(EventoChat.ArquivoGrande(grandes.first().nome))
            fila.value = (fila.value + (novos - grandes.toSet())).distinctBy { it.uri }
        }
    }

    /**
     * Imagem colada no campo (FC-412): a permissão de leitura do teclado ou da área de
     * transferência é temporária, então copia para o cache (mesmas regras do compartilhar).
     */
    fun colarAnexos(uris: List<String>) {
        viewModelScope.launch {
            val copiados = compartilhamentos.copiarColados(uris)
            fila.value = (fila.value + copiados).distinctBy { it.uri }
        }
    }

    /** Pasta do cache onde a câmera grava a foto (compartilhada pelo FileProvider). */
    fun pastaCamera(): java.io.File = arquivos.pastaCamera

    /** O campo já recebeu o texto compartilhado. */
    fun textoUsado() {
        textoParaCampo.value = null
    }

    fun removerAnexo(uri: String) {
        fila.value = fila.value.filterNot { it.uri == uri }
        viewModelScope.launch { envio.desistirDoArquivo(uri) }
    }

    /** Vídeo no visualizador: o arquivo local (ainda enviando) ou a URL assinada (o player lê por partes). */
    suspend fun urlDoVideo(conteudo: com.conversa.app.core.model.Conteudo): String? =
        if (conteudo.local) conteudo.conteudo.removePrefix(PREFIXO_LOCAL) else anexos.url(conteudo.conteudo).getOrNull()

    /**
     * "Baixar" (ANX-09): Android 10+ salva direto em Downloads/Conversa (continua mesmo
     * saindo da conversa); Android 9 pergunta onde salvar.
     */
    fun baixar(conteudo: com.conversa.app.core.model.Conteudo) {
        if (conteudo.local) return
        val nome = conteudo.nome.ifBlank { conteudo.conteudo }
        viewModelScope.launch {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                downloads.salvarEmDownloads(conteudo.conteudo, nome, null)
                eventos.enviar(EventoChat.Baixando(nome))
            } else {
                eventos.enviar(EventoChat.EscolherOndeSalvar(conteudo))
            }
        }
    }

    /** Android 9: o URI escolhido no "Salvar como". */
    fun salvarEm(uri: String, conteudo: com.conversa.app.core.model.Conteudo) {
        val nome = conteudo.nome.ifBlank { conteudo.conteudo }
        downloads.salvarEm(uri, conteudo.conteudo, nome, null)
        viewModelScope.launch { eventos.enviar(EventoChat.Baixando(nome)) }
    }

    /** Baixa para o cache (uma vez) e pede para a tela abrir o "Compartilhar". */
    fun compartilhar(conteudo: com.conversa.app.core.model.Conteudo) {
        if (conteudo.local) return
        viewModelScope.launch {
            arquivos.baixar(conteudo.conteudo, conteudo.nome.ifBlank { conteudo.conteudo })
                .onSuccess { eventos.enviar(EventoChat.Compartilhar(it, null)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    fun campoFocado() {
        focarCampo.value = false
    }

    /** "Responder" no menu ou deslizando a bolha (7.3): a barra aparece e o campo pega o foco. */
    fun responder(mensagem: Mensagem) {
        if (!podeAbrirMenu(mensagem)) return
        respondendo.value = ReferenciaPendente(TipoReferencia.RESPOSTA, mensagem)
        focarCampo.value = true
    }

    /** Contatos para a lista de menção do campo (7.7), como o web (todos os contatos). */
    val contatosMencao: StateFlow<List<com.conversa.app.core.model.Contato>> =
        contatos.observarOutros().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Destinos do "Encaminhar" (7.6): as conversas, menos esta, e os contatos sem direta. */
    val destinosEncaminhar: StateFlow<List<DestinoEncaminhar>> = combine(conversas.observarTodas(), contatos.observarOutros()) {
            lista,
            pessoas,
        ->
        destinosParaEncaminhar(lista, pessoas, conversaId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Encaminhar (7.6): para contato sem conversa, cria a direta antes; a mensagem vai pela fila
     * (`mensagem_referencia` tipo 2, com os conteúdos da original) e o destino abre.
     */
    fun encaminhar(mensagem: Mensagem, destino: DestinoEncaminhar) {
        if (mensagem.id <= 0) return
        viewModelScope.launch {
            val conversa = when (destino) {
                is DestinoEncaminhar.ParaConversa -> destino.conversaId
                is DestinoEncaminhar.ParaContato -> conversas.obterOuCriarDireta(destino.contatoId).getOrElse {
                    eventos.enviar(EventoChat.EncaminharFalhou)
                    return@launch
                }
            }
            envio.enviar(conversa, "", emptyList(), ReferenciaPendente(TipoReferencia.ENCAMINHAMENTO, mensagem))
            eventos.enviar(EventoChat.AbrirConversa(conversa))
        }
    }

    /**
     * "Responder no privado" (7.6, grupo): abre (ou cria) a direta com quem escreveu, com esta
     * mensagem pendente como encaminhada, para acrescentar o comentário (como o web).
     */
    fun responderNoPrivado(mensagem: Mensagem) {
        if (mensagem.id <= 0 || mensagem.remetenteId == eu || mensagem.remetenteId <= 0) return
        viewModelScope.launch {
            conversas.obterOuCriarDireta(mensagem.remetenteId)
                .onSuccess { eventos.enviar(EventoChat.AbrirConversa(it, encaminharDe = mensagem.id)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    fun cancelarResposta() {
        respondendo.value = null
    }

    /**
     * Toque na citação (7.5, MSG-06): nesta conversa, traz a original (sem deixar buraco), rola
     * e destaca; de outra conversa (encaminhada), abre aquela, se participo dela.
     */
    fun irParaMensagem(mensagemId: Long, conversaDaMensagem: Long) {
        if (mensagemId <= 0) return
        viewModelScope.launch {
            if (conversaDaMensagem > 0 && conversaDaMensagem != conversaId) {
                val participo = conversas.observar(conversaDaMensagem).first() != null
                eventos.enviar(
                    if (participo) EventoChat.AbrirConversa(conversaDaMensagem, mensagemId) else EventoChat.MensagemNaoLocalizada,
                )
                return@launch
            }
            salto.value = salto.value.copy(buscando = true)
            val resultado = mensagens.trazerAte(conversaId, mensagemId)
            salto.value = salto.value.copy(buscando = false)
            resultado
                .onSuccess { achou ->
                    if (achou) {
                        salto.value = Salto(
                            irPara = mensagemId,
                            destaque = mensagemId,
                        )
                    } else {
                        eventos.enviar(EventoChat.MensagemNaoLocalizada)
                    }
                }
                .onFailure { eventos.enviar(EventoChat.MensagemNaoLocalizada) }
        }
    }

    /** A tela chegou na mensagem do salto: o destaque fica [DESTAQUE_MS] e some. */
    fun chegouNaMensagem() {
        val destacada = salto.value.destaque
        salto.value = salto.value.copy(irPara = null)
        viewModelScope.launch {
            delay(DESTAQUE_MS)
            if (salto.value.destaque == destacada) salto.value = salto.value.copy(destaque = null)
        }
    }

    /** Reação do menu, do chip ou do seletor (7.2): alterna a minha; aparece na hora. */
    fun reagir(mensagem: Mensagem, emoji: String) {
        if (mensagem.id <= 0) return
        // Como o web: o limite é conferido antes da reação otimista (o servidor confere de novo).
        if (!podeReagir(mensagem.reacoes, emoji)) return eventos.enviar(EventoChat.LimiteDeReacoes)
        viewModelScope.launch {
            mensagens.reagir(conversaId, mensagem.id, emoji, eu, meuNome)
                .onFailure { eventos.enviar(EventoChat.ReagirFalhou(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    /** Ocultar (7.4): a lista da conversa também muda (a prévia pode ser esta mensagem). */
    fun ocultar(mensagem: Mensagem) {
        if (mensagem.id <= 0) return
        viewModelScope.launch {
            mensagens.ocultar(mensagem.id)
                .onSuccess { conversas.atualizar() }
                .onFailure { eventos.enviar(EventoChat.OcultarFalhou) }
        }
    }

    /**
     * "Copiar" (7.6), como o web: imagem vai como imagem; senão, os textos da mensagem
     * (um por linha).
     */
    fun copiar(mensagem: Mensagem) {
        val imagem = mensagem.conteudos.firstOrNull { it.tipo == TipoConteudo.IMAGEM && !it.local }
        if (imagem != null) {
            viewModelScope.launch {
                arquivos.baixar(imagem.conteudo, imagem.nome.ifBlank { imagem.conteudo })
                    .onSuccess { eventos.enviar(EventoChat.CopiarImagem(it)) }
                    .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
            }
            return
        }
        val texto = mensagem.conteudos.filter { it.tipo == TipoConteudo.TEXTO }.joinToString("\n") { it.conteudo }
        if (texto.isNotBlank()) eventos.enviar(EventoChat.CopiarTexto(texto))
    }

    /** "Abrir" num PDF (FC-411): baixa para o cache e abre no visualizador do app, como o web. */
    fun abrirPdf(conteudo: com.conversa.app.core.model.Conteudo) {
        if (conteudo.local) return
        viewModelScope.launch {
            arquivos.baixar(conteudo.conteudo, conteudo.nome.ifBlank { conteudo.conteudo })
                .onSuccess { eventos.enviar(EventoChat.AbrirPdf(it, conteudo)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    /** Baixa para o cache (uma vez) e pede para a tela abrir com outro app. */
    fun abrirArquivo(identificador: String, nome: String, mime: String?) {
        viewModelScope.launch {
            arquivos.baixar(identificador, nome)
                .onSuccess { eventos.enviar(EventoChat.AbrirArquivo(it, mime)) }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    // --- Áudio (ANX-10): um por vez, baixado para o cache antes de tocar ---

    private val baixandoAudio = MutableStateFlow<String?>(null)
    private val duracoes = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val prefixoAudio = "$conversaId:"

    private val transcricao = combine(transcricoes.desligada, transcricoes.erros, transcricoes.pedindo) { desligada, erros, pedindo ->
        Triple(desligada, erros, pedindo)
    }

    val audio: StateFlow<AudioNaConversa> = combine(player.estado, baixandoAudio, duracoes, transcricao) { p, baixando, d, t ->
        val daqui = p.chave?.startsWith(prefixoAudio) == true
        AudioNaConversa(
            chave = p.chave.takeIf { daqui },
            tocando = daqui && p.tocando,
            posicaoMs = if (daqui) p.posicaoMs else 0,
            baixando = baixando,
            duracoes = d,
            transcricaoDesligada = t.first,
            errosTranscricao = t.second,
            pedindoTranscricao = t.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioNaConversa())

    init {
        // Fim dos downloads pedidos (a notificação, se permitida, avisa mesmo fora da conversa).
        viewModelScope.launch {
            downloads.resultados.collect {
                eventos.enviar(
                    when (it) {
                        is ResultadoDownload.Salvo -> EventoChat.Salvo(it.nome, it.uri, it.mime)
                        is ResultadoDownload.Falhou -> EventoChat.DownloadFalhou(it.nome)
                    },
                )
            }
        }
        // Guarda a duração de cada áudio que tocou (a bolha continua mostrando depois de trocar de áudio).
        viewModelScope.launch {
            player.estado.collect { p ->
                val chave = p.chave ?: return@collect
                val duracao = p.duracaoMs ?: return@collect
                if (chave.startsWith(prefixoAudio) && duracoes.value[chave] != duracao) duracoes.value += chave to duracao
            }
        }
        viewModelScope.launch {
            player.falhas.collect { chave -> if (chave.startsWith(prefixoAudio)) eventos.enviar(EventoChat.AudioFalhou) }
        }
    }

    // --- Gravação (ANX-11): ver ControleGravacao ---

    private val controleGravacao = ControleGravacao(
        escopo = viewModelScope,
        conversaId = conversaId,
        gravador = gravador,
        arquivos = arquivos,
        envio = envio,
        mensagens = mensagens,
        player = player,
        relogio = relogio,
        es = Dispatchers.IO,
        avisar = { eventos.enviar(it) },
        pegarResposta = { respondendo.value.also { respondendo.value = null } },
    )

    /** Separado do [estado]: o tempo e o nível mudam 10 vezes por segundo e só o campo lê. */
    val gravacao: StateFlow<EstadoGravacao> = controleGravacao.estado

    fun iniciarGravacao() = controleGravacao.iniciar()

    fun travarGravacao() = controleGravacao.travar()

    fun pausarGravacao() = controleGravacao.pausar()

    fun continuarGravacao() = controleGravacao.continuar()

    fun ouvirGravacao() = controleGravacao.ouvir()

    fun enviarGravacao() = controleGravacao.enviar()

    fun descartarGravacao() = controleGravacao.descartar()

    /** Saiu da conversa: o áudio dela para (ANX-10) e a gravação aberta é descartada. */
    override fun onCleared() {
        controleGravacao.descartar()
        if (player.estado.value.chave?.startsWith(prefixoAudio) == true) player.parar()
        // O último estado do campo, sem esperar o atraso (grava no escopo do app).
        ultimoRascunho?.let { rascunhos.guardar(conversaId, it) }
    }

    // --- Rascunho por conversa (FC-519, 🆕 web `7322e83`) ---

    /** O campo mudou: o texto com as menções cruas (`@[Nome](id)`) entra no rascunho. */
    fun aoMudarRascunho(texto: String) {
        textoRascunho.value = texto
    }

    /**
     * Ao abrir: o rascunho salvo volta (texto, anexos e a resposta pendente; um texto compartilhado
     * por outro app vem depois do rascunho). Depois, cada mudança é gravada com um pequeno atraso.
     */
    @OptIn(FlowPreview::class)
    private suspend fun restaurarEGuardarRascunho() {
        rascunhos.ler(conversaId)?.let { salvo ->
            if (salvo.anexos.isNotEmpty()) fila.value = (salvo.anexos + fila.value).distinctBy { it.uri }
            if (salvo.texto.isNotBlank()) {
                textoRascunho.value = salvo.texto
                textoParaCampo.value = listOfNotNull(salvo.texto, textoParaCampo.value).joinToString("\n")
            }
            val (tipo, id) = salvo.referencia ?: (null to null)
            if (respondendo.value == null && tipo != null && id != null) {
                mensagens.buscar(id)?.let { respondendo.value = ReferenciaPendente(tipo, it) }
            }
        }
        combine(textoRascunho, fila, respondendo) { texto, anexos, resposta ->
            Rascunho(texto, anexos, resposta?.let { it.tipo to it.mensagem.id })
        }
            .onEach { ultimoRascunho = it }
            .debounce(RASCUNHO_MS)
            .collect { rascunhos.guardar(conversaId, it) }
    }

    /** Play/pause na bolha. Na primeira vez baixa o arquivo; áudio de outra pessoa vira "ouvido". */
    fun alternarAudio(mensagem: Mensagem, conteudo: com.conversa.app.core.model.Conteudo) {
        val chave = chaveDoAudio(mensagem, conteudo)
        val atual = player.estado.value
        if (atual.chave == chave) {
            if (atual.tocando) player.pausar() else player.continuar()
            return
        }
        if (baixandoAudio.value == chave) return
        if (conteudo.local) {
            // Ainda enviando: toca o próprio arquivo escolhido.
            player.tocar(chave, conteudo.conteudo.removePrefix(PREFIXO_LOCAL))
            return
        }
        viewModelScope.launch {
            baixandoAudio.value = chave
            val baixado = arquivos.baixar(conteudo.conteudo, conteudo.nome.ifBlank { "audio" })
            // Outro áudio pode ter sido pedido enquanto este baixava: só toca o último.
            if (baixandoAudio.value != chave) return@launch
            baixandoAudio.value = null
            baixado
                .onSuccess { arquivo ->
                    // URI `file:` com o caminho codificado (nome com espaço, `#`…).
                    player.tocar(chave, arquivo.toURI().toString())
                    if (mensagem.remetenteId != eu && !mensagem.reproduzida) mensagens.marcarReproduzida(conversaId, mensagem.id)
                }
                .onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    /** "Transcrever" / "Tentar de novo" (ANX-12). Sem transcritor no servidor, avisa o motivo e os botões somem. */
    fun transcrever(identificador: String) {
        viewModelScope.launch {
            transcricoes.transcrever(identificador).onFailure { eventos.enviar(EventoChat.Erro(it.paraErroApi().mensagemAmigavel())) }
        }
    }

    /** Transcrição "processando" que veio do servidor: acompanha até terminar. */
    fun acompanharTranscricao(identificador: String) = transcricoes.acompanhar(identificador)

    /** Arrastou a barra do áudio que está no player. */
    fun buscarAudio(chave: String, fracao: Float) {
        val atual = player.estado.value
        val duracao = atual.duracaoMs ?: return
        if (atual.chave == chave) player.buscar((duracao * fracao.coerceIn(0f, 1f)).toLong())
    }

    fun reenviar(idLocal: Long) {
        viewModelScope.launch { envio.reenviar(idLocal) }
    }

    fun descartar(idLocal: Long) {
        viewModelScope.launch { envio.descartar(idLocal) }
    }

    // --- Digitando (ENV-15): no máximo 1 aviso a cada 2,5 s; o último é adiado, não descartado ---

    private var janela: Job? = null
    private var pendente = false

    fun aoDigitar(texto: String) {
        if (texto.isBlank()) return
        if (janela?.isActive == true) {
            pendente = true
            return
        }
        avisarEAbrirJanela()
    }

    private fun avisarEAbrirJanela() {
        pendente = false
        viewModelScope.launch { mensagens.avisarDigitando(conversaId) }
        janela = viewModelScope.launch {
            delay(INTERVALO_DIGITANDO_MS)
            if (pendente) avisarEAbrirJanela()
        }
    }

    private fun reiniciarDigitando() {
        janela?.cancel()
        janela = null
        pendente = false
    }

    private data class Carga(val carregou: Boolean = false, val anteriores: Boolean = false, val inicio: Boolean = false)

    private data class Presente(val online: Set<Long>, val digitando: Set<Long>, val gravando: Set<Long>, val nomes: Map<Long, String>)

    companion object {
        /** Igual ao web (`DIGITANDO_DEBOUNCE_MS`). */
        const val INTERVALO_DIGITANDO_MS = 2_500L
    }
}
