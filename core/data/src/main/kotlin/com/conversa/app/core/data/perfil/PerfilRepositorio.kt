package com.conversa.app.core.data.perfil

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.data.anexos.AnexosRepositorio
import com.conversa.app.core.data.anexos.FonteArquivo
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarSenhaRequisicao
import com.conversa.app.core.network.dto.AlterarUsuarioRequisicao
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.nuloExplicito
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonPrimitive

/** A foto de perfil já recortada (JPEG em memória), como arquivo para o envio de anexos. */
private class FotoDePerfil(private val jpeg: ByteArray) : FonteArquivo {
    override val nome = "avatar.jpg"
    override val tamanho = jpeg.size.toLong()
    override val mime = "image/jpeg"

    override fun abrir() = ByteArrayInputStream(jpeg)
}

/**
 * Perfil do usuário logado (8.3, FC-802, AUT-06/07/08), como o `ProfileSettingsModal.vue`:
 * nome e e-mail (`PATCH /usuario`), senha (`POST /alterar-senha`) e a foto (anexo + `PATCH
 * /usuario {avatar_anexo_id}`). A sessão guardada acompanha o que mudou. A senha nunca é
 * guardada nem registrada: só vai no corpo do pedido.
 */
@Singleton
class PerfilRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val sessoes: SessaoRepositorio,
    private val anexos: AnexosRepositorio,
) {
    val sessao: StateFlow<Sessao?> = sessoes.sessao

    private fun atual(): Sessao = checkNotNull(sessoes.sessao.value) { "Sem sessão" }

    suspend fun alterarDados(nome: String, email: String): Result<Unit> {
        val sessao = atual()
        return chamarApi { api.alterarUsuario(AlterarUsuarioRequisicao(sessao.usuarioId, nome = nome.trim(), email = email.trim())) }
            .map { usuario -> sessoes.salvar(sessao.copy(nome = usuario.nome, email = usuario.email)) }
    }

    suspend fun alterarSenha(atual: String, nova: String): Result<Unit> =
        chamarApi { api.alterarSenha(AlterarSenhaRequisicao(senhaAtual = atual, senha = nova)) }.map {}

    /** Sobe a foto ([jpeg], já 256×256) e a põe no perfil. */
    suspend fun trocarFoto(jpeg: ByteArray): Result<Unit> {
        val sessao = atual()
        val anexo = anexos.enviar(FotoDePerfil(jpeg), TipoConteudo.IMAGEM).getOrElse { return Result.failure(it) }
        return chamarApi { api.alterarUsuario(AlterarUsuarioRequisicao(sessao.usuarioId, avatarAnexoId = JsonPrimitive(anexo.id))) }
            .map { sessoes.salvar(atual().copy(avatarIdentificador = anexo.identificador)) }
    }

    suspend fun removerFoto(): Result<Unit> {
        val sessao = atual()
        return chamarApi { api.alterarUsuario(AlterarUsuarioRequisicao(sessao.usuarioId, avatarAnexoId = nuloExplicito)) }
            .map { sessoes.salvar(atual().copy(avatarIdentificador = null)) }
    }

    /** URL assinada da foto (do cache do repositório de anexos enquanto vale). */
    suspend fun urlDaFoto(identificador: String): Result<String> = anexos.url(identificador)

    /** A imagem falhou (URL vencida): a próxima [urlDaFoto] busca outra. */
    fun esquecerUrl(identificador: String) = anexos.esquecerUrl(identificador)
}
