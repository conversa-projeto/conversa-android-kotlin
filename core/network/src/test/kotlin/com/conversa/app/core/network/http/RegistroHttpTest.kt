package com.conversa.app.core.network.http

import com.conversa.app.core.network.realtime.EventoSocket
import com.conversa.app.core.network.realtime.resumoParaRegistro
import com.conversa.app.core.registro.resumoDoErro
import com.google.common.truth.Truth.assertThat
import java.net.SocketTimeoutException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/** O que das requisições e dos eventos vai para o registro do app (e o que fica de fora). */
class RegistroHttpTest {
    @Test
    fun `endereco com numeros e sem os textos`() {
        assertThat(resumoDaUrl("https://10.0.5.78/api/mensagens?conversa=8&mensagemreferencia=1247&mensagensprevias=50".toHttpUrl()))
            .isEqualTo("https://10.0.5.78/api/mensagens?conversa=8&mensagemreferencia=1247&mensagensprevias=50")
        assertThat(resumoDaUrl("http://localhost:8081/api/pesquisar?texto=reuni%C3%A3o%20amanh%C3%A3&conversa=0".toHttpUrl()))
            .isEqualTo("http://localhost:8081/api/pesquisar?texto=…&conversa=0")
        assertThat(resumoDaUrl("https://s.exemplo.com/api/mensagens/status?conversa=8&mensagem=1,2,3".toHttpUrl()))
            .isEqualTo("https://s.exemplo.com/api/mensagens/status?conversa=8&mensagem=1,2,3")
        assertThat(
            resumoDaUrl("https://s.exemplo.com/api/x?ativo=true&vazio=".toHttpUrl()),
        ).isEqualTo("https://s.exemplo.com/api/x?ativo=true&vazio=")
    }

    @Test
    fun `url assinada do anexo sem a assinatura`() {
        val url = "https://s.exemplo.com/storage/conversa/abc.jpg?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AK%2F20261031" +
            "&X-Amz-Date=20261031T154600Z&X-Amz-Expires=600&X-Amz-Signature=9f8e7d6c"
        assertThat(resumoDaUrl(url.toHttpUrl())).isEqualTo(
            "https://s.exemplo.com/storage/conversa/abc.jpg?X-Amz-Algorithm=…&X-Amz-Credential=…&X-Amz-Date=…&X-Amz-Expires=600" +
                "&X-Amz-Signature=…",
        )
    }

    @Test
    fun `servicos de mapa so pelo nome`() {
        assertThat(resumoDaUrl("https://nominatim.openstreetmap.org/search?q=Rua%20X&format=json".toHttpUrl()))
            .isEqualTo("nominatim.openstreetmap.org (mapa)")
        assertThat(resumoDaUrl("https://tile.openstreetmap.org/15/12000/18000.png".toHttpUrl())).isEqualTo("tile.openstreetmap.org (mapa)")
    }

    @Test
    fun `tamanhos legiveis`() {
        assertThat(tamanho(512)).isEqualTo("512 B")
        assertThat(tamanho(2_355)).isEqualTo("2,3 kB")
        assertThat(tamanho(4_300_000)).isEqualTo("4,1 MB")
    }

    @Test
    fun `eventos do socket sem conteudo de mensagem`() {
        assertThat(EventoSocket.NovaMensagem("Ana", "o segredo da reunião").resumoParaRegistro()).isEqualTo("mensagem nova")
        assertThat(EventoSocket.Reacao(8, 1247, 7, "😂", adicionada = true).resumoParaRegistro()).doesNotContain("😂")
        assertThat(EventoSocket.Desconhecido(99, "{\"tipo\":99,\"texto\":\"oi\"}").resumoParaRegistro()).isEqualTo("tipo desconhecido 99")
        val sinal = EventoSocket.SinalChamada(
            5,
            7,
            buildJsonObject {
                put("acao", JsonPrimitive("ponteiro"))
                put("x", JsonPrimitive(0.5))
            },
        )
        assertThat(sinal.resumoParaRegistro()).isEqualTo("sinal ponteiro do usuário 7 na chamada 5")
        assertThat(EventoSocket.StatusMensagens(8, listOf(1, 2)).resumoParaRegistro()).isEqualTo("status de 2 mensagens na conversa 8")
    }

    @Test
    fun `erro da api se descreve com o status`() {
        assertThat(ErroApi.Servidor(403, "Sem permissão").paraRegistro()).isEqualTo("Servidor 403: Sem permissão")
        assertThat(ErroApi.ServidorIndisponivel(502).paraRegistro()).isEqualTo("ServidorIndisponivel 502")
        assertThat(
            ErroApi.SemConexao(SocketTimeoutException("timeout")).resumoDoErro(),
        ).isEqualTo("SemConexao ← SocketTimeoutException: timeout")
    }
}
