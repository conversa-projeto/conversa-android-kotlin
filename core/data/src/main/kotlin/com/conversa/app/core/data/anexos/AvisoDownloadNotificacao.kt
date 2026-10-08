package com.conversa.app.core.data.anexos

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.conversa.app.core.data.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Notificação "Download concluído" (TODO 4.7): toque abre o arquivo. Só aparece se a
 * pessoa permitiu notificações (a permissão é pedida na etapa 5); senão fica o aviso na tela.
 */
class AvisoDownloadNotificacao @Inject constructor(@ApplicationContext private val contexto: Context) : AvisoDownload {
    override fun concluido(resultado: ResultadoDownload.Salvo) {
        val gerenciador = NotificationManagerCompat.from(contexto)
        if (!podeNotificar(gerenciador)) return
        gerenciador.createNotificationChannel(
            NotificationChannelCompat.Builder(CANAL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(contexto.getString(R.string.canal_downloads))
                .build(),
        )
        val abrir = Intent(Intent.ACTION_VIEW)
            .setDataAndType(resultado.uri.toUri(), resultado.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val toque = PendingIntent.getActivity(
            contexto,
            resultado.uri.hashCode(),
            Intent.createChooser(abrir, null),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificacao = NotificationCompat.Builder(contexto, CANAL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(contexto.getString(R.string.download_concluido))
            .setContentText(resultado.nome)
            .setContentIntent(toque)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // conferida em podeNotificar
        gerenciador.notify(resultado.uri.hashCode(), notificacao)
    }

    private fun podeNotificar(gerenciador: NotificationManagerCompat): Boolean {
        if (!gerenciador.areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CANAL = "downloads"
    }
}
