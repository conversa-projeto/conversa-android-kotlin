package com.conversa.app.core.data

import com.conversa.app.core.database.entidades.AtividadeEntidade
import com.conversa.app.core.database.entidades.ContatoEntidade
import com.conversa.app.core.database.entidades.ConteudoEntidade
import com.conversa.app.core.database.entidades.ConversaEntidade
import com.conversa.app.core.database.entidades.MensagemCompleta
import com.conversa.app.core.database.entidades.MensagemEntidade
import com.conversa.app.core.database.entidades.ReacaoEntidade
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conteudo
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.MensagemResumida
import com.conversa.app.core.model.Reacao
import com.conversa.app.core.model.ReferenciaMensagem
import com.conversa.app.core.model.StatusTranscricao
import com.conversa.app.core.model.TipoAtividade
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.UsuarioReacao
import com.conversa.app.core.model.intensidadeDoTexto
import com.conversa.app.core.model.intensidadeParaTexto
import com.conversa.app.core.model.pastasDoTexto
import com.conversa.app.core.model.pastasParaTexto
import com.conversa.app.core.network.dto.ConfirmacaoDto
import com.conversa.app.core.network.dto.ContatoDto
import com.conversa.app.core.network.dto.ConteudoDto
import com.conversa.app.core.network.dto.ConversaDto
import com.conversa.app.core.network.dto.MensagemDto
import com.conversa.app.core.network.dto.MensagemResumidaDto
import com.conversa.app.core.network.dto.PostagemDto
import com.conversa.app.core.network.dto.ReferenciaDto
import com.conversa.app.core.network.dto.UsuarioReacaoDto
import com.conversa.app.core.network.dto.paraModelo
import com.conversa.app.core.network.json.ConversaJson
import kotlinx.serialization.builtins.ListSerializer

// DTO do servidor → entidade do Room → modelo de domínio.

internal fun ConversaDto.paraEntidade() = ConversaEntidade(
    id = id,
    tipo = tipo,
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
    emoji = emoji?.trim()?.takeIf { it.isNotEmpty() },
    chamada = chamada,
)

fun ConversaEntidade.paraModelo() = Conversa(
    id = id,
    tipo = TipoConversa.de(tipo),
    descricao = descricao,
    nome = nome,
    destinatarioId = destinatarioId,
    ultimaMensagemId = ultimaMensagemId,
    ultimaMensagemEm = ultimaMensagemEm,
    ultimaMensagemTexto = ultimaMensagemTexto,
    naoLidas = naoLidas,
    fixadaOrdem = fixadaOrdem,
    arquivadaEm = arquivadaEm,
    avatarUrl = avatarUrl,
    emoji = emoji,
    chamada = chamada,
)

private val serializadorUsuariosReacao = ListSerializer(UsuarioReacaoDto.serializer())

internal fun MensagemDto.paraEntidade() = MensagemCompleta(
    mensagem = MensagemEntidade(
        id = id,
        conversaId = conversaId,
        remetenteId = remetenteId,
        remetente = remetente,
        inserida = inserida,
        visivelEm = visivelEm,
        excluidaEm = excluidaEm,
        dataEfetiva = visivelEm ?: inserida,
        referenciaJson = mensagemReferencia?.let { ConversaJson.encodeToString(ReferenciaDto.serializer(), it) },
        recebida = recebida,
        visualizada = visualizada,
        reproduzida = reproduzida,
        postagemJson = postagem?.let { ConversaJson.encodeToString(PostagemDto.serializer(), it) },
        confirmacaoJson = confirmacao?.let { ConversaJson.encodeToString(ConfirmacaoDto.serializer(), it) },
        postagemId = postagemId,
        pastas = pastasParaTexto(pastas),
    ),
    conteudos = conteudos.map {
        ConteudoEntidade(
            mensagemId = id,
            ordem = it.ordem,
            id = it.id,
            tipo = it.tipo,
            conteudo = it.conteudo.orEmpty(),
            nome = it.nome,
            extensao = it.extensao,
            transcricaoStatus = it.transcricaoStatus,
            transcricao = it.transcricao,
            chatChamadaId = it.chatChamadaId,
            duracao = it.duracao,
            intensidade = it.intensidade?.let(::intensidadeParaTexto),
        )
    },
    reacoes = reacoes.mapIndexed { ordem, it ->
        ReacaoEntidade(
            mensagemId = id,
            emoji = it.emoji,
            quantidade = it.quantidade,
            reagiu = it.reagiu,
            usuariosJson = ConversaJson.encodeToString(serializadorUsuariosReacao, it.usuarios),
            ordem = ordem,
        )
    },
)

