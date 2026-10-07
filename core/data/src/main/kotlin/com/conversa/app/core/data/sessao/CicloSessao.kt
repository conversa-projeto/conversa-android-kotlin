package com.conversa.app.core.data.sessao

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.autenticacao.AutenticacaoRepositorio
import com.conversa.app.core.data.contatos.ContatosRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.mensagens.AgendadorEnvio
import com.conversa.app.core.data.mensagens.EnvioMensagens
import com.conversa.app.core.data.mensagens.MensagensRepositorio
import com.conversa.app.core.data.presenca.PresencaRepositorio
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.database.ConversaBanco
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.paraErroApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Início de sessão resiliente (AUT-03, como o `iniciarSessao` do web):
 * ao entrar (login ou app aberto já logado), registra o dispositivo e carrega
 * conversas e contatos.
 * - 401 → a sessão acaba sozinha (interceptador), nada a fazer aqui;
 * - qualquer outro erro (API subindo, 502 do nginx, sem rede) → **mantém** a sessão,
 *   expõe o erro em [falha] e tenta de novo a cada [INTERVALO_MS] até dar certo.
 */
@Singleton
class IniciadorSessao @Inject constructor(
    private val sessao: SessaoRepositorio,
    private val conversas: ConversasRepositorio,
    private val contatos: ContatosRepositorio,
    private val autenticacao: AutenticacaoRepositorio,
    private val envio: EnvioMensagens,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val _falha = MutableStateFlow<ErroApi?>(null)

    /** Erro da última tentativa de carregar a sessão; `null` quando deu certo ou não há sessão. */
    val falha: StateFlow<ErroApi?> = _falha.asStateFlow()

    private val _carregada = MutableStateFlow(false)

    /** A primeira carga desta sessão terminou (a lista pode dizer "nenhuma conversa" sem mentir). */
    val carregada: StateFlow<Boolean> = _carregada.asStateFlow()

    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch {
            sessao.sessao.map { it?.usuarioId }.distinctUntilChanged().collectLatest { usuario ->
                _falha.value = null
                _carregada.value = false
                if (usuario == null) return@collectLatest
                launch {
                    autenticacao.registrarDispositivo()
                        .onFailure { Timber.d("Registro do dispositivo falhou: %s", it.javaClass.simpleName) }
                }
                while (true) {
                    val erro = carregar() ?: break
                    if (erro is ErroApi.SessaoExpirada) break
                    _falha.value = erro
                    delay(INTERVALO_MS)
                }
                _falha.value = null
                _carregada.value = true
                // Mensagens que ficaram na fila (app fechado no meio do envio).
                envio.retomar()
            }
        }
    }

    /** Conversas e contatos em paralelo; devolve o primeiro erro, se houver. */
    private suspend fun carregar(): ErroApi? = coroutineScope {
        val listaConversas = async { conversas.atualizar() }
        val listaContatos = async { contatos.atualizar() }
        listOf(listaConversas.await(), listaContatos.await()).firstNotNullOfOrNull { it.exceptionOrNull()?.paraErroApi() }
    }

    companion object {
        /** Igual ao web: tenta de novo a cada 5 s. */
        const val INTERVALO_MS = 5_000L
    }
}

/**
 * Fim da sessão (logout ou 401): limpa o cache local, a presença e os contadores.
 * O socket fecha sozinho ([com.conversa.app.core.data.tempoReal.ConexaoTempoReal]);
 * imagens em cache e notificações são limpas pelo app.
 */
@Singleton
class LimpezaSessao @Inject constructor(
    private val sessao: SessaoRepositorio,
    private val banco: ConversaBanco,
    private val presenca: PresencaRepositorio,
    private val sincronizacao: SyncManager,
    private val mensagens: MensagensRepositorio,
    private val agendadorEnvio: AgendadorEnvio,
    private val anexos: AnexosRepositorio,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch { sessao.fim.collect { limpar() } }
    }

    suspend fun limpar() {
        agendadorEnvio.cancelar()
        presenca.limpar()
        sincronizacao.limpar()
        mensagens.limpar()
        anexos.limpar()
        withContext(Dispatchers.IO) { banco.clearAllTables() }
    }
}
