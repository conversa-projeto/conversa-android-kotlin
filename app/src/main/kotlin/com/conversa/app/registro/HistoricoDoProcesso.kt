package com.conversa.app.registro

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import timber.log.Timber

/**
 * Por que o processo anterior terminou (Android 11+) e por que este começou (Android 15+): o
 * sistema guarda e o app lê na abertura. É o que diz se o app foi fechado por falta de memória,
 * congelado, parado pela pessoa, travou (ANR, com a pilha) ou caiu.
 */
internal class HistoricoDoProcesso(private val contexto: Context) {
    private val atividades = contexto.getSystemService(ActivityManager::class.java)
    private val preferencias = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)

    fun registrar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) registrarSaidas()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) registrarInicio()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun registrarSaidas() {
        val saidas = runCatching { atividades?.getHistoricalProcessExitReasons(contexto.packageName, 0, MAXIMO_SAIDAS) }
            .getOrNull().orEmpty()
        val ultimaVista = preferencias.getLong(ULTIMA_SAIDA, 0)
        val novas = saidas.filter { it.timestamp > ultimaVista }.sortedBy { it.timestamp }
        if (novas.isEmpty()) {
            Timber.tag(TAG).d("Nenhuma saída nova do processo desde a última abertura")
            return
        }
        for (saida in novas) {
            val prioridade = when (saida.reason) {
                ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE, ApplicationExitInfo.REASON_ANR -> Log.ERROR
                ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
                ApplicationExitInfo.REASON_FREEZER, ApplicationExitInfo.REASON_SIGNALED,
                -> Log.WARN
                else -> Log.INFO
            }
            Timber.tag(TAG).log(
                prioridade,
                "O processo %d (%s) terminou em %s: %s%s; importância %d, status %d, memória pss %d kB, rss %d kB",
                saida.pid,
                saida.processName,
                momento(saida.timestamp),
                nomeDaSaida(saida.reason),
                saida.description?.let { " ($it)" }.orEmpty(),
                saida.importance,
                saida.status,
                saida.pss,
                saida.rss,
            )
            if (saida.reason == ApplicationExitInfo.REASON_ANR) registrarPilhaDoAnr(saida)
        }
        preferencias.edit { putLong(ULTIMA_SAIDA, novas.maxOf { it.timestamp }) }
    }

    /** O Android guarda o despejo das threads do ANR; vai para o registro até [MAXIMO_LINHAS_ANR] linhas. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun registrarPilhaDoAnr(saida: ApplicationExitInfo) {
        val linhas = runCatching {
            saida.traceInputStream?.bufferedReader()?.useLines { it.take(MAXIMO_LINHAS_ANR).toList() }
        }.getOrNull() ?: return
        Timber.tag(TAG).e("Pilhas do ANR (%d linhas):\n%s", linhas.size, linhas.joinToString("\n"))
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun registrarInicio() {
        runCatching {
            atividades?.addApplicationStartInfoCompletionListener(ContextCompat.getMainExecutor(contexto)) { inicio ->
                Timber.tag(TAG).i(
                    "Este processo começou por: %s; partida %s%s",
                    nomeDoInicio(inicio.reason),
                    nomeDaPartida(inicio.startType),
                    if (inicio.wasForceStopped()) "; o app tinha sido forçado a parar antes" else "",
                )
            }
        }
    }

    companion object {
        const val TAG = "Processo"
        private const val PREFERENCIAS = "registro"
        private const val ULTIMA_SAIDA = "ultima_saida_vista"
        private const val MAXIMO_SAIDAS = 16
        private const val MAXIMO_LINHAS_ANR = 400
        private val FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx", Locale.ROOT)

        private fun momento(ms: Long) = FORMATO.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

        fun nomeDaSaida(motivo: Int): String = when (motivo) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "o próprio app saiu"
            ApplicationExitInfo.REASON_SIGNALED -> "encerrado por um sinal do sistema"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "pouca memória no aparelho"
            ApplicationExitInfo.REASON_CRASH -> "erro no app (crash)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "erro nativo (crash)"
            ApplicationExitInfo.REASON_ANR -> "o app não respondia (ANR)"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "falha ao iniciar"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "uma permissão mudou"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "a pessoa pediu (ex.: forçar parada, limpar dados)"
            ApplicationExitInfo.REASON_USER_STOPPED -> "a pessoa parou o app"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "uma dependência do app morreu"
            ApplicationExitInfo.REASON_OTHER -> "outro motivo do sistema"
            ApplicationExitInfo.REASON_FREEZER -> "congelado pelo sistema"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "o estado do pacote mudou"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "o app foi atualizado"
            else -> "motivo desconhecido ($motivo)"
        }

        fun nomeDoInicio(motivo: Int): String = when (motivo) {
            ApplicationStartInfo.START_REASON_ALARM -> "alarme"
            ApplicationStartInfo.START_REASON_BACKUP -> "backup"
            ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> "celular ligado"
            ApplicationStartInfo.START_REASON_BROADCAST -> "aviso do sistema (broadcast)"
            ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> "provedor de conteúdo"
            ApplicationStartInfo.START_REASON_JOB -> "trabalho agendado (WorkManager)"
            ApplicationStartInfo.START_REASON_LAUNCHER -> "ícone do app"
            ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> "apps recentes"
            ApplicationStartInfo.START_REASON_PUSH -> "push"
            ApplicationStartInfo.START_REASON_SERVICE -> "serviço"
            ApplicationStartInfo.START_REASON_START_ACTIVITY -> "abrir uma tela (notificação, link, compartilhar)"
            else -> "outro ($motivo)"
        }

        fun nomeDaPartida(tipo: Int): String = when (tipo) {
            ApplicationStartInfo.START_TYPE_COLD -> "a frio"
            ApplicationStartInfo.START_TYPE_WARM -> "morna"
            ApplicationStartInfo.START_TYPE_HOT -> "a quente"
            else -> "desconhecida"
        }
    }
}
