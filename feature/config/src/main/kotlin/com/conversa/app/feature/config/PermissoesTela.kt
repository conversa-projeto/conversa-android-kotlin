package com.conversa.app.feature.config

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.conversa.app.core.ui.tema.ConversaTema

/** O que o app pode usar neste aparelho (8.5, FC-806, CFG-04). */
internal enum class Permissao { NOTIFICACOES, MICROFONE, CAMERA, TELA_CHEIA, BATERIA }

/** A permissão de execução (o diálogo do sistema) de cada item, quando existe. */
private fun Permissao.deExecucao(): String? = when (this) {
    Permissao.NOTIFICACOES -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    Permissao.MICROFONE -> Manifest.permission.RECORD_AUDIO
    Permissao.CAMERA -> Manifest.permission.CAMERA
    Permissao.TELA_CHEIA, Permissao.BATERIA -> null
}

/** Tela cheia só existe como permissão a partir do Android 14. */
private fun Permissao.disponivel(): Boolean = this != Permissao.TELA_CHEIA || Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

/** O estado de agora (relido a cada volta das configurações do sistema). */
private fun concedida(contexto: Context, permissao: Permissao): Boolean = when (permissao) {
    Permissao.NOTIFICACOES -> NotificationManagerCompat.from(contexto).areNotificationsEnabled()
    Permissao.MICROFONE, Permissao.CAMERA ->
        ContextCompat.checkSelfPermission(contexto, checkNotNull(permissao.deExecucao())) == PackageManager.PERMISSION_GRANTED
    Permissao.TELA_CHEIA ->
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            contexto.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() != false
    Permissao.BATERIA -> contexto.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(contexto.packageName) == true
}

/** A tela do sistema que resolve o item (quando o diálogo não serve ou já foi negado de vez). */
private fun configuracoesDe(contexto: Context, permissao: Permissao): Intent {
    val pacote = Uri.fromParts("package", contexto.packageName, null)
    return when (permissao) {
        Permissao.NOTIFICACOES -> Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
        ).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
        Permissao.TELA_CHEIA -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pacote)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacote)
        }
        Permissao.BATERIA -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        Permissao.MICROFONE, Permissao.CAMERA -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pacote)
    }
}

/** Abre a tela do sistema; se o aparelho não tiver aquela, a do app. */
internal fun abrirNoSistema(contexto: Context, intent: Intent) {
    try {
        contexto.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        runCatching {
            contexto.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", contexto.packageName, null)),
            )
        }
    }
}

/**
 * Permissões (8.5, FC-806, CFG-04): o estado de cada uma e um botão. Com diálogo do sistema,
 * "Permitir" pede; se a pessoa já negou de vez, o pedido volta sem diálogo e o app abre a
 * tela do sistema. Tela cheia e bateria só se resolvem lá.
 */
@Composable
fun PermissoesRotaTela(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val atividade = LocalActivity.current
    var estados by remember { mutableStateOf(Permissao.entries.associateWith { concedida(contexto, it) }) }
    val reler = { estados = Permissao.entries.associateWith { concedida(contexto, it) } }
    LifecycleResumeEffect(Unit) {
        reler()
        onPauseOrDispose {}
    }
    var pedindo by remember { mutableStateOf<Permissao?>(null) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedeu ->
        val permissao = pedindo
        pedindo = null
        reler()
        val semDialogo = permissao?.deExecucao()?.let { atividade?.let { a -> !deveExplicar(a, it) } } == true
        if (!concedeu && permissao != null && semDialogo) abrirNoSistema(contexto, configuracoesDe(contexto, permissao))
    }
    SubTela(titulo = stringResource(R.string.permissoes_titulo), aoVoltar = aoVoltar) { modificador ->
        Column(modificador.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.permissoes_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.padding(16.dp),
            )
            Permissao.entries.filter { it.disponivel() }.forEach { permissao ->
                HorizontalDivider()
                LinhaPermissao(
                    permissao = permissao,
                    concedida = estados[permissao] == true,
                    aoResolver = {
                        val deExecucao = permissao.deExecucao()
                        if (deExecucao != null) {
                            pedindo = permissao
                            pedir.launch(deExecucao)
                        } else {
                            abrirNoSistema(contexto, configuracoesDe(contexto, permissao))
                        }
                    },
                )
            }
        }
    }
}

private fun deveExplicar(atividade: Activity, permissao: String): Boolean = atividade.shouldShowRequestPermissionRationale(permissao)

@Composable
private fun LinhaPermissao(permissao: Permissao, concedida: Boolean, aoResolver: () -> Unit) {
    val (titulo, descricao) = when (permissao) {
        Permissao.NOTIFICACOES -> R.string.permissoes_notificacoes to R.string.permissoes_notificacoes_texto
        Permissao.MICROFONE -> R.string.permissoes_microfone to R.string.permissoes_microfone_texto
        Permissao.CAMERA -> R.string.permissoes_camera to R.string.permissoes_camera_texto
        Permissao.TELA_CHEIA -> R.string.permissoes_tela_cheia to R.string.permissoes_tela_cheia_texto
        Permissao.BATERIA -> R.string.permissoes_bateria to R.string.permissoes_bateria_texto
    }
    val estado = when {
        permissao == Permissao.BATERIA -> if (concedida) R.string.permissoes_bateria_livre else R.string.permissoes_bateria_otimizada
        concedida -> R.string.permissoes_concedida
        else -> R.string.permissoes_nao_concedida
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(titulo), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(descricao), style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario)
            Text(
                stringResource(estado),
                style = MaterialTheme.typography.labelMedium,
                color = if (concedida) ConversaTema.cores.chamadaAtender else MaterialTheme.colorScheme.error,
            )
        }
        if (!concedida) {
            OutlinedButton(onClick = aoResolver) {
                Text(
                    stringResource(
                        if (permissao.deExecucao() != null) R.string.permissoes_permitir else R.string.permissoes_abrir_configuracoes,
                    ),
                )
            }
        }
    }
}
