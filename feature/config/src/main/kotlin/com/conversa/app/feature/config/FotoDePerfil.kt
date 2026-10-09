package com.conversa.app.feature.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.conversa.app.core.model.LADO_AVATAR
import com.conversa.app.core.model.QUALIDADE_AVATAR
import com.conversa.app.core.model.recorteQuadradoCentral
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prepara a foto escolhida como o web (`redimensionarImagem`): o quadrado do meio, reduzido a
 * 256×256, em JPEG 85%. O `ImageDecoder` já gira pela orientação do EXIF; fotos grandes são
 * lidas reduzidas para não estourar a memória. Nulo = não deu para ler a imagem.
 */
class PreparadorFotoDePerfil @Inject constructor(@ApplicationContext private val contexto: Context) {
    suspend fun preparar(uri: Uri): ByteArray? = withContext(Dispatchers.Default) {
        runCatching {
            val fonte = ImageDecoder.createSource(contexto.contentResolver, uri)
            val imagem = ImageDecoder.decodeBitmap(fonte) { decodificador, info, _ ->
                val menorLado = minOf(info.size.width, info.size.height)
                decodificador.setTargetSampleSize((menorLado / (LADO_AVATAR * 2)).coerceAtLeast(1))
                decodificador.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val recorte = recorteQuadradoCentral(imagem.width, imagem.height)
            val quadrado = Bitmap.createBitmap(imagem, recorte.x, recorte.y, recorte.lado, recorte.lado)
            val final = Bitmap.createScaledBitmap(quadrado, LADO_AVATAR, LADO_AVATAR, true)
            ByteArrayOutputStream().use { saida ->
                final.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_AVATAR, saida)
                saida.toByteArray()
            }
        }.getOrNull()
    }
}
