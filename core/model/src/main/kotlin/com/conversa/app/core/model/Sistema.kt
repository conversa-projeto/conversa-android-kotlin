package com.conversa.app.core.model

// Sistema e Acessos (8.5, FC-808, CFG-07, CFG-08, AUT-09), como o `ConfiguracaoSistema.vue`
// e o `ConfiguracaoAcessos.vue` do web.

/** Códigos de permissão do servidor (contrato §13.1) e o nome que o web mostra. */
object CodigoPermissao {
    /** "Sistema": ver e alterar os parâmetros. */
    const val PARAMETROS = "parametros"

    /** "Acessos": conceder e retirar permissões. */
    const val PERMISSOES = "permissoes"

    // Administração e uso do app (servidor `ef13c22`); os de uso o servidor confere em cada rota.
    const val BOTS = "bots"
    const val USUARIOS = "usuarios"
    const val FERRAMENTAS = "ferramentas"
    const val CONHECIMENTO = "conhecimento"
    const val EMOJI = "emoji"
    const val FIGURINHA = "figurinha"
    const val IMAGEM = "imagem"
    const val VIDEO = "video"
    const val AUDIO = "audio"
    const val ARQUIVO = "arquivo"
    const val RESPONDER = "responder"
    const val ENCAMINHAR = "encaminhar"
    const val REAGIR = "reagir"
    const val POSTAGEM = "postagem"
    const val VOTACAO = "votacao"
    const val CONFIRMACAO = "confirmacao"
    const val AGENDAR = "agendar"
    const val GRUPO = "grupo"
    const val CHAMADA_AUDIO = "chamada_audio"
    const val CHAMADA_VIDEO = "chamada_video"
    const val COMPARTILHAR_TELA = "compartilhar_tela"
    const val TELEFONIA = "telefonia"
    const val OCULTAR = "ocultar"
    const val NOME = "nome"
    const val EMAIL = "email"
    const val AVATAR = "avatar"
    const val SENHA = "senha"
    const val PRIVACIDADE = "privacidade"
    const val CORES = "cores"
    const val CONFIG_CHAMADAS = "config_chamadas"
}

/** Os grupos da tela de Acessos, na ordem do web (`utils/permissoesUso.ts`). */
enum class GrupoPermissao(val codigos: List<String>) {
    ADMINISTRACAO(emptyList()),
    ENVIAR(
        listOf(
            CodigoPermissao.EMOJI,
            CodigoPermissao.FIGURINHA,
            CodigoPermissao.IMAGEM,
            CodigoPermissao.VIDEO,
            CodigoPermissao.AUDIO,
            CodigoPermissao.ARQUIVO,
        ),
    ),
    MENSAGENS(
        listOf(
            CodigoPermissao.RESPONDER,
            CodigoPermissao.ENCAMINHAR,
            CodigoPermissao.REAGIR,
            CodigoPermissao.AGENDAR,
            CodigoPermissao.CONFIRMACAO,
            CodigoPermissao.OCULTAR,
        ),
    ),
    GRUPOS(listOf(CodigoPermissao.GRUPO, CodigoPermissao.POSTAGEM, CodigoPermissao.VOTACAO)),
    CHAMADAS(
        listOf(CodigoPermissao.CHAMADA_AUDIO, CodigoPermissao.CHAMADA_VIDEO, CodigoPermissao.COMPARTILHAR_TELA, CodigoPermissao.TELEFONIA),
    ),
    CONFIGURACOES(
        listOf(
            CodigoPermissao.NOME,
            CodigoPermissao.EMAIL,
            CodigoPermissao.AVATAR,
            CodigoPermissao.SENHA,
            CodigoPermissao.PRIVACIDADE,
            CodigoPermissao.CORES,
            CodigoPermissao.CONFIG_CHAMADAS,
        ),
    ),

    /** De uso, mas que o app ainda não conhece (o servidor criou depois). */
    OUTRAS(emptyList()),
}

/**
 * Como o web: a administração primeiro (as que não são de uso), depois as de uso nos grupos
 * delas; uma de uso que nenhum grupo conhece vai para "Outras". Grupos vazios não aparecem.
 */
fun gruposDePermissoes(permissoes: List<PermissaoSistema>): List<Pair<GrupoPermissao, List<PermissaoSistema>>> {
    val deUso = permissoes.filter { it.uso }.associateBy { it.codigo }
    val conhecidos = GrupoPermissao.entries.flatMap { it.codigos }.toSet()
    return buildList {
        add(GrupoPermissao.ADMINISTRACAO to permissoes.filterNot { it.uso })
        GrupoPermissao.entries.filter { it.codigos.isNotEmpty() }.forEach { grupo -> add(grupo to grupo.codigos.mapNotNull { deUso[it] }) }
        add(GrupoPermissao.OUTRAS to permissoes.filter { it.uso && it.codigo !in conhecidos })
    }.filter { it.second.isNotEmpty() }
}

