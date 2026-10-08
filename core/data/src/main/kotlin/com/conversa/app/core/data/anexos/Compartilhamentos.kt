package com.conversa.app.core.data.anexos

import android.content.Context
import androidx.core.net.toUri
import com.conversa.app.core.network.di.DespachanteEs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber

/** O que outro app compartilhou, já copiado para o cache: pronto para a fila do campo. */
data class ItensCompartilhados(val texto: String, val anexos: List<AnexoLocal>, val ignorados: Int = 0)

/**
 * Compartilhamento recebido de outro app (TODO 4.9, AND-10).
 * - [receber] copia os arquivos para `cache/compartilhados/<n>/`: a permissão de leitura
 *   que vem no "Compartilhar" é temporária, e o envio pode acontecer depois (WorkManager);
 * - só aceita `content://` de outro app: `file://` ou um URI do próprio app poderiam
 *   fazer o app mandar os próprios arquivos privados (a sessão, por exemplo) para a conversa;
 * - acima de 1 GiB não entra (conta em [ItensCompartilhados.ignorados]);
 * - fica guardado até a pessoa escolher a conversa ([retirar]).
 */
@Singleton
class Compartilhamentos @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val fontes: FontesArquivo,
    private val arquivos: ArquivosLocais,
    @DespachanteEs private val es: CoroutineDispatcher,
) {
    private val _pendente = MutableStateFlow<ItensCompartilhados?>(null)

    /** Pasta dos arquivos do compartilhamento pendente (depois de retirados, são da fila do chat). */
    private var pastaPendente: File? = null

    /** Compartilhamento esperando a pessoa escolher a conversa ("Enviar para…"). */
    val pendente: StateFlow<ItensCompartilhados?> = _pendente.asStateFlow()

    /** `false` se não sobrou nada para enviar (sem texto e sem arquivo aceito). */
    suspend fun receber(texto: String?, uris: List<String>): Boolean = withContext(es) {
        descartarPendente()
        val pasta = File(contexto.cacheDir, "compartilhados/${System.nanoTime()}").apply { mkdirs() }
        var ignorados = 0
        val anexos = uris.distinct().mapNotNull { uri ->
            copiar(uri, pasta).also { if (it == null) ignorados++ }
        }
        val limpo = texto?.trim().orEmpty()
        if (anexos.isEmpty()) pasta.deleteRecursively()
        if (limpo.isEmpty() && anexos.isEmpty()) {
            _pendente.value = null
            return@withContext false
        }
        _pendente.value = ItensCompartilhados(limpo, anexos, ignorados)
        pastaPendente = pasta.takeIf { anexos.isNotEmpty() }
        true
    }

    /** Imagem colada no campo (teclado ou área de transferência): mesma cópia e mesmas regras. */
    suspend fun copiarColados(uris: List<String>): List<AnexoLocal> = withContext(es) {
        val pasta = File(contexto.cacheDir, "compartilhados/${System.nanoTime()}").apply { mkdirs() }
        uris.distinct().mapNotNull { copiar(it, pasta) }.also { if (it.isEmpty()) pasta.deleteRecursively() }
    }

    /** A conversa foi escolhida: entrega os itens (uma vez só). */
    fun retirar(): ItensCompartilhados? = _pendente.value.also {
        _pendente.value = null
        // Os arquivos agora são da fila do chat: saem depois de enviados ou removidos (FontesArquivo.liberar).
        pastaPendente = null
    }

    /** Desistiu de compartilhar. */
    fun descartar() = descartarPendente()

    private fun copiar(uri: String, pasta: File): AnexoLocal? {
        if (!aceito(uri)) {
            Timber.w("Compartilhamento recusado: só content:// de outro app")
            return null
        }
        val origem = fontes.descrever(uri) ?: return null
        if (origem.tamanho > AnexosRepositorio.LIMITE_BYTES) return null
        val destino = File(pasta, nomeSeguro(origem.nome)).let { if (it.exists()) File(pasta, "${System.nanoTime()}-${it.name}") else it }
        return try {
            contexto.contentResolver.openInputStream(uri.toUri())?.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
                ?: return null
            origem.copy(uri = arquivos.uriCompartilhado(destino), tamanho = destino.length())
        } catch (e: Exception) {
            Timber.w("Não copiou o compartilhado: %s", e.javaClass.simpleName)
            destino.delete()
            null
        }
    }

    private fun aceito(uri: String): Boolean {
        val alvo = uri.toUri()
        return alvo.scheme == "content" && alvo.authority?.startsWith(contexto.packageName) == false
    }

    private fun descartarPendente() {
        _pendente.value = null
        pastaPendente?.deleteRecursively()
        pastaPendente = null
    }
}
