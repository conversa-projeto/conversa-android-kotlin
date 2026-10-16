package com.conversa.app.notificacoes

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.ui.componentes.inicialDoNome
import com.conversa.app.core.ui.tema.coresDoTema
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Avatares da notificação e dos atalhos de conversa (NOT-02): a foto (URL assinada, pelo Coil,
 * recortada em círculo) ou, sem foto, a inicial ou o emoji do grupo, nas cores do `Avatar` do app.
 * Guarda a última foto de cada conversa e pessoa: as URLs vencem em 10 min e mudam a cada leitura,
 * e o atalho é republicado sem rede.
 */
@Singleton
class IconesDeAvatar @Inject constructor(@ApplicationContext private val contexto: Context) {
    private val fotos = LruCache<String, Bitmap>(FOTOS_GUARDADAS)

    /** A foto da conversa (direta ou grupo com imagem), o emoji do grupo ou a inicial do título. */
    suspend fun daConversa(conversa: Conversa): IconCompat =
        IconCompat.createWithBitmap(foto(chave(conversa), conversa.avatarUrl) ?: desenhado(conversa))

    /** Como [daConversa], sem ir à rede: a foto guardada ou o desenho. */
    fun guardadoDaConversa(conversa: Conversa): IconCompat =
        IconCompat.createWithBitmap(fotos.get(chave(conversa)) ?: desenhado(conversa))

    /** A foto de quem mandou; sem foto, nulo (o sistema desenha a inicial). */
    suspend fun daPessoa(usuarioId: Long, url: String?): IconCompat? = foto("u$usuarioId", url)?.let(IconCompat::createWithBitmap)

    /** A foto em bitmap, para o ícone grande do Android 9 e 10. */
    fun fotoGuardada(conversa: Conversa): Bitmap? = fotos.get(chave(conversa))

    /** Fim da sessão: as fotos são de outra conta. */
    fun limpar() = fotos.evictAll()

    private fun chave(conversa: Conversa) = "c${conversa.id}"

    private suspend fun foto(chave: String, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return fotos.get(chave)
        val pedido = ImageRequest.Builder(contexto)
            .data(url)
            .size(TAMANHO_PX)
            .transformations(CircleCropTransformation())
            .allowHardware(false)
            .build()
        val bitmap = try {
            withTimeoutOrNull(ESPERA_FOTO_MS) {
                (SingletonImageLoader.get(contexto).execute(pedido) as? SuccessResult)?.image?.toBitmap()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Avatar da notificação: %s", e.javaClass.simpleName)
            null
        }
        if (bitmap != null) fotos.put(chave, bitmap)
        return bitmap ?: fotos.get(chave)
    }

    private fun desenhado(conversa: Conversa): Bitmap {
        val emoji = conversa.emoji?.takeIf { it.isNotBlank() }
        return desenhar(emoji ?: inicialDoNome(conversa.titulo), ehEmoji = emoji != null)
    }

    /** Círculo com a letra (ou o emoji) no meio, como o `Avatar` sem foto. */
    private fun desenhar(texto: String, ehEmoji: Boolean): Bitmap {
        val escuro = (contexto.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val cores = coresDoTema(escuro)
        val bitmap = createBitmap(TAMANHO_PX, TAMANHO_PX)
        val tela = Canvas(bitmap)
        val meio = TAMANHO_PX / 2f
        tela.drawCircle(meio, meio, meio, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cores.avatarFundo.toArgb() })
        val letra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cores.avatarLetra.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = TAMANHO_PX * if (ehEmoji) 0.5f else 0.42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        tela.drawText(texto, meio, meio - (letra.descent() + letra.ascent()) / 2, letra)
        return bitmap
    }

    private companion object {
        const val TAMANHO_PX = 256
        const val FOTOS_GUARDADAS = 64
        const val ESPERA_FOTO_MS = 5_000L
    }
}
