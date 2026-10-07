package com.conversa.app.core.data.autenticacao

import android.os.Build
import com.conversa.app.core.data.MotivoFimSessao
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.core.datastore.PreferenciasStore
import com.conversa.app.core.model.Sessao
import com.conversa.app.core.network.api.ConversaApi
import com.conversa.app.core.network.dto.AlterarDispositivoRequisicao
import com.conversa.app.core.network.dto.CadastroRequisicao
import com.conversa.app.core.network.dto.LoginRequisicao
import com.conversa.app.core.network.http.ErroApi
import com.conversa.app.core.network.http.chamarApi
import com.conversa.app.core.network.json.nuloExplicito
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/** O login respondeu 200 sem token ("Resposta de login inválida", AUT-01). */
class RespostaLoginInvalidaException : Exception("Resposta de login inválida: token ausente")

/** O servidor recusou o cadastro por e-mail repetido (hoje vem como 500 do Postgres, contrato §2.5). */
class EmailJaCadastradoException : Exception("E-mail já cadastrado")

/** Dados do aparelho para o `PATCH /dispositivo` (AUT-04). */
interface InfoDispositivo {
    val nome: String
    val modelo: String
    val versaoSo: String
}

/** Sem o nome que a pessoa deu ao aparelho (pode ter o nome dela): só fabricante e modelo. */
class InfoDispositivoAndroid @Inject constructor() : InfoDispositivo {
    override val nome: String
        get() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
    override val modelo: String get() = Build.MODEL.orEmpty()
    override val versaoSo: String get() = "Android ${Build.VERSION.RELEASE}"
}

/**
 * Login, cadastro, registro do dispositivo e saída (AUT-01, 02, 04, 05).
 * A senha só passa por aqui a caminho do servidor; nunca é guardada nem registrada em log.
 */
@Singleton
class AutenticacaoRepositorio @Inject constructor(
    private val api: ConversaApi,
    private val sessao: SessaoRepositorio,
    private val preferencias: PreferenciasStore,
    private val dispositivo: InfoDispositivo,
) {
    /** Último usuário digitado (para preencher o campo do login). */
    val ultimoLogin: Flow<String?> = preferencias.ultimoLogin

    /**
     * `POST /login {login, senha, dispositivo_id?}`. Reenvia o id do dispositivo salvo
     * para o servidor reaproveitar o mesmo registro (contrato §2.1).
     */
    suspend fun entrar(login: String, senha: String): Result<Sessao> {
        val usuario = login.trim()
        val dispositivoSalvo = preferencias.dispositivoId.first()
        val resposta = chamarApi { api.login(LoginRequisicao(usuario, senha, dispositivoSalvo)) }
            .getOrElse { return Result.failure(it) }
        if (resposta.token.isBlank()) return Result.failure(RespostaLoginInvalidaException())

        val dispositivoId = resposta.dispositivo?.id ?: dispositivoSalvo
        dispositivoId?.let { preferencias.salvarDispositivoId(it) }
        preferencias.salvarUltimoLogin(usuario)
        val nova = Sessao(
            token = resposta.token,
            usuarioId = resposta.id,
            nome = resposta.nome,
            email = resposta.email,
            telefone = resposta.telefone,
            avatarIdentificador = resposta.avatarIdentificador,
            dispositivoId = dispositivoId,
        )
        sessao.salvar(nova)
        return Result.success(nova)
    }

    /** `PUT /usuario {nome, login, email, senha}` (rota pública). Não faz login. */
    suspend fun cadastrar(nome: String, login: String, email: String, senha: String): Result<Unit> =
        chamarApi { api.cadastrar(CadastroRequisicao(nome.trim(), login.trim(), email.trim(), senha)) }
            .map { }
            .recoverCatching { erro ->
                throw if (erro is ErroApi.Servidor && erro.ehEmailDuplicado()) EmailJaCadastradoException() else erro
            }

    /**
     * `PATCH /dispositivo` com nome, modelo e versão (AUT-04). Os textos são cortados
     * nos limites das colunas (50/50/15), senão o servidor responde 500 (contrato §7.3).
     */
    suspend fun registrarDispositivo(): Result<Unit> {
        val id = sessao.sessao.value?.dispositivoId ?: return Result.success(Unit)
        return chamarApi {
            api.alterarDispositivo(
                AlterarDispositivoRequisicao(
                    id = id,
                    nome = dispositivo.nome.take(LIMITE_NOME),
                    modelo = dispositivo.modelo.take(LIMITE_MODELO),
                    versaoSo = dispositivo.versaoSo.take(LIMITE_VERSAO),
                    plataforma = PLATAFORMA,
                ),
            )
        }.map { }
    }

    /**
     * Sair (AUT-05). Não há rota de logout: para o servidor parar os pushes, limpa o
     * `token_fcm` do dispositivo (melhor esforço, com tempo curto). Depois encerra a
     * sessão; quem escuta o fim da sessão fecha o socket e limpa o cache.
     */
    suspend fun sair() {
        val id = sessao.sessao.value?.dispositivoId
        if (id != null) {
            val resultado = withTimeoutOrNull(TEMPO_LIMPAR_TOKEN_MS) {
                chamarApi { api.alterarDispositivo(AlterarDispositivoRequisicao(id = id, tokenFcm = nuloExplicito)) }
            }
            if (resultado?.isSuccess != true) Timber.i("Não foi possível limpar o token de push ao sair")
        }
        sessao.encerrar(MotivoFimSessao.SAIU)
    }

    private fun ErroApi.Servidor.ehEmailDuplicado(): Boolean = status >= 500 && detalhe.contains("email", ignoreCase = true)

    companion object {
        const val PLATAFORMA = "android"
        const val LIMITE_NOME = 50
        const val LIMITE_MODELO = 50
        const val LIMITE_VERSAO = 15
        private const val TEMPO_LIMPAR_TOKEN_MS = 3_000L
    }
}
