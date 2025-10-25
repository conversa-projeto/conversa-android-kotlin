package com.conversa.conversa.data.api

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class DownloadHelper(private val context: Context) {

    /**
     * Faz download de um anexo e salva na pasta Downloads
     */
    suspend fun downloadAnexo(
        conteudoId: Int,
        nomeArquivo: String,
        extensao: String,
        authToken: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.api.downloadAnexo(
                token = "Bearer $authToken",
                conteudoId = conteudoId
            )

            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(
                    Exception("Erro ao baixar arquivo: ${response.code()}")
                )
            }

            val body = response.body()!!
            val fileName = "${nomeArquivo}.${extensao}"
            
            // Salva o arquivo na pasta Downloads
            val file = saveToDownloads(body, fileName)
            
            // Notifica o sistema sobre o novo arquivo
            notifyDownloadComplete(file, fileName)
            
            Result.success(file)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Salva o ResponseBody em um arquivo na pasta Downloads
     */
    private fun saveToDownloads(body: ResponseBody, fileName: String): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        
        // Cria a pasta Conversa dentro de Downloads
        val conversaDir = File(downloadsDir, "Conversa")
        if (!conversaDir.exists()) {
            conversaDir.mkdirs()
        }
        
        val file = File(conversaDir, fileName)
        
        // Salva o arquivo
        body.byteStream().use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        
        return file
    }

    /**
     * Notifica o sistema Android sobre o download concluído
     */
    private fun notifyDownloadComplete(file: File, fileName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        // Adiciona o arquivo ao banco de dados do sistema
        try {
            downloadManager.addCompletedDownload(
                fileName,
                "Download do Conversa",
                true,
                getMimeType(file.extension),
                file.absolutePath,
                file.length(),
                true
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Retorna o MIME type baseado na extensão do arquivo
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            // Imagens
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            
            // Documentos
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            
            // Áudio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            
            // Vídeo
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            
            // Compactados
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            
            else -> "application/octet-stream"
        }
    }
}
