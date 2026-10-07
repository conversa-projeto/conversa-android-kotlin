package com.conversa.app.imagens

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.network.HttpException
import coil3.request.Options
import coil3.toUri
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.ui.componentes.AnexoRemoto

/**
 * Carrega um [AnexoRemoto] (ANX-14, TODO 4.3):
 * 1. já está no cache de disco (chave = identificador)? Usa, sem pedir URL ao servidor;
 * 2. senão, pede a URL assinada ao [AnexosRepositorio] (com cache) e repassa ao fetcher
 *    de rede do Coil, que grava no disco com a mesma chave;
 * 3. o MinIO recusou (403: URL vencida)? Esquece a URL e tenta uma vez com outra.
 */
class FetcherAnexo(
    private val anexo: AnexoRemoto,
    private val opcoes: Options,
    private val carregador: ImageLoader,
    private val anexos: AnexosRepositorio,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        doDisco()?.let { return it }
        return try {
            daRede()
        } catch (e: HttpException) {
            if (e.response.code != HTTP_PROIBIDO) throw e
            anexos.esquecerUrl(anexo.identificador)
            daRede()
        }
    }

    private fun doDisco(): FetchResult? {
        if (opcoes.diskCachePolicy.readEnabled.not()) return null
        val cache = carregador.diskCache ?: return null
        val copia = cache.openSnapshot(anexo.identificador) ?: return null
        return SourceFetchResult(
            source = ImageSource(copia.data, cache.fileSystem, anexo.identificador, copia),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private suspend fun daRede(): FetchResult? {
        val url = anexos.url(anexo.identificador).getOrThrow()
        val opcoesDoAnexo = opcoes.copy(diskCacheKey = anexo.identificador)
        val (rede) = carregador.components.newFetcher(url.toUri(), opcoesDoAnexo, carregador)
            ?: error("Coil sem fetcher de rede")
        return rede.fetch()
    }

    class Fabrica(private val anexos: () -> AnexosRepositorio) : Fetcher.Factory<AnexoRemoto> {
        override fun create(data: AnexoRemoto, options: Options, imageLoader: ImageLoader): Fetcher =
            FetcherAnexo(data, options, imageLoader, anexos())
    }

    /** Chave do cache de memória = identificador. */
    class Chave : Keyer<AnexoRemoto> {
        override fun key(data: AnexoRemoto, options: Options): String = data.identificador
    }

    private companion object {
        const val HTTP_PROIBIDO = 403
    }
}