fun MensagemCompleta.paraModelo() = Mensagem(
    id = mensagem.id,
    remetenteId = mensagem.remetenteId,
    remetente = mensagem.remetente,
    conversaId = mensagem.conversaId,
    inserida = mensagem.inserida,
    visivelEm = mensagem.visivelEm,
    excluidaEm = mensagem.excluidaEm,
    referencia = mensagem.referenciaJson?.let { json ->
        runCatching { ConversaJson.decodeFromString(ReferenciaDto.serializer(), json).paraModelo() }.getOrNull()
    },
    recebida = mensagem.recebida,
    visualizada = mensagem.visualizada,
    reproduzida = mensagem.reproduzida,
    enviando = mensagem.enviando,
    falhou = mensagem.falhou,
    postagem = mensagem.postagemJson?.let { json ->
        runCatching { ConversaJson.decodeFromString(PostagemDto.serializer(), json).paraModelo() }.getOrNull()
    },
    confirmacao = mensagem.confirmacaoJson?.let { json ->
        runCatching { ConversaJson.decodeFromString(ConfirmacaoDto.serializer(), json).paraModelo() }.getOrNull()
    },
    postagemId = mensagem.postagemId,
    pastas = pastasDoTexto(mensagem.pastas),
    conteudos = conteudos.sortedBy { it.ordem }.map {
        Conteudo(
            id = it.id,
            ordem = it.ordem,
            tipo = TipoConteudo.de(it.tipo),
            conteudo = it.conteudo,
            nome = it.nome,
            extensao = it.extensao,
            transcricaoStatus = StatusTranscricao.de(it.transcricaoStatus),
            transcricao = it.transcricao,
            chatChamadaId = it.chatChamadaId,
            duracao = it.duracao,
            intensidade = intensidadeDoTexto(it.intensidade),
        )
    },
    reacoes = reacoes.sortedBy { it.ordem }.map { reacao ->
        val usuarios = runCatching {
            ConversaJson.decodeFromString(serializadorUsuariosReacao, reacao.usuariosJson)
        }.getOrDefault(emptyList())
        Reacao(
            emoji = reacao.emoji,
            quantidade = reacao.quantidade,
            reagiu = reacao.reagiu,
            usuarios = usuarios.map { UsuarioReacao(it.usuarioId, it.nome, it.reagidoEm, it.avatarUrl) },
        )
    },
)

/** Reação do modelo para o Room (a otimista, antes do servidor), na posição [ordem]. */
internal fun Reacao.paraEntidade(mensagemId: Long, ordem: Int) = ReacaoEntidade(
    mensagemId = mensagemId,
    ordem = ordem,
    emoji = emoji,
    quantidade = quantidade,
    reagiu = reagiu,
    usuariosJson = ConversaJson.encodeToString(
        serializadorUsuariosReacao,
        usuarios.map { UsuarioReacaoDto(it.usuarioId, it.nome, it.reagidoEm, it.avatarUrl) },
    ),
)

internal fun ContatoDto.paraEntidade() = ContatoEntidade(
    id = id,
    nome = nome,
    login = login,
    email = email,
    telefone = telefone,
    avatarUrl = avatarUrl,
    bot = bot,
    criadoEm = criadoEm,
)

fun ContatoEntidade.paraModelo() =
    Contato(id = id, nome = nome, login = login, email = email, telefone = telefone, avatarUrl = avatarUrl, bot = bot, criadoEm = criadoEm)

/**
 * Mensagem do modelo no formato da citação que o servidor devolve: a otimista de uma
 * resposta (7.3) já mostra a citação, com a cadeia que a respondida tinha.
 */
internal fun Mensagem.paraResumidaDto(): MensagemResumidaDto = MensagemResumidaDto(
    id = id,
    conversaId = conversaId,
    remetente = remetente,
    inserida = inserida,
    excluidaEm = excluidaEm,
    conteudos = conteudos.map { it.paraDto() },
    mensagemReferencia = referencia?.paraDto(),
)

private fun ReferenciaMensagem.paraDto(): ReferenciaDto = ReferenciaDto(tipo.codigo, mensagem?.paraDto())

private fun MensagemResumida.paraDto(): MensagemResumidaDto = MensagemResumidaDto(
    id = id,
    conversaId = conversaId,
    remetente = remetente,
    inserida = inserida,
    excluidaEm = excluidaEm,
    conteudos = conteudos.map { it.paraDto() },
    mensagemReferencia = referencia?.paraDto(),
)

private fun Conteudo.paraDto() = ConteudoDto(id, ordem, tipo.codigo, conteudo, nome, extensao, transcricaoStatus.codigo, transcricao)

// --- Atividades (8.1): o Room guarda os códigos; o modelo, os enums ---

internal fun Atividade.paraEntidade() = AtividadeEntidade(
    id = id,
    tipo = tipo.codigo,
    criadoEm = criadoEm,
    pendente = pendente,
    autorId = autorId,
    autorNome = autorNome,
    autorAvatarUrl = autorAvatarUrl,
    conversaId = conversaId,
    conversaTipo = conversaTipo?.codigo,
    conversaDescricao = conversaDescricao,
    mensagemId = mensagemId,
    conteudoTipo = conteudoTipo?.codigo,
    texto = texto,
    chamadaId = chamadaId,
    chamadaTipo = chamadaTipo?.codigo,
    emoji = emoji,
)

fun AtividadeEntidade.paraModelo() = Atividade(
    id = id,
    tipo = TipoAtividade.de(tipo),
    criadoEm = criadoEm,
    pendente = pendente,
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
