package com.conversa.app.imagens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.model.PREFIXO_LOCAL
import com.conversa.app.core.ui.componentes.QuadroVideo
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Primeiro quadro de um vídeo ([QuadroVideo], ANX-04, TODO 4.4):
 * - já no cache de disco (chave `quadro:<conteudo>`)? Usa;
 * - senão, o `MediaMetadataRetriever` lê o vídeo pela URL assinada, por partes (um vídeo de
 *   200 MB não é baixado inteiro), ou o arquivo local; o quadro vai para o disco em JPEG;
 * - URL vencida (não abriu): pede outra e tenta uma vez.
 */
class FetcherQuadroVideo(
    private val quadro: QuadroVideo,
    private val opcoes: Options,
    private val carregador: ImageLoader,
    private val contexto: Context,
    private val anexos: AnexosRepositorio,
) : Fetcher {
    private val chave = "quadro:" + quadro.conteudo

    override suspend fun fetch(): FetchResult? {
        val cache = carregador.diskCache
        cache?.openSnapshot(chave)?.let { return resultado(it, cache, DataSource.DISK) }
        val imagem = withContext(Dispatchers.IO) { extrair() } ?: throw IOException("Vídeo sem quadro")
        if (cache == null) throw IOException("Sem cache de disco")
        val editor = cache.openEditor(chave) ?: throw IOException("Cache ocupado")
        try {
            withContext(Dispatchers.IO) {
                cache.fileSystem.write(editor.data) { imagem.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, outputStream()) }
            }
        } catch (e: Exception) {
            editor.abort()
            throw e
        } finally {
            imagem.recycle()
        }
        val pronto = editor.commitAndOpenSnapshot() ?: throw IOException("Cache não gravou")
        return resultado(pronto, cache, DataSource.NETWORK)
    }

    private fun resultado(copia: DiskCache.Snapshot, cache: DiskCache, origem: DataSource) = SourceFetchResult(
        source = ImageSource(copia.data, cache.fileSystem, chave, copia),
        mimeType = "image/jpeg",
        dataSource = origem,
    )

    private suspend fun extrair(): Bitmap? {
        if (quadro.conteudo.startsWith(PREFIXO_LOCAL)) {
            return lerQuadro {
                setDataSource(contexto, quadro.conteudo.removePrefix(PREFIXO_LOCAL).toUri())
            }
        }
        val primeira = anexos.url(quadro.conteudo).getOrNull() ?: return null
        lerQuadro { setDataSource(primeira, emptyMap()) }?.let { return it }
        anexos.esquecerUrl(quadro.conteudo)
        val nova = anexos.url(quadro.conteudo).getOrNull() ?: return null
        return lerQuadro { setDataSource(nova, emptyMap()) }
    }

    private fun lerQuadro(abrir: MediaMetadataRetriever.() -> Unit): Bitmap? {
        val leitor = MediaMetadataRetriever()
        return try {
            leitor.abrir()
            leitor.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, LADO_MAXIMO, LADO_MAXIMO)
        } catch (e: Exception) {
            Timber.d("Quadro do vídeo não saiu: %s", e.javaClass.simpleName)
            null
        } finally {
            leitor.release()
        }
    }

    class Fabrica(private val contexto: Context, private val anexos: () -> AnexosRepositorio) : Fetcher.Factory<QuadroVideo> {
        override fun create(data: QuadroVideo, options: Options, imageLoader: ImageLoader): Fetcher =
            FetcherQuadroVideo(data, options, imageLoader, contexto, anexos())
    }

    class Chave : Keyer<QuadroVideo> {
        override fun key(data: QuadroVideo, options: Options): String = "quadro:" + data.conteudo
    }

    private companion object {
        const val LADO_MAXIMO = 640
        const val QUALIDADE_JPEG = 85
    }
}
