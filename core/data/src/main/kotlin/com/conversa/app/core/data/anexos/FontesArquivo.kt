package com.conversa.app.core.data.anexos

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.tipoPorMime
import dagger.hilt.android.qualifiers.ApplicationContext
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
}

/** Só o nome do arquivo: sem caminho (`../`), sem barras e sem caracteres de controle. */
fun nomeSeguro(nome: String): String = nome.substringAfterLast('/').substringAfterLast('\\')
    .filter { it >= ' ' && it != ':' }
    .trim()
    .ifBlank { "arquivo" }
