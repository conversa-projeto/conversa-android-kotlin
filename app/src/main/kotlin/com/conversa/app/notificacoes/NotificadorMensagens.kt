package com.conversa.app.notificacoes

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.conversa.app.MainActivity
import com.conversa.app.R
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.conversas.ConversasRepositorio
import com.conversa.app.core.data.notificacoes.CanaisNotificacao
import com.conversa.app.core.data.notificacoes.ConversaEmTela
import com.conversa.app.core.data.paraModelo
import com.conversa.app.core.data.sincronizacao.SyncManager
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.database.dao.MensagemDao
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.ResumoCitacao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.resumoDaMensagem
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** O que fazer com uma mensagem nova de outra pessoa (NOT-01/NOT-02, como o web). */
enum class AvisoMensagem { NOTIFICAR, SOM, NADA }

/**
 * Regra do web: conversa arquivada → nada; app na frente com a conversa na tela → nada;
 * app na frente em outra tela → só o som; app em segundo plano → notificação.
 */
fun decidirAviso(arquivada: Boolean, emPrimeiroPlano: Boolean, conversaNaTela: Boolean): AvisoMensagem = when {
    arquivada -> AvisoMensagem.NADA
    !emPrimeiroPlano -> AvisoMensagem.NOTIFICAR
    conversaNaTela -> AvisoMensagem.NADA
    else -> AvisoMensagem.SOM
}

/**
 * Notificação de mensagem (TODO 5.4–5.6 e 5.8, FC-602…605, FC-608, NOT-01…03):
 * - uma por conversa (`MessagingStyle`, id = id da conversa), montada das não lidas do Room;
 * - "Responder" (RemoteInput) e "Marcar como lida" ([AcoesNotificacaoReceiver]);
 * - toque abre a conversa (`conversa://chat/{id}`);
 * - some quando a conversa fica sem não lidas, é arquivada ou é aberta;
 * - cada conversa notificada vira um atalho de longa duração (seção "Conversas" do Android).
 * Hoje é disparada pelo [SyncManager] (WebSocket ou atualização periódica); o push (5.1)
 * vai chamar [avisar] depois de sincronizar.
 */
