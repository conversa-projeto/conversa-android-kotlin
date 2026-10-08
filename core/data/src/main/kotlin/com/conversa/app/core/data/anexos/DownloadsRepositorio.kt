package com.conversa.app.core.data.anexos

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.conversa.app.core.network.di.DespachanteEs
import com.conversa.app.core.network.di.EscopoAplicacao
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Fim de um "Baixar" (ANX-09). */
sealed interface ResultadoDownload {
    val nome: String

    /** [uri]: o arquivo salvo (`content://`), para abrir. */
    data class Salvo(override val nome: String, val uri: String, val mime: String) : ResultadoDownload

    data class Falhou(override val nome: String) : ResultadoDownload
}

/** Onde o arquivo baixado é gravado. Pendente até [concluir] (some se [descartar]). */
interface DestinoDownload {
    val uri: String

    fun abrir(): OutputStream

    fun concluir()

    fun descartar()
}

/** A pasta pública de downloads do aparelho. */
interface PastaDownloads {
    fun criar(nome: String, mime: String): DestinoDownload

    /** Arquivo escolhido pela pessoa ("Salvar como", Android 9). */
    fun escolhido(uri: String): DestinoDownload
}

/** Aviso de download concluído (notificação, quando permitida). */
fun interface AvisoDownload {
    fun concluido(resultado: ResultadoDownload.Salvo)
}

/**
 * "Baixar" um anexo (TODO 4.7, ANX-09): primeiro para o cache ([ArquivosLocais.baixar],
 * que renova a URL vencida e usa `.part`), depois copia para Downloads/Conversa. Roda no
 * escopo do app: sair da conversa não interrompe. No fim, [resultados] (a tela mostra
 * "Salvo em Downloads") e o [AvisoDownload].
 */
@Singleton
class DownloadsRepositorio @Inject constructor(
    private val arquivos: ArquivosLocais,
    private val pasta: PastaDownloads,
    private val aviso: AvisoDownload,
    @EscopoAplicacao private val escopo: CoroutineScope,
    @DespachanteEs private val es: CoroutineDispatcher,
) {
    private val _resultados = MutableSharedFlow<ResultadoDownload>(extraBufferCapacity = 8)
    val resultados: SharedFlow<ResultadoDownload> = _resultados.asSharedFlow()

    /** Android 10+: grava direto em Downloads/Conversa (MediaStore, sem permissão). */
    fun salvarEmDownloads(identificador: String, nome: String, mime: String?) {
        val tipo = mimeDe(nome, mime)
        val seguro = nomeSeguro(nome)
        escopo.launch { salvar(identificador, seguro, tipo) { pasta.criar(seguro, tipo) } }
    }

    /** Android 9: no arquivo que a pessoa escolheu em "Salvar como" (sem permissão de armazenamento). */
    fun salvarEm(uri: String, identificador: String, nome: String, mime: String?) {
        val tipo = mimeDe(nome, mime)
        escopo.launch { salvar(identificador, nomeSeguro(nome), tipo) { pasta.escolhido(uri) } }
    }

    private suspend fun salvar(identificador: String, nome: String, mime: String, criar: () -> DestinoDownload) {
        val resultado = withContext(es) {
            val origem = arquivos.baixar(identificador, nome).getOrElse { return@withContext ResultadoDownload.Falhou(nome) }
            val destino = runCatching(criar).getOrElse { return@withContext ResultadoDownload.Falhou(nome) }
            try {
                destino.abrir().use { saida -> origem.inputStream().use { it.copyTo(saida) } }
                destino.concluir()
                ResultadoDownload.Salvo(nome, destino.uri, mime)
            } catch (e: IOException) {
                Timber.w("Não salvou em Downloads: %s", e.javaClass.simpleName)
                destino.descartar()
                ResultadoDownload.Falhou(nome)
            }
        }
        if (resultado is ResultadoDownload.Salvo) aviso.concluido(resultado)
        _resultados.emit(resultado)
    }

    private fun mimeDe(nome: String, mime: String?): String = mime
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(extensaoDe(nome))
        ?: "application/octet-stream"
}

/** Downloads/Conversa pelo MediaStore (Android 10+), ou o URI escolhido em "Salvar como". */
class PastaDownloadsAndroid @Inject constructor(@ApplicationContext private val contexto: Context) : PastaDownloads {
    override fun criar(nome: String, mime: String): DestinoDownload {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IllegalStateException("Antes do Android 10, use \"Salvar como\"")
        return criarNoMediaStore(nome, mime)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun criarNoMediaStore(nome: String, mime: String): DestinoDownload {
        val resolver = contexto.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, nome)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Conversa")
            // Pendente: outros apps só veem o arquivo depois de completo.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores) ?: throw IOException("MediaStore recusou")
        return object : DestinoDownload {
            override val uri = uri.toString()

            override fun abrir(): OutputStream = resolver.openOutputStream(uri) ?: throw IOException("Sem acesso ao destino")

            override fun concluir() {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            }

            override fun descartar() {
                resolver.delete(uri, null, null)
            }
        }
    }

    override fun escolhido(uri: String): DestinoDownload {
        val alvo: Uri = uri.toUri()
        val resolver = contexto.contentResolver
        return object : DestinoDownload {
            override val uri = uri

            override fun abrir(): OutputStream = resolver.openOutputStream(alvo) ?: throw IOException("Sem acesso ao destino")

            override fun concluir() = Unit

            override fun descartar() {
                runCatching { android.provider.DocumentsContract.deleteDocument(resolver, alvo) }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadsModulo {
    @Binds
    abstract fun pastaDownloads(pasta: PastaDownloadsAndroid): PastaDownloads

    @Binds
    abstract fun avisoDownload(aviso: AvisoDownloadNotificacao): AvisoDownload
}
