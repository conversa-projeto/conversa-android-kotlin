package com.conversa.app.core.data.anexos

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.tipoPorMime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

/** Arquivo escolhido no aparelho, pronto para entrar na fila do campo. */
data class AnexoLocal(val uri: String, val nome: String, val tamanho: Long, val mime: String?, val tipo: TipoConteudo)

/** Lê arquivos pelo URI (galeria, documentos, câmera). Interface para os testes. */
interface FontesArquivo {
    /** Nome, tamanho e tipo; `null` se o URI não pode ser lido. */
    fun descrever(uri: String, tipo: TipoConteudo? = null): AnexoLocal?

    /** Fonte para o envio; `null` se o acesso ao URI acabou (ex.: permissão temporária). */
    fun abrir(uri: String): FonteArquivo?

    /**
     * O arquivo já foi enviado (ou a mensagem descartada): devolve a permissão guardada
     * (o Android limita quantas um app pode ter) e apaga a foto da câmera do cache.
     */
    fun liberar(uri: String)
}

class FontesArquivoAndroid @Inject constructor(@ApplicationContext private val contexto: Context) : FontesArquivo {
    override fun descrever(uri: String, tipo: TipoConteudo?): AnexoLocal? = runCatching {
        val alvo = uri.toUri()
        val resolver = contexto.contentResolver
        var nome: String? = null
        var tamanho = -1L
        resolver.query(alvo, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                nome = cursor.getString(0)
                if (!cursor.isNull(1)) tamanho = cursor.getLong(1)
            }
        }
        // Sem tamanho informado (alguns provedores): mede abrindo o arquivo.
        if (tamanho < 0) tamanho = resolver.openFileDescriptor(alvo, "r")?.use { it.statSize } ?: 0
        val mime = resolver.getType(alvo)
        AnexoLocal(
            uri = uri,
            nome = nomeSeguro(nome ?: alvo.lastPathSegment ?: "arquivo"),
            tamanho = tamanho,
            mime = mime,
            tipo = tipo ?: tipoPorMime(mime),
        )
    }.getOrNull()

    override fun abrir(uri: String): FonteArquivo? {
        val anexo = descrever(uri) ?: return null
        return object : FonteArquivo {
            override val nome = anexo.nome
            override val tamanho = anexo.tamanho
            override val mime = anexo.mime

            override fun abrir(): InputStream = contexto.contentResolver.openInputStream(uri.toUri())
                ?: throw java.io.IOException("Arquivo indisponível")
        }
    }

    override fun liberar(uri: String) {
        val alvo = uri.toUri()
        // Foto da câmera, gravação ou compartilhado: arquivo do próprio app (FileProvider, pastas do cache).
        if (alvo.authority == contexto.packageName + ".arquivos") {
            val segmentos = alvo.pathSegments.map(::nomeSeguro)
            when {
                segmentos.size == 2 && segmentos[0] == "camera" -> File(File(contexto.cacheDir, "camera"), segmentos[1]).delete()
                // gravacoes/<n>/audio-....m4a: apaga a pasta da gravação inteira (trechos que sobraram).
                segmentos.size == 3 && segmentos[0] == "gravacoes" ->
                    File(File(contexto.cacheDir, "gravacoes"), segmentos[1]).deleteRecursively()
                // compartilhados/<n>/<arquivo>: outros arquivos do mesmo compartilhamento podem estar na fila.
                segmentos.size == 3 && segmentos[0] == "compartilhados" -> {
                    val pasta = File(File(contexto.cacheDir, "compartilhados"), segmentos[1])
                    File(pasta, segmentos[2]).delete()
                    if (pasta.list().isNullOrEmpty()) pasta.delete()
                }
            }
            return
        }
        try {
            contexto.contentResolver.releasePersistableUriPermission(alvo, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Não havia permissão guardada (provedor sem suporte): nada a devolver.
        }
    }
}

/** Só o nome do arquivo: sem caminho (`../`), sem barras, sem caracteres de controle e nunca `.` ou `..`. */
fun nomeSeguro(nome: String): String = nome.substringAfterLast('/').substringAfterLast('\\')
    .filter { it >= ' ' && it != ':' }
    .trim()
    .takeUnless { it.isBlank() || it.all { c -> c == '.' } }
    ?: "arquivo"
