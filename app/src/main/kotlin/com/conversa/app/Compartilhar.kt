package com.conversa.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/** O que veio num "Compartilhar" de outro app (AND-10): texto e/ou URIs de arquivos. */
data class CompartilhamentoRecebido(val texto: String?, val uris: List<String>)

/**
 * Lê `ACTION_SEND` (um item) e `ACTION_SEND_MULTIPLE` (vários). `null` se o intent não é um
 * compartilhamento ou veio vazio. A filtragem do que é aceito (só `content://` de outro app)
 * fica em `Compartilhamentos.receber`.
 */
fun lerCompartilhamento(intent: Intent): CompartilhamentoRecebido? {
    val uris = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> return null
    }.map { it.toString() }
    val texto = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    if (texto == null && uris.isEmpty()) return null
    return CompartilhamentoRecebido(texto, uris)
}
