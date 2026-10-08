package com.conversa.app.core.network.dto

import com.conversa.app.core.model.Atividade
import com.conversa.app.core.model.Chamada
import com.conversa.app.core.model.ChamadaHistorico
import com.conversa.app.core.model.ChamadaNaMensagem
import com.conversa.app.core.model.ChamadaPendente
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.Enquete
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.MensagemResumida
import com.conversa.app.core.model.OpcaoEnquete
import com.conversa.app.core.model.ParticipanteChamada
import com.conversa.app.core.model.ParticipanteHistorico
import com.conversa.app.core.model.ParticipanteNaMensagem
import com.conversa.app.core.model.Reacao
import com.conversa.app.core.model.ReferenciaMensagem
import com.conversa.app.core.model.ServidorIce
import com.conversa.app.core.model.ServidoresIce
import com.conversa.app.core.model.StatusChamada
import com.conversa.app.core.model.StatusParticipante
import com.conversa.app.core.model.StatusTranscricao
import com.conversa.app.core.model.TipoAtividade
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.TipoReferencia
import com.conversa.app.core.model.UsuarioReacao
import com.conversa.app.core.model.Votante
import com.conversa.app.core.network.json.ConversaJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

// DTO do servidor → modelo de domínio.

fun ConversaDto.paraModelo() = Conversa(
    id = id,
    tipo = TipoConversa.de(tipo),
    descricao = descricao?.trim()?.takeIf { it.isNotEmpty() },
    nome = nome,
    destinatarioId = destinatarioId,
    ultimaMensagemId = mensagemId,
    ultimaMensagemEm = ultimaMensagem,
    ultimaMensagemTexto = ultimaMensagemTexto,
    naoLidas = mensagensSemVisualizar,
    fixadaOrdem = fixadaOrdem,
    arquivadaEm = arquivadaEm,
    avatarUrl = avatarUrl,
)

fun ContatoDto.paraModelo() = Contato(id, nome, login, email, telefone, avatarUrl)

fun MembroDto.paraModelo() = MembroConversa(vinculoId = id, usuarioId = usuarioId, nome = nome, avatarUrl = avatarUrl)

fun ConteudoDto.paraModelo() = Conteudo(
    id = id,
    ordem = ordem,
    tipo = TipoConteudo.de(tipo),
    conteudo = conteudo.orEmpty(),
    nome = nome,
    extensao = extensao,
    transcricaoStatus = StatusTranscricao.de(transcricaoStatus),
    transcricao = transcricao,
)

fun ReferenciaDto.paraModelo(): ReferenciaMensagem = ReferenciaMensagem(
    tipo = TipoReferencia.de(tipo),
    mensagem = mensagem?.paraModelo(),
)

fun MensagemResumidaDto.paraModelo(): MensagemResumida = MensagemResumida(
    id = id,
    conversaId = conversaId,
    remetente = remetente,
    inserida = inserida,
    excluidaEm = excluidaEm,
    conteudos = conteudos.sortedBy { it.ordem }.map { it.paraModelo() },
    referencia = mensagemReferencia?.paraModelo(),
)

fun ReacaoDto.paraModelo() = Reacao(
    emoji = emoji,
    quantidade = quantidade,
    reagiu = reagiu,
    usuarios = usuarios.map { UsuarioReacao(it.usuarioId, it.nome, it.reagidoEm, it.avatarUrl) },
)

fun MensagemDto.paraModelo() = Mensagem(
    id = id,
    remetenteId = remetenteId,
    remetente = remetente,
    conversaId = conversaId,
    inserida = inserida,
    visivelEm = visivelEm,
    excluidaEm = excluidaEm,
    referencia = mensagemReferencia?.paraModelo(),
    recebida = recebida,
    visualizada = visualizada,
    reproduzida = reproduzida,
    conteudos = conteudos.sortedBy { it.ordem }.map { it.paraModelo() },
    reacoes = reacoes.map { it.paraModelo() },
)

