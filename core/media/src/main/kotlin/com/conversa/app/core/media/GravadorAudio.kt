package com.conversa.app.core.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import timber.log.Timber

/**
 * Grava trechos do microfone em AAC/M4A (TODO 4.6, ANX-11). AAC toca em todos os
 * clientes (navegadores, Windows, Android). Pausar = fechar o trecho; continuar = abrir
 * outro; no fim [juntarTrechos] monta um arquivo só.
 */
interface GravadorAudio {
    /** Começa um trecho em [arquivo]. `false` se o microfone não abriu (ocupado, sem permissão). */
    fun iniciar(arquivo: File): Boolean

    /** Fecha o trecho atual. `false` se ele não ficou válido (curto demais, sem dados). */
    fun parar(): Boolean

    /** Nível do microfone (0..1) desde a última leitura, para a animação. */
    fun nivel(): Float
}

class GravadorMediaRecorder @Inject constructor(@ApplicationContext private val contexto: Context) : GravadorAudio {
    private var gravador: MediaRecorder? = null

    override fun iniciar(arquivo: File): Boolean {
        val novo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(contexto) else criarAntigo()
        return try {
            novo.setAudioSource(MediaRecorder.AudioSource.MIC)
            novo.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            novo.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            novo.setAudioChannels(1)
            novo.setAudioSamplingRate(TAXA_AMOSTRAGEM)
            novo.setAudioEncodingBitRate(TAXA_BITS)
            novo.setOutputFile(arquivo.path)
            novo.prepare()
            novo.start()
            gravador = novo
            true
        } catch (e: Exception) {
            // IOException, IllegalStateException e RuntimeException (microfone ocupado): nunca fecha o app (#48 do legado).
            Timber.w("Microfone não abriu: %s", e.javaClass.simpleName)
            runCatching { novo.release() }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun criarAntigo() = MediaRecorder()

    override fun parar(): Boolean {
        val atual = gravador ?: return false
        gravador = null
        return try {
            atual.stop()
            true
        } catch (_: RuntimeException) {
            // stop() logo depois de start(): o trecho não tem dados.
            false
        } finally {
            atual.release()
        }
    }

    override fun nivel(): Float = ((gravador?.maxAmplitude ?: 0) / AMPLITUDE_MAXIMA).coerceIn(0f, 1f)

    private companion object {
        const val TAXA_AMOSTRAGEM = 44_100
        const val TAXA_BITS = 64_000
        const val AMPLITUDE_MAXIMA = 32_767f
    }
}

/**
 * Junta trechos AAC/M4A (do mesmo gravador, mesmo formato) em [destino], em ordem,
 * sem recodificar: copia as amostras e desloca o tempo de cada trecho. `false` se falhar.
 */
fun juntarTrechos(trechos: List<File>, destino: File): Boolean {
    if (trechos.isEmpty()) return false
    if (trechos.size == 1) {
        trechos[0].copyTo(destino, overwrite = true)
        return true
    }
    var muxer: MediaMuxer? = null
    return try {
        val saida = MediaMuxer(destino.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
        var faixa = -1
        var deslocamentoUs = 0L
        val buffer = ByteBuffer.allocate(TAMANHO_BUFFER)
        val info = MediaCodec.BufferInfo()
        for (trecho in trechos) {
            val extrator = MediaExtractor()
            try {
                extrator.setDataSource(trecho.path)
                val indice = (0 until extrator.trackCount).firstOrNull {
                    extrator.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: continue
                val formato = extrator.getTrackFormat(indice)
                extrator.selectTrack(indice)
                if (faixa < 0) {
                    faixa = saida.addTrack(formato)
                    saida.start()
                }
                val quadroUs = AMOSTRAS_POR_QUADRO_AAC * 1_000_000L / formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var ultimoUs = -quadroUs
                while (true) {
                    val tamanho = extrator.readSampleData(buffer, 0)
                    if (tamanho < 0) break
                    val bandeiras = if (extrator.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC !=
                        0
                    ) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    info.set(0, tamanho, deslocamentoUs + extrator.sampleTime, bandeiras)
                    saida.writeSampleData(faixa, buffer, info)
                    ultimoUs = extrator.sampleTime
                    extrator.advance()
                }
                deslocamentoUs += ultimoUs + quadroUs
            } finally {
                extrator.release()
            }
        }
        if (faixa < 0) return false
        saida.stop()
        true
    } catch (e: Exception) {
        Timber.w("Não juntou os trechos: %s", e.javaClass.simpleName)
        destino.delete()
        false
    } finally {
        runCatching { muxer?.release() }
    }
}

private const val TAMANHO_BUFFER = 256 * 1024
private const val AMOSTRAS_POR_QUADRO_AAC = 1024

@Module
@InstallIn(SingletonComponent::class)
abstract class GravacaoModulo {
    @Binds
    abstract fun gravadorAudio(gravador: GravadorMediaRecorder): GravadorAudio
}