@Singleton
class NotificadorMensagens @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val sync: SyncManager,
    private val conversas: ConversasRepositorio,
    private val mensagemDao: MensagemDao,
    private val sessao: SessaoRepositorio,
    private val primeiroPlano: MonitorPrimeiroPlano,
    private val emTela: ConversaEmTela,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private val gerenciador = NotificationManagerCompat.from(contexto)
    private var ultimoSomMs = 0L

    fun iniciar() {
        sync.novasDeOutros.onEach { ids -> ids.forEach { avisar(it) } }.launchIn(escopo)
        // NOT-03: lida (contador zerado) ou arquivada → some.
        conversas.observarTodas().onEach { lista ->
            val postadas = postadas()
            lista.filter { it.id in postadas && (it.naoLidas == 0 || it.arquivada) }.forEach { cancelar(it.id) }
        }.launchIn(escopo)
        // Abriu a conversa → some.
        emTela.id.filterNotNull().onEach { cancelar(it) }.launchIn(escopo)
        // FC-608: as conversas recentes viram atalhos (aparecem em "Conversas" e no compartilhar).
        conversas.observarTodas()
            .map { lista -> lista.filterNot { it.arquivada }.sortedByDescending { it.ultimaMensagemEm }.take(ATALHOS_RECENTES) }
            .distinctUntilChanged { antes, depois -> antes.map { it.id to it.titulo } == depois.map { it.id to it.titulo } }
            .onEach { recentes -> recentes.forEach(::publicarAtalho) }
            .launchIn(escopo)
    }

    suspend fun avisar(conversaId: Long) {
        val conversa = conversas.observar(conversaId).first() ?: return
        when (decidirAviso(conversa.arquivada, primeiroPlano.emPrimeiroPlano.value, emTela.id.value == conversaId)) {
            AvisoMensagem.NADA -> Unit
            AvisoMensagem.SOM -> tocarSom()
            AvisoMensagem.NOTIFICAR -> {
                val eu = sessao.sessao.value?.usuarioId ?: return
                val mensagens = mensagemDao.naoLidasDeOutros(conversaId, eu, MAX_NA_NOTIFICACAO).map { it.paraModelo() }.asReversed()
                if (mensagens.isNotEmpty()) mostrar(conversa, mensagens)
            }
        }
    }

    fun cancelar(conversaId: Long) = gerenciador.cancel(TAG, conversaId.toInt())

    /**
     * Depois de "Responder": no Android 15+ o sistema segura a notificação (e ignora o
     * cancelamento) até o app publicar uma atualização. Mostra a resposta ("Você: …"),
     * sem som, e ela sai sozinha logo depois.
     */
    suspend fun respondida(conversaId: Long, resposta: String) {
        if (!podeNotificar()) return
        val conversa = conversas.observar(conversaId).first() ?: return cancelar(conversaId)
        val eu = Person.Builder().setName(contexto.getString(R.string.notificacao_voce)).setKey("eu").build()
        val estilo = NotificationCompat.MessagingStyle(eu)
            .setGroupConversation(conversa.tipo == TipoConversa.GRUPO)
            .addMessage(resposta, System.currentTimeMillis(), null as Person?)
        if (conversa.tipo == TipoConversa.GRUPO) estilo.conversationTitle = conversa.titulo
        val notificacao = NotificationCompat.Builder(contexto, CanaisNotificacao.MENSAGENS)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setStyle(estilo)
            .setShortcutId("conversa-$conversaId")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(abrirConversa(conversaId))
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(TEMPO_RESPOSTA_VISIVEL_MS)
            .build()
        @Suppress("MissingPermission") // conferida em podeNotificar
        gerenciador.notify(TAG, conversaId.toInt(), notificacao)
    }

    /** Fim da sessão: notificações e atalhos de conversa somem (podem ser de outra conta). */
    fun limpar() {
        gerenciador.cancelAll()
        ShortcutManagerCompat.removeAllDynamicShortcuts(contexto)
        runCatching {
            ShortcutManagerCompat.removeLongLivedShortcuts(
                contexto,
                ShortcutManagerCompat.getShortcuts(contexto, ShortcutManagerCompat.FLAG_MATCH_CACHED).map {
                    it.id
                },
            )
        }
    }

    private fun postadas(): Set<Long> = runCatching {
        gerenciador.activeNotifications.filter { it.tag == TAG }.map { it.id.toLong() }.toSet()
    }.getOrDefault(emptySet())

    private fun mostrar(conversa: Conversa, mensagens: List<Mensagem>) {
        if (!podeNotificar()) return
        val grupo = conversa.tipo == TipoConversa.GRUPO
        val eu = Person.Builder().setName(contexto.getString(R.string.notificacao_voce)).setKey("eu").build()
        val estilo = NotificationCompat.MessagingStyle(eu).setGroupConversation(grupo)
        if (grupo) estilo.conversationTitle = conversa.titulo
        mensagens.forEach { mensagem ->
            val pessoa = Person.Builder().setName(mensagem.remetente).setKey("u${mensagem.remetenteId}").build()
            estilo.addMessage(texto(mensagem), mensagem.dataEfetiva.toEpochMilli(), pessoa)
        }
        val atalho = publicarAtalho(conversa)
        val notificacao = NotificationCompat.Builder(contexto, CanaisNotificacao.MENSAGENS)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setStyle(estilo)
            .setShortcutId(atalho)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(abrirConversa(conversa.id))
            .setAutoCancel(true)
            .setNumber(conversa.naoLidas)
            .addAction(acaoResponder(conversa.id))
            .addAction(acaoMarcarLida(conversa.id))
            .build()
        @Suppress("MissingPermission") // conferida em podeNotificar
        gerenciador.notify(TAG, conversa.id.toInt(), notificacao)
    }

    /** Corpo como o web: texto resumido ou o tipo ("Imagem", "Gravação de áudio"…). */
    private fun texto(mensagem: Mensagem): String = when (val resumo = resumoDaMensagem(mensagem)) {
        ResumoCitacao.Oculta -> contexto.getString(R.string.notificacao_oculta)
        is ResumoCitacao.Texto -> resumo.texto
        is ResumoCitacao.Tipo -> contexto.getString(
            when (resumo.tipo) {
                TipoConteudo.IMAGEM -> R.string.notificacao_imagem
                TipoConteudo.GRAVACAO_AUDIO -> R.string.notificacao_gravacao
                TipoConteudo.AUDIO -> R.string.notificacao_audio
                TipoConteudo.FIGURINHA -> R.string.notificacao_figurinha
                TipoConteudo.ENQUETE -> R.string.notificacao_votacao
                else -> R.string.notificacao_arquivo
            },
        )
    }

    /** Atalho de longa duração da conversa (FC-608): exigido para a notificação ser "conversa" no Android 11+. */
    private fun publicarAtalho(conversa: Conversa): String {
        val id = "conversa-${conversa.id}"
        val atalho = ShortcutInfoCompat.Builder(contexto, id)
            .setShortLabel(conversa.titulo)
            .setLongLived(true)
            .setIcon(IconCompat.createWithResource(contexto, R.mipmap.ic_launcher))
            .setIntent(intencaoDaConversa(conversa.id))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(contexto, atalho) }
        return id
    }

    private fun intencaoDaConversa(conversaId: Long) = Intent(Intent.ACTION_VIEW, "conversa://chat/$conversaId".toUri())
        .setClass(contexto, MainActivity::class.java)

    private fun abrirConversa(conversaId: Long): PendingIntent = PendingIntent.getActivity(
        contexto,
        conversaId.toInt(),
        intencaoDaConversa(conversaId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun acaoResponder(conversaId: Long): NotificationCompat.Action {
        val entrada = RemoteInput.Builder(AcoesNotificacaoReceiver.CHAVE_RESPOSTA)
            .setLabel(contexto.getString(R.string.notificacao_responder_dica))
            .build()
        // Mutável: o Android preenche o texto digitado na intenção.
        val intencao = PendingIntent.getBroadcast(
            contexto,
            conversaId.toInt(),
            AcoesNotificacaoReceiver.intencao(contexto, AcoesNotificacaoReceiver.ACAO_RESPONDER, conversaId),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notificacao, contexto.getString(R.string.notificacao_responder), intencao)
            .addRemoteInput(entrada)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    private fun acaoMarcarLida(conversaId: Long): NotificationCompat.Action {
        val intencao = PendingIntent.getBroadcast(
            contexto,
            conversaId.toInt(),
            AcoesNotificacaoReceiver.intencao(contexto, AcoesNotificacaoReceiver.ACAO_MARCAR_LIDA, conversaId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(R.drawable.ic_notificacao, contexto.getString(R.string.notificacao_marcar_lida), intencao)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    /** App na frente em outra tela: só o som (NOT-01), no máximo um a cada 1,5 s. */
    private fun tocarSom() {
        val agora = System.currentTimeMillis()
        if (agora - ultimoSomMs < INTERVALO_SOM_MS) return
        ultimoSomMs = agora
        runCatching { RingtoneManager.getRingtone(contexto, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
    }

    private fun podeNotificar(): Boolean {
        if (!gerenciador.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val TAG = "conversa"
        const val MAX_NA_NOTIFICACAO = 7
        private const val TEMPO_RESPOSTA_VISIVEL_MS = 2_000L
        private const val ATALHOS_RECENTES = 4
        private const val INTERVALO_SOM_MS = 1_500L
    }
}
