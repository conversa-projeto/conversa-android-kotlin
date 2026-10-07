package com.conversa.app.core.network.realtime

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Um payload real de cada tipo do contrato §6.6. */
class EventoSocketParserTest {
    private fun ler(json: String) = EventoSocketParser.ler(json)

    @Test
    fun `erro de login e erro de leitura`() {
        assertThat(ler("""{"tipo":0,"message":"Token inválido ou expirado"}"""))
            .isEqualTo(EventoSocket.ErroLogin("Token inválido ou expirado"))
        assertThat(ler("""{"tipo":9,"message":"JSON inválido!"}""")).isEqualTo(EventoSocket.ErroLeitura("JSON inválido!"))
    }

    @Test
    fun `nova mensagem sem conversa_id`() {
        assertThat(ler("""{"tipo":2,"titulo":"Ana Souza","mensagem":"oi"}""")).isEqualTo(EventoSocket.NovaMensagem("Ana Souza", "oi"))
    }

    @Test
    fun `status com ids em csv`() {
        assertThat(ler("""{"tipo":3,"grupo":42,"mensagens":"12, 13,14"}"""))
            .isEqualTo(EventoSocket.StatusMensagens(42, listOf(12, 13, 14)))
    }

    @Test
    fun `digitando gravando e reacao`() {
        assertThat(ler("""{"tipo":4,"conversa_id":42,"usuario_id":8}""")).isEqualTo(EventoSocket.Digitando(42, 8))
        assertThat(ler("""{"tipo":5,"conversa_id":42,"usuario_id":8}""")).isEqualTo(EventoSocket.GravandoAudio(42, 8))
        assertThat(ler("""{"tipo":7,"conversa_id":42,"mensagem_id":101,"usuario_id":8,"emoji":"👍","acao":"remove"}"""))
            .isEqualTo(EventoSocket.Reacao(42, 101, 8, "👍", adicionada = false))
        assertThat(
            (
                ler(
                    """{"tipo":7,"conversa_id":42,"mensagem_id":101,"usuario_id":8,"emoji":"👍","acao":"add"}""",
                ) as EventoSocket.Reacao
                ).adicionada,
        )
            .isTrue()
    }

    @Test
    fun `conversa atualizada`() {
        assertThat(ler("""{"tipo":40,"conversa_id":42,"usuario_id":7}""")).isEqualTo(EventoSocket.ConversaAtualizada(42, 7))
    }

    @Test
    fun `eventos de chamada 51 a 56`() {
        assertThat(ler("""{"tipo":51,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.ChamadaRecebida(10, 7))
        assertThat(ler("""{"tipo":52,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.ChamadaFinalizada(10, 7))
        assertThat(ler("""{"tipo":53,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.UsuarioRecusou(10, 7))
        assertThat(ler("""{"tipo":54,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.UsuarioEntrou(10, 7))
        assertThat(ler("""{"tipo":55,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.UsuarioSaiu(10, 7))
        assertThat(ler("""{"tipo":56,"chamada_id":10,"usuario_id":7}""")).isEqualTo(EventoSocket.VideoAtivado(10, 7))
    }

    @Test
    fun `sinal da chamada com dados`() {
        val evento = ler(
            """{"tipo":57,"chamada_id":10,"usuario_id":7,"dados":{"acao":"chat","conversa_id":99}}""",
        ) as EventoSocket.SinalChamada
        assertThat(evento.chamadaId).isEqualTo(10)
        assertThat(evento.dados["acao"]!!.jsonPrimitive.content).isEqualTo("chat")
    }

    @Test
    fun `presenca atividade e enquete`() {
        assertThat(ler("""{"tipo":60,"usuario_id":8,"online":true}""")).isEqualTo(EventoSocket.StatusUsuario(8, true))
        assertThat(ler("""{"tipo":61}""")).isEqualTo(EventoSocket.NovaAtividade)
        assertThat(ler("""{"tipo":62,"enquete_id":42,"conversa_id":50}""")).isEqualTo(EventoSocket.EnqueteAtualizada(42, 50))
    }

    @Test
    fun `tipo novo ou json quebrado vira desconhecido sem excecao`() {
        assertThat(ler("""{"tipo":99,"x":1}""")).isInstanceOf(EventoSocket.Desconhecido::class.java)
        assertThat(ler("nao e json")).isInstanceOf(EventoSocket.Desconhecido::class.java)
        assertThat(ler("""{"tipo":51}""")).isInstanceOf(EventoSocket.Desconhecido::class.java)
    }

    @Test
    fun `ids em texto sao aceitos`() {
        assertThat(ler("""{"tipo":4,"conversa_id":"42","usuario_id":"8"}""")).isEqualTo(EventoSocket.Digitando(42, 8))
    }
}
