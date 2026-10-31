package com.conversa.app.core.registro

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import org.junit.After
import org.junit.Test

/** O formato da linha, a leitura para a tela, o sigilo e o resumo dos erros. */
class LinhaDoRegistroTest {
    private val momento = ZonedDateTime.parse("2026-10-31T15:46:12.345-03:00")

    @After
    fun esquecer() = Sigilo.guardar("token", null)

    @Test
    fun `linha com momento, fuso, nivel, tag e thread curta`() {
        val linha = linhaDoRegistro(momento, 4, "Http", "OkHttp https://10.0.5.78/api/...", "GET /api/conversas → 200")
        assertThat(linha).isEqualTo("2026-10-31 15:46:12.345 -03:00 I/Http [OkHttp] GET /api/conversas → 200")
        assertThat(linhaDoRegistro(momento, 6, null, "main", "x")).isEqualTo("2026-10-31 15:46:12.345 -03:00 E/- [main] x")
    }

    @Test
    fun `agrupa a pilha do erro na entrada de cima`() {
        val linhas = listOf(
            "\tat com.exemplo.Sobra(Sobra.kt:1)",
            linhaDoRegistro(momento, 5, "Envio", "w", "falhou"),
            "java.io.IOException: x",
            "\tat com.exemplo.A(A.kt:10)",
            linhaDoRegistro(momento, 3, "Sync", "w", "ok"),
        )

        val entradas = agruparEntradas(linhas)

        assertThat(entradas).hasSize(3)
        assertThat(entradas[0].nivel).isEqualTo('?')
        assertThat(entradas[1]).isEqualTo(
            EntradaDoRegistro(
                "2026-10-31 15:46:12.345 -03:00",
                'W',
                "Envio [w] falhou\njava.io.IOException: x\n\tat com.exemplo.A(A.kt:10)",
            ),
        )
        assertThat(entradas[2].texto).isEqualTo("Sync [w] ok")
    }

    @Test
    fun `nao vaza token, senha nem assinatura`() {
        Sigilo.guardar("token", "abc123def456")

        assertThat(Sigilo.esconder("login com abc123def456 ok")).isEqualTo("login com *** ok")
        assertThat(Sigilo.esconder("Authorization: Bearer eyJhbGciOi.x.y")).doesNotContain("eyJ")
        assertThat(Sigilo.esconder("{\"login\":\"ana\",\"senha\":\"segredo1\"}")).isEqualTo("{\"login\":\"ana\",\"senha\":\"***\"}")
        assertThat(Sigilo.esconder("/storage/a.jpg?X-Amz-Expires=600&X-Amz-Signature=9f8e7d&X-Amz-Credential=AK%2F1"))
            .isEqualTo("/storage/a.jpg?X-Amz-Expires=600&X-Amz-Signature=***&X-Amz-Credential=***")
        assertThat(Sigilo.esconder("?token=zzz&id=3")).isEqualTo("?token=***&id=3")
    }

    @Test
    fun `texto comum passa inteiro`() {
        Sigilo.guardar("token", "abc")
        assertThat(Sigilo.esconder("Token não informado (401)")).isEqualTo("Token não informado (401)")
        assertThat(Sigilo.esconder("abc: conversa 8")).isEqualTo("abc: conversa 8")
    }

    @Test
    fun `resumo do erro com as causas`() {
        val erro = IOException("falhou o envio", SocketTimeoutException("timeout"))
        assertThat(erro.resumoDoErro()).isEqualTo("IOException: falhou o envio ← SocketTimeoutException: timeout")

        val proprio = object : Exception("ignorada"), DescricaoParaRegistro {
            override fun paraRegistro() = "Servidor 403: Sem permissão"
        }
        assertThat(proprio.resumoDoErro()).isEqualTo("Servidor 403: Sem permissão")
        assertThat(IllegalStateException().resumoDoErro()).isEqualTo("IllegalStateException")
    }
}
