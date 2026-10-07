package com.conversa.conversa.data.repository

import com.conversa.conversa.data.api.ConversaApi
import com.conversa.conversa.data.model.AlterarSenhaRequest
import com.conversa.conversa.data.model.AtualizarUsuarioRequest
import com.conversa.conversa.data.model.CadastrarUsuarioRequest
import com.conversa.conversa.data.model.UsuarioPerfil
import com.conversa.conversa.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first

class UsuarioRepository(
    private val api: ConversaApi,
    private val userPreferences: UserPreferences,
) {

    suspend fun cadastrar(nome: String, login: String, email: String, senha: String, telefone: String? = null): Result<UsuarioPerfil> = runCatching {
        val resp = api.cadastrarUsuario(CadastrarUsuarioRequest(nome, login, email, senha, telefone))
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    suspend fun atualizar(nome: String? = null, email: String? = null, telefone: String? = null, avatarAnexoId: Int? = null): Result<UsuarioPerfil> = runCatching {
        val resp = api.atualizarUsuario("Bearer ${token()}", AtualizarUsuarioRequest(nome, email, telefone, avatarAnexoId))
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
        resp.body() ?: error("Body vazio")
    }

    suspend fun alterarSenha(senhaAtual: String, senhaNova: String): Result<Unit> = runCatching {
        val resp = api.alterarSenha("Bearer ${token()}", AlterarSenhaRequest(senhaAtual, senhaNova))
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    suspend fun deletarConta(userId: Int): Result<Unit> = runCatching {
        val resp = api.deletarUsuario("Bearer ${token()}", userId)
        if (!resp.isSuccessful) error("HTTP ${resp.code()}")
    }

    private suspend fun token() = userPreferences.authToken.first() ?: error("Sem token")
}