fun DadosChamadaDto.paraModelo() = Chamada(
    id = id,
    tipo = TipoChamada.de(tipo),
    status = StatusChamada.de(status),
    criadoEm = criadoEm,
    criadoPor = criadoPor,
    iniciada = iniciada,
    finalizada = finalizada,
    conversaChatId = conversaChatId,
    participantes = usuarios.map {
        ParticipanteChamada(it.usuarioId, it.usuarioNome, StatusParticipante.de(it.status), it.entrouEm, it.saiuEm)
    },
)

fun ChamadaHistoricoDto.paraModelo() = ChamadaHistorico(
    id = id,
    tipo = TipoChamada.de(tipo),
    status = StatusChamada.de(status),
    criadoEm = criadoEm,
    criadoPor = criadoPor,
    conversaId = conversaId?.takeIf { it > 0 },
    duracao = duracao,
    participantes = participantes.map { ParticipanteHistorico(it.usuarioId, it.nome, StatusParticipante.de(it.status), it.avatarUrl) },
)

fun ChamadaPendenteDto.paraModelo() = ChamadaPendente(
    id = id,
    tipo = TipoChamada.de(tipo),
    status = StatusChamada.de(status),
    conversaId = conversaId,
    criadoEm = criadoEm,
    criadoPor = criadoPor,
)

fun IceDto.paraModelo() = ServidoresIce(
    servidores = iceServers.map { servidor ->
        val urls = when (val u = servidor.urls) {
            is JsonArray -> u.mapNotNull { it.jsonPrimitive.contentOrNull }
            is JsonPrimitive -> listOfNotNull(u.contentOrNull)
            else -> emptyList()
        }
        ServidorIce(urls = urls, usuario = servidor.username, credencial = servidor.credential)
    },
    somenteRelay = iceTransportPolicy.equals("relay", ignoreCase = true),
)

fun AtividadeDto.paraModelo() = Atividade(
    id = id,
    tipo = TipoAtividade.de(tipo),
    criadoEm = criadoEm,
    nova = nova,
    autorId = autorId,
    autorNome = autorNome,
    autorAvatarUrl = autorAvatarUrl,
    conversaId = conversaId,
    conversaTipo = conversaTipo?.let(TipoConversa::de),
    conversaDescricao = conversaDescricao,
    mensagemId = mensagemId,
    conteudoTipo = conteudoTipo?.let(TipoConteudo::de),
    texto = texto,
    chamadaId = chamadaId,
    chamadaTipo = chamadaTipo?.let(TipoChamada::de),
    emoji = emoji,
)

fun EnqueteDto.paraModelo() = Enquete(
    id = id,
    conversaId = conversaId,
    mensagemId = mensagemId,
    pergunta = pergunta,
    multipla = multipla,
    criadoPor = criadoPor,
    opcoes = opcoes.map { opcao -> OpcaoEnquete(opcao.id, opcao.texto, opcao.votantes.map { Votante(it.id, it.nome) }) },
    totalVotantes = totalVotantes,
    meusVotos = meusVotos,
    encerraEm = encerraEm,
    encerradaEm = encerradaEm,
    encerrada = encerrada,
    podeEncerrar = podeEncerrar,
    podeAlterarPrazo = podeAlterarPrazo,
)

/** Lê o JSON do conteúdo tipo 6 (bolha de chamada, MSG-13). JSON inválido → `null` (a bolha mostra o padrão). */
fun lerChamadaDaMensagem(conteudo: String): ChamadaNaMensagem? = runCatching {
    val dto = ConversaJson.decodeFromString(ChamadaConteudoDto.serializer(), conteudo)
    ChamadaNaMensagem(
        chamadaId = dto.chamadaId,
        tipo = TipoChamada.de(dto.tipo),
        status = dto.status,
        duracaoSegundos = dto.duracao,
        participantes = dto.participantes.map { ParticipanteNaMensagem(it.usuarioId, it.nome, it.status, it.duracao) },
    )
}.getOrNull()
