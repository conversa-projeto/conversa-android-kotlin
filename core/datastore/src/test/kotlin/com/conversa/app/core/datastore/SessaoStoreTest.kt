package com.conversa.app.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import com.conversa.app.core.model.Sessao
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.experimental.xor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

/** Cifrador de mentira: inverte bits (dá para ver que o arquivo não fica em texto puro). */
private class CifradorFalso : Cifrador {
    override fun cifrar(dados: ByteArray) = ByteArray(dados.size) { dados[it] xor 0x5A }

    override fun decifrar(dados: ByteArray) = cifrar(dados)
}

/** DataStore em memória (o de arquivo não consegue renomear no Windows durante os testes; no Android não há esse problema). */
private class DataStoreEmMemoria<T>(inicial: T) : DataStore<T> {
    private val estado = MutableStateFlow(inicial)
    override val data: Flow<T> = estado

    override suspend fun updateData(transform: suspend (t: T) -> T): T = transform(estado.value).also { estado.value = it }
}

class SessaoStoreTest {
    private val sessao = Sessao(token = "jwt-secreto", usuarioId = 7, nome = "Ana", email = "ana@x.com", dispositivoId = 12)

    @Test
    fun `serializer grava cifrado e le de volta igual`() = runTest {
        val serializer = SessaoSerializer(CifradorFalso())
        val arquivo = ArquivoSessao(SessaoGravada("jwt-secreto", 7, "Ana", dispositivoId = 12))
        val saida = ByteArrayOutputStream()
        serializer.writeTo(arquivo, saida)

        val bytes = saida.toByteArray()
        assertThat(bytes.decodeToString()).doesNotContain("jwt-secreto")
        assertThat(serializer.readFrom(ByteArrayInputStream(bytes))).isEqualTo(arquivo)
        assertThat(serializer.readFrom(ByteArrayInputStream(ByteArray(0)))).isEqualTo(ArquivoSessao())
    }

    @Test
    fun `arquivo ilegivel gera CorruptionException`() {
        val serializer = SessaoSerializer(object : Cifrador {
            override fun cifrar(dados: ByteArray) = dados

            override fun decifrar(dados: ByteArray): ByteArray = error("chave perdida")
        })
        assertThrows(CorruptionException::class.java) {
            runBlocking { serializer.readFrom(ByteArrayInputStream(byteArrayOf(1, 2, 3))) }
        }
    }

    @Test
    fun `store salva, le de volta e limpa`() = runTest {
        val store = SessaoStore(DataStoreEmMemoria(ArquivoSessao()))
        assertThat(store.sessao.first()).isNull()
        store.salvar(sessao)
        assertThat(store.sessao.first()).isEqualTo(sessao)
        store.limpar()
        assertThat(store.sessao.first()).isNull()
    }
}
