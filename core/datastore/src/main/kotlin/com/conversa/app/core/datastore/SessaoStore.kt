package com.conversa.app.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.conversa.app.core.model.Sessao
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Formato gravado em disco (cifrado). Nunca contém a senha. */
@Serializable
internal data class SessaoGravada(
    val token: String,
    val usuarioId: Long,
    val nome: String,
    val email: String? = null,
    val telefone: String? = null,
    val avatarIdentificador: String? = null,
    val dispositivoId: Long? = null,
)

/** Conteúdo do arquivo: a sessão ou nada (sem login). */
@Serializable
internal data class ArquivoSessao(val sessao: SessaoGravada? = null)

/** Serializa a sessão em JSON e cifra antes de gravar. Arquivo corrompido ou de outro aparelho = sem sessão. */
internal class SessaoSerializer(private val cifrador: Cifrador) : Serializer<ArquivoSessao> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = ArquivoSessao()

    override suspend fun readFrom(input: InputStream): ArquivoSessao {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(ArquivoSessao.serializer(), cifrador.decifrar(bytes).decodeToString())
        } catch (e: Exception) {
            throw CorruptionException("Sessão ilegível", e)
        }
    }

    override suspend fun writeTo(t: ArquivoSessao, output: OutputStream) {
        output.write(cifrador.cifrar(json.encodeToString(ArquivoSessao.serializer(), t).encodeToByteArray()))
    }
}

/** Guarda a sessão (token JWT e dados do usuário) cifrada. */
class SessaoStore internal constructor(private val dataStore: DataStore<ArquivoSessao>) {
    val sessao: Flow<Sessao?> = dataStore.data.map { it.sessao?.paraModelo() }

    suspend fun salvar(sessao: Sessao) {
        dataStore.updateData { ArquivoSessao(sessao.paraGravada()) }
    }

    suspend fun limpar() {
        dataStore.updateData { ArquivoSessao() }
    }
}

private fun SessaoGravada.paraModelo() = Sessao(token, usuarioId, nome, email, telefone, avatarIdentificador, dispositivoId)

private fun Sessao.paraGravada() = SessaoGravada(token, usuarioId, nome, email, telefone, avatarIdentificador, dispositivoId)
