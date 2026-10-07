package com.conversa.app.core.network.dto

import com.conversa.app.core.model.StatusChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.network.json.ConversaJson
import com.conversa.app.core.network.json.nuloExplicito
import com.conversa.app.core.testing.Fixtures
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Um teste por DTO, com os JSONs do contrato (core/testing/.../fixtures). */
class DesserializacaoTest {
    private val outros = ConversaJson.parseToJsonElement(Fixtures.ler("outros.json")).jsonObject

    private inline fun <reified T> de(campo: String): T = ConversaJson.decodeFromJsonElement<T>(outros[campo]!!)

    @Test
    fun login() {
        val login = ConversaJson.decodeFromString<LoginResposta>(Fixtures.ler("login.json"))
        assertThat(login.id).isEqualTo(7)
        assertThat(login.telefone).isNull()
        assertThat(login.dispositivo!!.id).isEqualTo(12)
        assertThat(login.token).startsWith("eyJ")
    }

    @Test
    fun conversas() {
        val lista = ConversaJson.decodeFromString(ListSerializer(ConversaDto.serializer()), Fixtures.ler("conversas.json")).map {
            it.paraModelo()
        }
        assertThat(lista[0].tipo).isEqualTo(TipoConversa.DIRETA)
        assertThat(lista[0].naoLidas).isEqualTo(3)
        assertThat(lista[0].ultimaMensagemEm).isEqualTo(Instant.parse("2026-10-06T12:00:00.123Z"))
        assertThat(lista[1].titulo).isEqualTo("Projeto Alpha")
        assertThat(lista[1].fixada).isTrue()
        assertThat(lista[1].arquivada).isTrue()
        assertThat(lista[1].ultimaMensagemId).isEqualTo(0)
    }

    @Test
    fun mensagens() {
        val lista = ConversaJson.decodeFromString(ListSerializer(MensagemDto.serializer()), Fixtures.ler("mensagens.json")).map {
            it.paraModelo()
        }

        val comResposta = lista[0]
        assertThat(comResposta.conteudos.map { it.ordem }).containsExactly(1, 2).inOrder()
        assertThat(comResposta.conteudos[1].tipo).isEqualTo(TipoConteudo.IMAGEM)
        assertThat(comResposta.referencia!!.tipo).isEqualTo(TipoReferencia.RESPOSTA)
        assertThat(comResposta.referencia!!.mensagem!!.conteudos.single().conteudo).isEqualTo("bom dia")
        assertThat(comResposta.reacoes.single().usuarios.single().nome).isEqualTo("Bruno Lima")

        val oculta = lista[1]
        assertThat(oculta.oculta).isTrue()
        assertThat(oculta.referencia).isNull()
        assertThat(oculta.reacoes).isEmpty()

        val tipos = lista[2].conteudos.map { it.tipo }
        assertThat(tipos).containsExactly(TipoConteudo.CHAMADA, TipoConteudo.ENQUETE, TipoConteudo.DESCONHECIDO).inOrder()
    }

    @Test
    fun `dados da chamada`() {
        val chamada = ConversaJson.decodeFromString<DadosChamadaDto>(Fixtures.ler("chamada-dados.json")).paraModelo()
        assertThat(chamada.status).isEqualTo(StatusChamada.PENDENTE)
        assertThat(chamada.participantes).hasSize(2)
        assertThat(chamada.conversaChatId).isNull()
    }

    @Test
    fun `pendentes sem usuarios e historico`() {
        val pendente = de<List<ChamadaPendenteDto>>("pendentes").single().paraModelo()
        assertThat(pendente.conversaId).isEqualTo(0)
        val historico = de<List<ChamadaHistoricoDto>>("historico").single()
        assertThat(historico.duracao).isEqualTo(125)
        assertThat(historico.participantes.single().duracao).isNull()
    }

    @Test
    fun `ice com urls em texto ou lista`() {
        val ice = de<IceDto>("ice").paraModelo()
        assertThat(ice.somenteRelay).isTrue()
        assertThat(ice.servidores.single().urls).containsExactly("turns:meu.dominio:8443?transport=tcp")
        assertThat(ice.servidores.single().usuario).isEqualTo("1791234567:7")
        val lista = de<IceDto>("ice_lista").paraModelo()
        assertThat(lista.somenteRelay).isFalse()
        assertThat(lista.servidores.single().urls).hasSize(2)
    }

    @Test
    fun atividades() {
        val atividade = de<List<AtividadeDto>>("atividades").single().paraModelo()
        assertThat(atividade.emoji).isEqualTo("😂")
        assertThat(atividade.nova).isTrue()
    }

    @Test
    fun enquete() {
        val enquete = de<EnqueteDto>("enquete").paraModelo()
        assertThat(enquete.opcoes).hasSize(2)
        assertThat(enquete.meusVotos).containsExactly(1L)
        assertThat(enquete.totalVotantes).isEqualTo(1)
    }

    @Test
    fun `anexo novo e anexo existente com id em texto`() {
        val novo = de<IncluirAnexoResposta>("anexo_novo")
        assertThat(novo.existe).isFalse()
        assertThat(novo.id).isEqualTo(5)
        val existente = de<IncluirAnexoResposta>("anexo_existente")
        assertThat(existente.existe).isTrue()
        assertThat(existente.id).isEqualTo(5)
        assertThat(existente.uploadStatus).isNull()
    }

    @Test
    fun `mensagens novas, status e sip vazio`() {
        assertThat(de<List<NovaMensagemDto>>("mensagens_novas").single().ate).isEqualTo("2026-10-06T12:00:00.123Z")
        assertThat(de<List<StatusMensagemDto>>("status").single().visualizada).isTrue()
        assertThat(de<SipDto>("sip_vazio").id).isNull()
    }

    @Test
    fun `nulos nao sao enviados, a nao ser o nulo explicito`() {
        val semToken = ConversaJson.encodeToJsonElement(
            AlterarDispositivoRequisicao.serializer(),
            AlterarDispositivoRequisicao(id = 12),
        ).jsonObject
        assertThat(semToken.keys).containsExactly("id")
        val logout = ConversaJson.encodeToJsonElement(
            AlterarDispositivoRequisicao.serializer(),
            AlterarDispositivoRequisicao(id = 12, tokenFcm = nuloExplicito),
        ) as JsonObject
        assertThat(logout.toString()).isEqualTo("""{"id":12,"token_fcm":null}""")
    }

    @Test
    fun `envio de mensagem com referencia usa os nomes do contrato`() {
        val corpo = EnviarMensagemRequisicao(
            conversaId = 42,
            conteudos = listOf(ConteudoEnvioDto(1, 1, "oi")),
            mensagemReferencia = ReferenciaEnvioDto(tipo = 1, origemMensagemId = 99),
        )
        assertThat(ConversaJson.encodeToString(EnviarMensagemRequisicao.serializer(), corpo))
            .isEqualTo(
                """{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"mensagem_referencia":{"tipo":1,"origem_mensagem_id":99}}""",
            )
    }
}
