package com.conversa.conversa.data.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Helper para fazer upload de anexos (imagens e arquivos)
 */
class UploadHelper(private val context: Context) {

    /**
     * Faz upload de uma imagem selecionada da galeria
     * @param uri URI da imagem selecionada
     * @param authToken Token de autenticação
     * @return Identificador (hash SHA256) do arquivo enviado
     */
    suspend fun uploadImagem(uri: Uri, authToken: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Lê o arquivo da URI
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(Exception("Não foi possível abrir o arquivo"))

                // 2. Copia para arquivo temporário
                val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()

                // 3. Calcula SHA256 do arquivo
                val sha256 = calcularSHA256(tempFile)

                // 4. Verifica se arquivo já existe no servidor
                val existeResponse = RetrofitClient.api.verificarAnexoExiste(
                    token = "Bearer $authToken",
                    identificador = sha256
                )

                if (existeResponse.isSuccessful) {
                    val existeResult = existeResponse.body()
                    if (existeResult?.existe == true) {
                        // Arquivo já existe, retorna o identificador
                        tempFile.delete()
                        return@withContext Result.success(sha256)
                    }
                }

                // 5. Faz upload do arquivo
                val nomeArquivo = obterNomeArquivo(uri) ?: "imagem"
                val extensao = obterExtensao(uri) ?: "jpg"

                val requestBody = tempFile.readBytes().toRequestBody(
                    "application/octet-stream".toMediaTypeOrNull()
                )

                val uploadResponse = RetrofitClient.api.uploadAnexo(
                    token = "Bearer $authToken",
                    tipo = 2, // 2 = Imagem
                    nome = nomeArquivo,
                    extensao = extensao,
                    arquivo = requestBody
                )

                tempFile.delete()

                if (uploadResponse.isSuccessful) {
                    val resultado = uploadResponse.body()
                    if (resultado != null) {
                        Result.success(resultado.identificador)
                    } else {
                        Result.failure(Exception("Resposta vazia do servidor"))
                    }
                } else {
                    Result.failure(Exception("Erro ao fazer upload: ${uploadResponse.code()}"))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Faz upload de um arquivo de áudio
     * @param audioFile Arquivo de áudio gravado
     * @param authToken Token de autenticação
     * @return Identificador (hash SHA256) do arquivo enviado
     */
    suspend fun uploadAudio(audioFile: File, authToken: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Calcula SHA256 do arquivo
                val sha256 = calcularSHA256(audioFile)

                // 2. Verifica se arquivo já existe no servidor
                val existeResponse = RetrofitClient.api.verificarAnexoExiste(
                    token = "Bearer $authToken",
                    identificador = sha256
                )

                if (existeResponse.isSuccessful) {
                    val existeResult = existeResponse.body()
                    if (existeResult?.existe == true) {
                        // Arquivo já existe, retorna o identificador
                        audioFile.delete()
                        return@withContext Result.success(sha256)
                    }
                }

                // 3. Faz upload do arquivo
                val nomeArquivo = "audio_${System.currentTimeMillis()}"
                val extensao = audioFile.extension

                val requestBody = audioFile.readBytes().toRequestBody(
                    "application/octet-stream".toMediaTypeOrNull()
                )

                val uploadResponse = RetrofitClient.api.uploadAnexo(
                    token = "Bearer $authToken",
                    tipo = 4, // 4 = Áudio
                    nome = nomeArquivo,
                    extensao = extensao,
                    arquivo = requestBody
                )

                // Deleta arquivo temporário após upload
                audioFile.delete()

                if (uploadResponse.isSuccessful) {
                    val resultado = uploadResponse.body()
                    if (resultado != null) {
                        Result.success(resultado.identificador)
                    } else {
                        Result.failure(Exception("Resposta vazia do servidor"))
                    }
                } else {
                    Result.failure(Exception("Erro ao fazer upload: ${uploadResponse.code()}"))
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Result.failure(e)
            }
        }
    }

    /**
     * Calcula o hash SHA256 de um arquivo
     */
    private fun calcularSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = file.readBytes()
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Obtém o nome do arquivo a partir da URI
     */
    private fun obterNomeArquivo(uri: Uri): String? {
        var nome: String? = null
        
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                nome = cursor.getString(nameIndex)
                // Remove extensão se existir
                nome = nome?.substringBeforeLast(".")
            }
        }
        
        return nome
    }

    /**
     * Obtém a extensão do arquivo a partir da URI
     */
    private fun obterExtensao(uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        
        return when {
            mimeType?.startsWith("image/jpeg") == true -> "jpg"
            mimeType?.startsWith("image/png") == true -> "png"
            mimeType?.startsWith("image/gif") == true -> "gif"
            mimeType?.startsWith("image/webp") == true -> "webp"
            else -> {
                // Tenta extrair da URI
                uri.lastPathSegment?.substringAfterLast(".", "jpg")
            }
        }
    }

    /**
     * Formata o tamanho do arquivo para exibição
     */
    fun formatarTamanhoArquivo(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}
