package com.conversa.app.feature.config

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.conversa.app.core.data.notificacoes.CanaisNotificacao
import com.conversa.app.core.ui.tema.ConversaTema

/** Os canais na ordem em que aparecem (os mesmos criados no `Application`). */
private val ORDEM_DOS_CANAIS = listOf(
    CanaisNotificacao.MENSAGENS,
    CanaisNotificacao.CHAMADAS_RECEBIDAS,
    CanaisNotificacao.CHAMADA_ATIVA,
    CanaisNotificacao.SISTEMA,
)

/** Um canal como o sistema o vê agora: nome e descrição já no idioma do aparelho. */
private data class Canal(val id: String, val nome: String, val descricao: String?, val ligado: Boolean)

/**
 * Notificações (8.5, FC-806): as do app e as de cada canal (mensagens, chamadas recebidas,
 * chamada em andamento, sistema), ligadas ou não. Som, vibração e estilo se mudam nas
 * configurações do Android: cada linha abre a do canal.
 */
@Composable
fun NotificacoesRotaTela(aoVoltar: () -> Unit) {
    val contexto = LocalContext.current
    val gerenciador = remember(contexto) { NotificationManagerCompat.from(contexto) }
    fun lerCanais() = ORDEM_DOS_CANAIS.mapNotNull { id ->
        gerenciador.getNotificationChannelCompat(id)?.let {
            Canal(id, it.name?.toString().orEmpty(), it.description, it.importance != NotificationManagerCompat.IMPORTANCE_NONE)
        }
    }
    var appLigado by remember { mutableStateOf(gerenciador.areNotificationsEnabled()) }
    var canais by remember { mutableStateOf(lerCanais()) }
    LifecycleResumeEffect(Unit) {
        appLigado = gerenciador.areNotificationsEnabled()
        canais = lerCanais()
        onPauseOrDispose {}
    }
    SubTela(titulo = stringResource(R.string.notificacoes_titulo), aoVoltar = aoVoltar) { modificador ->
        Column(modificador.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.notificacoes_texto),
                style = MaterialTheme.typography.bodyMedium,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.padding(16.dp),
            )
            Linha(
                titulo = stringResource(R.string.notificacoes_do_app),
                descricao = null,
                ligado = appLigado,
                aoTocar = {
                    abrirNoSistema(
                        contexto,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName),
                    )
                },
            )
            HorizontalDivider()
            Text(
                stringResource(R.string.notificacoes_tipos),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            canais.forEach { canal ->
                // Com as do app desligadas, nenhum canal aparece, mesmo ligado.
                Linha(
                    titulo = canal.nome,
                    descricao = canal.descricao,
                    ligado = appLigado && canal.ligado,
                    aoTocar = {
                        abrirNoSistema(
                            contexto,
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, contexto.packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, canal.id),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun Linha(titulo: String, descricao: String?, ligado: Boolean, aoTocar: () -> Unit) {
    ListItem(
        headlineContent = { Text(titulo) },
        supportingContent = {
            Column {
                descricao?.let { Text(it, color = ConversaTema.cores.textoTerciario) }
                Text(
                    stringResource(if (ligado) R.string.notificacoes_ativadas else R.string.notificacoes_desativadas),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ligado) ConversaTema.cores.chamadaAtender else MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.notificacoes_abrir_no_sistema), onClick = aoTocar),
    )
}
