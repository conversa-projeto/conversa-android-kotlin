package com.conversa.app.core.data.anexos

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.core.net.toUri
import com.conversa.app.core.model.BARRAS_ONDA
import com.conversa.app.core.model.DURACAO_MAXIMA_AUDIO
import com.conversa.app.core.model.DadosAudio
import com.conversa.app.core.model.arredondarDuracao
import com.conversa.app.core.model.picos
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Mede a duração e a onda de um áudio antes do envio (ANX-10, como o `medirAudio` do web):
 * o player de quem recebe mostra os dois sem baixar o arquivo. Nulo se não deu para medir
 * (formato desconhecido, arquivo grande demais): o áudio vai sem os dados, como antes.
 */
interface MedidorAudio {
    suspend fun medir(uri: String, tamanho: Long): DadosAudio?

    companion object {
        /** Acima disso decodificar pesa demais (o mesmo limite do web). */
        const val TAMANHO_MAXIMO = 30L * 1024 * 1024
    }
}

/** Decodifica o áudio (MediaExtractor + MediaCodec) para PCM e guarda o pico de cada janela de 10 ms. */
class MedidorAudioAndroid @Inject constructor(@ApplicationContext private val contexto: Context) : MedidorAudio {
    override suspend fun medir(uri: String, tamanho: Long): DadosAudio? {
        if (tamanho > MedidorAudio.TAMANHO_MAXIMO) return null
        return withContext(Dispatchers.Default) {
            runCatching { decodificar(uri) }
                .onFailure { Timber.w(it, "Não deu para medir o áudio") }
                .getOrNull()
        }
    }

    private fun decodificar(uri: String): DadosAudio? {
        val extrator = MediaExtractor()
        var decodificador: MediaCodec? = null
        try {
            extrator.setDataSource(contexto, uri.toUri(), null)
            val faixa = (0 until extrator.trackCount).firstOrNull {
                extrator.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val formato = extrator.getTrackFormat(faixa)
            extrator.selectTrack(faixa)
            val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
            decodificador = codec
            codec.configure(formato, null, null, 0)
            codec.start()

            var taxa = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var canais = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var flutuante = false
            val janelas = ArrayList<Float>()
            var porJanela = maxOf(1, taxa * canais / 100)
            var naJanela = 0
            var maximo = 0f
            var amostras = 0L
            val info = MediaCodec.BufferInfo()
            var fimDaEntrada = false
            while (true) {
                if (!fimDaEntrada) {
                    val entrada = codec.dequeueInputBuffer(ESPERA_US)
                    if (entrada >= 0) {
                        val lidos = extrator.readSampleData(codec.getInputBuffer(entrada)!!, 0)
                        if (lidos < 0) {
                            codec.queueInputBuffer(entrada, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            fimDaEntrada = true
                        } else {
                            codec.queueInputBuffer(entrada, 0, lidos, extrator.sampleTime, 0)
                            extrator.advance()
                        }
                    }
                }
                val saida = codec.dequeueOutputBuffer(info, ESPERA_US)
                if (saida == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val novo = codec.outputFormat
                    taxa = novo.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    canais = novo.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    flutuante = novo.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        novo.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    porJanela = maxOf(1, taxa * canais / 100)
                } else if (saida >= 0) {
                    val dados = codec.getOutputBuffer(saida)!!.order(ByteOrder.nativeOrder())
                    dados.position(info.offset)
                    dados.limit(info.offset + info.size)
                    // Todos os canais juntos: o maior valor de cada janela de 10 ms.
                    if (flutuante) {
                        val valores = dados.asFloatBuffer()
                        while (valores.hasRemaining()) {
                            val valor = abs(valores.get())
                            if (valor > maximo) maximo = valor
                            amostras++
                            if (++naJanela >= porJanela) {
                                janelas += maximo.coerceAtMost(1f)
                                naJanela = 0
                                maximo = 0f
                            }
                        }
                    } else {
                        val valores = dados.asShortBuffer()
                        while (valores.hasRemaining()) {
                            val valor = abs(valores.get().toInt()) / 32_768f
                            if (valor > maximo) maximo = valor
                            amostras++
                            if (++naJanela >= porJanela) {
                                janelas += maximo
                                naJanela = 0
                                maximo = 0f
                            }
                        }
                    }
                    codec.releaseOutputBuffer(saida, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            if (naJanela > 0) janelas += maximo
            if (amostras == 0L || taxa <= 0 || canais <= 0) return null
            val segundos = arredondarDuracao(amostras.toDouble() / (taxa.toLong() * canais))
            if (segundos <= 0.0 || segundos > DURACAO_MAXIMA_AUDIO) return null
            return DadosAudio(segundos, picos(janelas.toFloatArray(), BARRAS_ONDA))
        } finally {
            runCatching { decodificador?.stop() }
            runCatching { decodificador?.release() }
            extrator.release()
        }
    }

    private companion object {
        const val ESPERA_US = 10_000L
    }
}
