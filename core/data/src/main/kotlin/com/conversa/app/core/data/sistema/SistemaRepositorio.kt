package com.conversa.app.core.data.sistema

import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.model.Acessos
import com.conversa.app.core.model.AlteracaoParametros
import com.conversa.app.core.model.ParametrosSistema
import com.conversa.app.core.model.PermissaoSistema
import com.conversa.app.core.model.UsuarioAcessos
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarParametrosRequisicao
import com.conversa.app.core.network.dto.ParametrosDto
import com.conversa.app.core.network.dto.PermissaoUsuarioDto
import com.conversa.app.core.network.dto.PermissoesDto
import com.conversa.app.core.network.http.chamarApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Administração do servidor (8.5, FC-808): as minhas permissões (AUT-09), os parâmetros
 * (CFG-07) e quem pode o quê (CFG-08). O servidor confere a permissão em cada rota (403);
 * aqui elas só decidem o que aparece.
 */
@Singleton
class SistemaRepositorio @Inject constructor(private val api: ConversaApi, private val sessoes: SessaoRepositorio) {
    /** Os códigos de quem está logado; preso ao usuário, para outra conta não herdar as telas. */
    private val minhas = MutableStateFlow<Pair<Long, Set<String>>?>(null)

    val minhasPermissoes: Flow<Set<String>> = combine(sessoes.sessao, minhas) { sessao, carregadas ->
        carregadas?.takeIf { sessao != null && it.first == sessao.usuarioId }?.second.orEmpty()
    }

    /** `GET /usuario/permissoes` (no modo aberto, todas). */
    suspend fun carregarMinhasPermissoes(): Result<Set<String>> {
        val usuario = sessoes.sessao.value?.usuarioId ?: return Result.success(emptySet())
        return chamarApi { api.minhasPermissoes() }.map { it.toSet() }.onSuccess { minhas.value = usuario to it }
    }

    suspend fun parametros(): Result<ParametrosSistema> = chamarApi { api.parametros() }.map { it.paraModelo() }

    suspend fun alterarParametros(alteracao: AlteracaoParametros): Result<ParametrosSistema> =
        chamarApi { api.alterarParametros(alteracao.paraRequisicao()) }.map { it.paraModelo() }

    suspend fun acessos(): Result<Acessos> = chamarApi { api.permissoes() }.map { it.paraModelo() }

    /** Depois de mexer: as minhas podem ter mudado (as próprias, ou o fim do modo aberto). */
    suspend fun conceder(usuarioId: Long, codigo: String): Result<Unit> =
        chamarApi { api.concederPermissao(PermissaoUsuarioDto(usuarioId, codigo)) }.map { carregarMinhasPermissoes() }

    suspend fun retirar(usuarioId: Long, codigo: String): Result<Unit> =
        chamarApi { api.retirarPermissao(usuarioId, codigo) }.map { carregarMinhasPermissoes() }
}

internal fun ParametrosDto.paraModelo() = ParametrosSistema(
    fcmProjetoId = fcmProjectId.orEmpty(),
    fcmEmail = fcmClientEmail.orEmpty(),
    fcmChaveConfigurada = fcmPrivateKeyConfigurada,
    turnForcarRelay = turnForcarRelay,
    transcritorUrl = transcritorUrl.orEmpty(),
    transcritorIdioma = transcritorIdioma.orEmpty(),
    gravacaoDias = gravacaoDias,
    s3Bucket = s3Bucket.orEmpty(),
)

internal fun AlteracaoParametros.paraRequisicao() = AlterarParametrosRequisicao(
    fcmProjectId = fcmProjetoId,
    fcmClientEmail = fcmEmail,
    fcmPrivateKey = fcmChave,
    turnForcarRelay = turnForcarRelay,
    transcritorUrl = transcritorUrl,
    transcritorIdioma = transcritorIdioma,
    gravacaoDias = gravacaoDias,
)

internal fun PermissoesDto.paraModelo() = Acessos(
    permissoes = permissoes.map { PermissaoSistema(it.codigo, it.descricao) },
    usuarios = usuarios.map { UsuarioAcessos(it.id, it.nome, it.login, it.permissoes.toSet()) },
    modoAberto = modoAberto,
)
