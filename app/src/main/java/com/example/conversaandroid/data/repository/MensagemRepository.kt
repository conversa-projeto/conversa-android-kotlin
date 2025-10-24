package com.example.conversaandroid.data.repository

import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.api.ConversaApiService
import com.seudominio.conversa.data.api.request.ConteudoRequest
import com.seudominio.conversa.data.api.request.EnviarMensagemRequest
import com.seudominio.conversa.data.local.dao.MensagemDao
import com.seudominio.conversa.data.local.entities.ConteudoEntity
import com.seudominio.conversa.data.local.entities.MensagemEntity
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.data.websocket.SocketMessage
import com.seudominio.conversa.data.websocket.WebSocketClient
import com.seudominio.conversa.domain.model.Conteudo
import com.seudominio.conversa.domain.model.Mensagem
import com.seudominio.conversa.domain.model.TipoConteudo
import com.seudominio.conversa.utils.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MensagemRepository @Inject constructor(
    private val apiService: ConversaApiService,
    private val mensagemDao: MensagemDao,
    private val webSocketClient: WebSocketClient,
    private val preferencesManager: PreferencesManager
) {

    fun getMensagensLocal(conversaId: Int): Flow<List<Mensagem>> {
        return mensagemDao.getMensagensFlowByConversa(conversaId).map { list ->
            list.map { mensagemWithConteudos ->
                mensagemWithConteudos.mensagem.toMensagem(mensagemWithConteudos.conteudos)
            }
        }
    }

    suspend fun syncMensagens(conversaId: Int): ApiResponse<List<Mensagem>> {
        val response = NetworkUtils.safeApiCall {
            apiService.getMensagens(
                conversaId = conversaId,
                mensagensPrevias = 50
            )
        }

        return when (response) {
            is ApiResponse.Success -> {
                val mensagens = response.data.map { mensagemResponse ->
                    Mensagem(
                        id = mensagemResponse.id,
                        conversaId = mensagemResponse.conversaId,
                        remetenteId = mensagemResponse.remetenteId,
                        remetenteNome = mensagemResponse.remetente ?: "",
                        inserida = mensagemResponse.inserida,
                        alterada = mensagemResponse.alterada,
                        recebida = mensagemResponse.recebida,
                        visualizada = mensagemResponse.visualizada,
                        reproduzida = mensagemResponse.reproduzida,
                        conteudos = mensagemResponse.conteudos.map { conteudo ->
                            Conteudo(
                                id = conteudo.id,
                                tipo = TipoConteudo.fromValue(conteudo.tipo),
                                ordem = conteudo.ordem,
                                conteudo = conteudo.conteudo ?: "",
                                nome = conteudo.nome ?: "",
                                extensao = conteudo.extensao ?: "",
                                identificador = conteudo.identificador
                            )
                        }
                    )
                }

                // Salvar no banco local
                mensagens.forEach { mensagem ->
                    val mensagemId = mensagemDao.insertMensagem(
                        MensagemEntity.fromMensagem(mensagem)
                    )

                    val conteudos = mensagem.conteudos.map { conteudo ->
                        ConteudoEntity.fromConteudo(conteudo, mensagem.id)
                    }
                    mensagemDao.insertConteudos(conteudos)
                }

                ApiResponse.Success(mensagens)
            }
            is ApiResponse.Error -> {
                ApiResponse.Error(response.message, response.code)
            }
            is ApiResponse.Loading -> {
                ApiResponse.Loading()
            }
        }
    }

    suspend fun enviarMensagem(
        conversaId: Int,
        texto: String? = null,
        arquivo: File? = null,
        tipoArquivo: TipoConteudo = TipoConteudo.ARQUIVO
    ): ApiResponse<Mensagem> {

        val conteudos = mutableListOf<ConteudoRequest>()

        // Adicionar texto se existir
        texto?.let {
            conteudos.add(
                ConteudoRequest(
                    ordem = 0,
                    tipo = TipoConteudo.TEXTO.value,
                    conteudo = it
                )
            )
        }

        // Upload de arquivo se existir
        arquivo?.let { file ->
            val uploadResponse = uploadArquivo(file, tipoArquivo)
            if (uploadResponse is ApiResponse.Success) {
                conteudos.add(
                    ConteudoRequest(
                        ordem = conteudos.size,
                        tipo = tipoArquivo.value,
                        identificador = uploadResponse.data,
                        nome = file.name,
                        extensao = file.extension
                    )
                )
            } else if (uploadResponse is ApiResponse.Error) {
                return ApiResponse.Error(uploadResponse.message)
            }
        }

        if (conteudos.isEmpty()) {
            return ApiResponse.Error("Mensagem vazia")
        }

        val request = EnviarMensagemRequest(
            conversaId = conversaId,
            conteudos = conteudos
        )

        val response = NetworkUtils.safeApiCall {
            apiService.enviarMensagem(request)
        }

        return when (response) {
            is ApiResponse.Success -> {
                val mensagemResponse = response.data
                val userId = preferencesManager.getUser()?.id ?: 0

                val mensagem = Mensagem(
                    id = mensagemResponse.id,
                    conversaId = mensagemResponse.conversaId,
                    remetenteId = mensagemResponse.remetenteId,
                    remetenteNome = mensagemResponse.remetente ?: "",
                    inserida = mensagemResponse.inserida,
                    alterada = mensagemResponse.alterada,
                    recebida = true,
                    visualizada = false,
                    conteudos = mensagemResponse.conteudos.map { conteudo ->
                        Conteudo(
                            id = conteudo.id,
                            tipo = TipoConteudo.fromValue(conteudo.tipo),
                            ordem = conteudo.ordem,
                            conteudo = conteudo.conteudo ?: "",
                            nome = conteudo.nome ?: "",
                            extensao = conteudo.extensao ?: "",
                            identificador = conteudo.identificador
                        )
                    }
                )

                // Salvar no banco local
                mensagemDao.insertMensagem(MensagemEntity.fromMensagem(mensagem))
                val conteudosEntity = mensagem.conteudos.map { conteudo ->
                    ConteudoEntity.fromConteudo(conteudo, mensagem.id)
                }
                mensagemDao.insertConteudos(conteudosEntity)

                // Enviar via WebSocket
                webSocketClient.sendMessage(
                    SocketMessage.NovaMensagem(conversaId, mensagem.id)
                )

                ApiResponse.Success(mensagem)
            }
            is ApiResponse.Error -> {
                ApiResponse.Error(response.message, response.code)
            }
            is ApiResponse.Loading -> {
                ApiResponse.Loading()
            }
        }
    }

    private suspend fun uploadArquivo(file: File, tipo: TipoConteudo): ApiResponse<String> {
        val requestFile = file.asRequestBody("application/octet-stream".toMediaType())
        val body = MultipartBody.Part.createFormData("arquivo", file.name, requestFile)

        val response = NetworkUtils.safeApiCall {
            apiService.uploadAnexo(
                tipo = tipo.value,
                nome = file.name,
                extensao = file.extension,
                file = body
            )
        }

        return when (response) {
            is ApiResponse.Success -> {
                ApiResponse.Success(response.data.identificador)
            }
            is ApiResponse.Error -> {
                ApiResponse.Error(response.message, response.code)
            }
            is ApiResponse.Loading -> {
                ApiResponse.Loading()
            }
        }
    }

    suspend fun marcarMensagemComoVisualizada(conversaId: Int, mensagemId: Int) {
        NetworkUtils.safeApiCall {
            apiService.visualizarMensagem(conversaId, mensagemId)
        }
        mensagemDao.marcarComoVisualizada(mensagemId)
    }

    suspend fun marcarTodasComoVisualizadas(conversaId: Int) {
        val userId = preferencesManager.getUser()?.id ?: return
        mensagemDao.marcarMensagensComoVisualizadas(conversaId, userId)
    }
}