/** Parâmetros do servidor (`GET /parametros`). A chave privada do Firebase nunca vem: só se está configurada. */
data class ParametrosSistema(
    val fcmProjetoId: String = "",
    val fcmEmail: String = "",
    val fcmChaveConfigurada: Boolean = false,
    val turnForcarRelay: Boolean = true,
    val transcritorUrl: String = "",
    val transcritorIdioma: String = "",
    val gravacaoDias: Int = 0,
    val s3Bucket: String = "",
)

/** `PATCH /parametros`: só o que mudou; nulo = não mexe. */
data class AlteracaoParametros(
    val fcmProjetoId: String? = null,
    val fcmEmail: String? = null,
    val fcmChave: String? = null,
    val turnForcarRelay: Boolean? = null,
    val transcritorUrl: String? = null,
    val transcritorIdioma: String? = null,
    val gravacaoDias: Int? = null,
)

/** Limite do "Guardar as gravações por (dias)": 0 = para sempre, até 100 anos. */
const val MAXIMO_DIAS_GRAVACAO = 36_500

/** O texto do campo de dias, se for um número aceito (0 a [MAXIMO_DIAS_GRAVACAO]). */
fun diasDeGravacao(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it in 0..MAXIMO_DIAS_GRAVACAO }

/**
 * Como o web: só os campos que mudaram vão para o servidor; a chave, só se foi digitada
 * (em branco mantém a atual). Nulo = nada mudou ("Salvar" fica desligado).
 */
fun alteracaoDosParametros(original: ParametrosSistema, editado: ParametrosSistema, novaChave: String): AlteracaoParametros? {
    fun <T> seMudou(antes: T, depois: T): T? = depois.takeIf { it != antes }
    val alteracao = AlteracaoParametros(
        fcmProjetoId = seMudou(original.fcmProjetoId, editado.fcmProjetoId),
        fcmEmail = seMudou(original.fcmEmail, editado.fcmEmail),
        fcmChave = novaChave.takeIf { it.isNotBlank() },
        turnForcarRelay = seMudou(original.turnForcarRelay, editado.turnForcarRelay),
        transcritorUrl = seMudou(original.transcritorUrl, editado.transcritorUrl),
        transcritorIdioma = seMudou(original.transcritorIdioma, editado.transcritorIdioma),
        gravacaoDias = seMudou(original.gravacaoDias, editado.gravacaoDias),
    )
    return alteracao.takeIf { it != AlteracaoParametros() }
}

/** [uso]: permissão de uso do app (enviar, chamar…); as outras são de administração (servidor `ef13c22`). */
data class PermissaoSistema(val codigo: String, val descricao: String, val uso: Boolean = false)

data class UsuarioAcessos(val id: Long, val nome: String, val login: String, val permissoes: Set<String>)

/** `GET /permissoes`: o que cada permissão libera, quem tem o quê, e se ninguém tem "Acessos" ainda. */
data class Acessos(val permissoes: List<PermissaoSistema>, val usuarios: List<UsuarioAcessos>, val modoAberto: Boolean)

/** "N de M": quantas das permissões da tela a pessoa tem. */
fun Acessos.quantasDe(usuario: UsuarioAcessos): Int = permissoes.count { it.codigo in usuario.permissoes }

/** O selo "Administração": tem alguma das que não são de uso. */
fun Acessos.temAdministracao(usuario: UsuarioAcessos): Boolean = permissoes.any { !it.uso && it.codigo in usuario.permissoes }

/** As próprias, marcadas, ficam travadas: o servidor não deixa retirar (responde 400). */
fun UsuarioAcessos.propriaMarcada(eu: Long, codigo: String): Boolean = id == eu && codigo in permissoes

/** "Buscar usuário": por nome ou login, sem diferenciar maiúsculas. */
fun filtrarUsuarios(usuarios: List<UsuarioAcessos>, termo: String): List<UsuarioAcessos> {
    val t = termo.trim()
    if (t.isEmpty()) return usuarios
    return usuarios.filter { it.nome.contains(t, ignoreCase = true) || it.login.contains(t, ignoreCase = true) }
}

/** Depois de o servidor aceitar: a caixa marcada (ou não). A primeira de "Acessos" encerra o modo aberto. */
fun Acessos.comPermissao(usuarioId: Long, codigo: String, concedida: Boolean): Acessos = copy(
    usuarios = usuarios.map { u ->
        if (u.id != usuarioId) u else u.copy(permissoes = if (concedida) u.permissoes + codigo else u.permissoes - codigo)
    },
    modoAberto = modoAberto && !(concedida && codigo == CodigoPermissao.PERMISSOES),
)

/** O que o servidor tem ligado (`GET /recursos`, GER-08): o transcritor e a IA. */
data class Recursos(val transcricao: Boolean, val ia: Boolean)
