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
    fun `postagem com as ultimas respostas e confirmacao com lista`() {
        val json = """
            {"id":1170,"remetente_id":8,"remetente":"Bruno","conversa_id":3,"inserida":"2026-10-13T23:08:00.000Z",
             "conteudos":[{"ordem":1,"tipo":1,"conteudo":"Aviso"}],
             "postagem":{"titulo":"Teste de postagem","respostas":2,"nao_lidas":1,
               "ultimas":[{"id":1171,"remetente_id":9,"remetente":"Carla","conversa_id":3,"inserida":"2026-10-13T23:09:00Z",
                 "postagem_id":1170,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"Resposta"}]}]},
             "confirmacao":{"total":2,"confirmou":false,"pode_confirmar":true,
               "destinatarios":[{"usuario_id":7,"nome":"Ana"},{"usuario_id":9,"nome":"Carla"}],
               "usuarios":[{"usuario_id":9,"nome":"Carla","avatar_url":null,"confirmada_em":"2026-10-13T23:10:00Z"}]}}
        """.trimIndent()
        val mensagem = ConversaJson.decodeFromString<MensagemDto>(json).paraModelo()

        val postagem = mensagem.postagem!!
        assertThat(postagem.titulo).isEqualTo("Teste de postagem")
        assertThat(postagem.respostas).isEqualTo(2)
        assertThat(postagem.naoLidas).isEqualTo(1)
        assertThat(postagem.ultimas.single().conteudos.single().conteudo).isEqualTo("Resposta")
        val confirmacao = mensagem.confirmacao!!
        assertThat(confirmacao.podeConfirmar).isTrue()
        assertThat(confirmacao.destinatarios.map { it.nome }).containsExactly("Ana", "Carla").inOrder()
        assertThat(confirmacao.usuarios.single().usuarioId).isEqualTo(9)
    }

    @Test
    fun `conversa com emoji do grupo e chat de ligacao`() {
        val lista = ConversaJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ConversaDto.serializer()),
            """[{"id":15,"tipo":2,"descricao":"Ana, Bruno","emoji":"🎉","chamada":true},{"id":3,"tipo":2}]""",
        )
        assertThat(
            lista.map {
                Triple(it.id, it.emoji, it.chamada)
            },
        ).containsExactly(Triple(15L, "🎉", true), Triple(3L, null, false)).inOrder()
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
        // 🆕 5cad911: data final e permissões; sem os campos (servidor antigo), aberta e sem permissões.
        val prazo = java.time.Instant.parse("2026-10-09T18:00:00Z")
        assertThat(enquete.encerraEm).isEqualTo(prazo)
        assertThat(enquete.encerradaEm).isNull()
        assertThat(enquete.podeEncerrar).isTrue()
        assertThat(enquete.podeAlterarPrazo).isTrue()
        assertThat(enquete.fechada(prazo.minusSeconds(1))).isFalse()
        assertThat(enquete.fechada(prazo)).isTrue()
        val antiga = ConversaJson.decodeFromString<EnqueteDto>("""{"id":1,"conversa_id":2,"pergunta":"?"}""").paraModelo()
        assertThat(antiga.fechada(prazo)).isFalse()
        assertThat(antiga.podeEncerrar).isFalse()
    }

    @Test
    fun `prazo da enquete - null explicito para tirar a data`() {
        val tirar = ConversaJson.encodeToString(
            PrazoEnqueteRequisicao.serializer(),
            PrazoEnqueteRequisicao(42, com.conversa.app.core.network.json.nuloExplicito),
        )
        assertThat(tirar).isEqualTo("""{"enquete_id":42,"encerra_em":null}""")
        val definir = ConversaJson.encodeToString(
            PrazoEnqueteRequisicao.serializer(),
            PrazoEnqueteRequisicao(42, kotlinx.serialization.json.JsonPrimitive("2026-10-09T18:00:00Z")),
        )
        assertThat(definir).isEqualTo("""{"enquete_id":42,"encerra_em":"2026-10-09T18:00:00Z"}""")
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
    fun `privacidade vai so com a chave que mudou`() {
        assertThat(ConversaJson.encodeToString(AlterarUsuarioRequisicao.serializer(), AlterarUsuarioRequisicao(7, aparecerOffline = true)))
            .isEqualTo("""{"id":7,"aparecer_offline":true}""")
        assertThat(
            ConversaJson.decodeFromString(
                PrivacidadeDto.serializer(),
                """{"mostrar_visto_em":false,"mostrar_na_conversa":true,"aparecer_offline":false}""",
            ),
        )
            .isEqualTo(PrivacidadeDto(mostrarVistoEm = false))
    }

    @Test
    fun `envio de postagem e de resposta a postagem usam os nomes do contrato`() {
        val conteudos = listOf(ConteudoEnvioDto(1, 1, "oi"))
        fun json(corpo: EnviarMensagemRequisicao) = ConversaJson.encodeToString(EnviarMensagemRequisicao.serializer(), corpo)

        assertThat(json(EnviarMensagemRequisicao(42, conteudos, postagem = PostagemEnvioDto("Aviso"))))
            .isEqualTo("""{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"postagem":{"titulo":"Aviso"}}""")
        // Sem título: o servidor aceita o objeto vazio (`titulo` é opcional).
        assertThat(json(EnviarMensagemRequisicao(42, conteudos, postagem = PostagemEnvioDto())))
            .isEqualTo("""{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"postagem":{}}""")
        assertThat(json(EnviarMensagemRequisicao(42, conteudos, postagemId = 104)))
            .isEqualTo("""{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"postagem_id":104}""")
    }

    @Test
    fun `confirmacao de leitura no envio e no pedir depois usa os nomes do contrato`() {
        val conteudos = listOf(ConteudoEnvioDto(1, 1, "oi"))
        fun json(corpo: EnviarMensagemRequisicao) = ConversaJson.encodeToString(EnviarMensagemRequisicao.serializer(), corpo)

        assertThat(json(EnviarMensagemRequisicao(42, conteudos, pedeConfirmacao = true)))
            .isEqualTo("""{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"pede_confirmacao":true}""")
        assertThat(json(EnviarMensagemRequisicao(42, conteudos, pedeConfirmacao = true, confirmacaoUsuarios = listOf(8, 9))))
            .isEqualTo(
                """{"conversa_id":42,"conteudos":[{"ordem":1,"tipo":1,"conteudo":"oi"}],"pede_confirmacao":true,"confirmacao_usuarios":[8,9]}""",
            )
        // Sem a lista: todos os outros membros.
        assertThat(ConversaJson.encodeToString(PedirConfirmacaoRequisicao.serializer(), PedirConfirmacaoRequisicao(5)))
            .isEqualTo("""{"mensagem_id":5}""")
        assertThat(ConversaJson.encodeToString(PedirConfirmacaoRequisicao.serializer(), PedirConfirmacaoRequisicao(5, listOf(8))))
            .isEqualTo("""{"mensagem_id":5,"usuarios":[8]}""")
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

    @Test
    fun `resumo da chamada no conteudo tipo 6, com datas sem fuso`() {
        val mensagens = ConversaJson.decodeFromString<List<MensagemDto>>(Fixtures.ler("mensagens.json"))
        val conteudo = mensagens.flatMap { it.conteudos }.first { it.tipo == 6 }.conteudo!!
        val chamada = lerChamadaDaMensagem(conteudo)!!
        assertThat(chamada.chamadaId).isEqualTo(10)
        assertThat(chamada.tipo).isEqualTo(com.conversa.app.core.model.TipoChamada.AUDIO)
        assertThat(chamada.encerrada).isTrue()
        assertThat(chamada.duracaoSegundos).isEqualTo(125)

        val grupo = lerChamadaDaMensagem(
            """{"chamada_id":11,"tipo":2,"status":5,"iniciada":null,"finalizada":null,"duracao":null,
               "participantes":[{"usuario_id":7,"nome":"Ana","status":4,"duracao":60},{"usuario_id":8,"nome":"Bruno","status":1,"duracao":null},
               {"usuario_id":9,"nome":"Caio","status":2,"duracao":null}]}""",
        )!!
        assertThat(grupo.emGrupo).isTrue()
        assertThat(grupo.falhou).isTrue()
        assertThat(grupo.participantes.map { it.duracaoSegundos }).containsExactly(60L, null, null).inOrder()
        assertThat(lerChamadaDaMensagem("não é json")).isNull()
    }
}
