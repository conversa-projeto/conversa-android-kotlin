package com.example.conversaandroid.data.repository

import com.seudominio.conversa.data.api.ApiResponse
import com.seudominio.conversa.data.api.ConversaApiService
import com.seudominio.conversa.data.api.request.CriarConversaRequest
import com.seudominio.conversa.data.local.dao.ConversaDao
import com.seudominio.conversa.data.local.dao.UsuarioDao
import com.seudominio.conversa.data.local.entities.ConversaEntity
import com.seudominio.conversa.data.local.entities.ConversaUsuarioEntity
import com.seudominio.conversa.data.local.entities.UsuarioEntity
import com.seudominio.conversa.domain.model.Conversa
import com.seudominio.conversa.domain.model.TipoConversa
import com.seudominio.conversa.domain.model.Usuario
import com.seudominio.conversa.utils.NetworkUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversaRepository @Inject constructor(
    private val apiService: ConversaApiService,
    private val conversaDao: ConversaDao,
    private val usuarioDao: UsuarioDao
) {

    fun getConversasLocal(): Flow<List<Conversa>> {
        return conversaDao.getAllConversasWithUsuarios().map { list ->
            list.map { conversaWithUsuarios ->
                val conversa = conversaWithUsuarios.conversa.toConversa()
                conversa.usuarios.addAll(
                    conversaWithUsuarios.usuarios.map { it.toUsuario() }
                )
                conversa
            }
        }
    }

    suspend fun syncConversas(): ApiResponse<List<Conversa>> {
        val response = NetworkUtils.safeApiCall {
            apiService.getConversas()
        }

        return when (response) {
            is ApiResponse.Success -> {
                val conversas = response.data.map { conversaResponse ->
                    Conversa(
                        id = conversaResponse.id,
                        tipo = TipoConversa.fromValue(conversaResponse.tipo),
                        descricao = conversaResponse.descricao ?: "",
                        ultimaMensagem = conversaResponse.ultimaMensagemTexto ?: "",
                        ultimaMensagemData = conversaResponse.ultimaMensagem ?: 0L,
                        ultimaMensagemId = conversaResponse.mensagemId ?: 0,
                        criadoEm = conversaResponse.inserida,
                        mensagensSemVisualizar = conversaResponse.mensagensSemVisualizar,
                        destinatarioId = conversaResponse.destinatarioId,
                        destinatarioNome = conversaResponse.nome
                    )
                }

                // Salvar no banco local
                conversaDao.insertConversas(
                    conversas.map { ConversaEntity.fromConversa(it) }
                )

                // Salvar usuários das conversas
                conversas.forEach { conversa ->
                    conversa.destinatarioId?.let { destinatarioId ->
                        val usuario = Usuario(
                            id = destinatarioId,
                            nome = conversa.destinatarioNome ?: ""
                        )
                        usuarioDao.insertUsuario(UsuarioEntity.fromUsuario(usuario))

                        conversaDao.insertConversaUsuario(
                            ConversaUsuarioEntity(
                                conversaId = conversa.id,
                                usuarioId = destinatarioId
                            )
                        )
                    }
                }

                ApiResponse.Success(conversas)
            }
            is ApiResponse.Error -> {
                ApiResponse.Error(response.message, response.code)
            }
            is ApiResponse.Loading -> {
                ApiResponse.Loading()
            }
        }
    }

    suspend fun criarConversa(descricao: String, tipo: TipoConversa): ApiResponse<Conversa> {
        val request = CriarConversaRequest(
            descricao = descricao,
            tipo = tipo.value
        )

        val response = NetworkUtils.safeApiCall {
            apiService.criarConversa(request)
        }

        return when (response) {
            is ApiResponse.Success -> {
                val conversaResponse = response.data
                val conversa = Conversa(
                    id = conversaResponse.id,
                    tipo = TipoConversa.fromValue(conversaResponse.tipo),
                    descricao = conversaResponse.descricao ?: "",
                    criadoEm = conversaResponse.inserida
                )

                // Salvar no banco local
                conversaDao.insertConversa(ConversaEntity.fromConversa(conversa))

                ApiResponse.Success(conversa)
            }
            is ApiResponse.Error -> {
                ApiResponse.Error(response.message, response.code)
            }
            is ApiResponse.Loading -> {
                ApiResponse.Loading()
            }
        }
    }

    suspend fun getConversaById(id: Int): Conversa? {
        val conversaWithUsuarios = conversaDao.getConversaWithUsuarios(id)
        return conversaWithUsuarios?.let {
            val conversa = it.conversa.toConversa()
            conversa.usuarios.addAll(
                it.usuarios.map { usuario -> usuario.toUsuario() }
            )
            conversa
        }
    }
}