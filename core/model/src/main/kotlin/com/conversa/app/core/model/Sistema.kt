package com.conversa.app.core.model

// Sistema e Acessos (8.5, FC-808, CFG-07, CFG-08, AUT-09), como o `ConfiguracaoSistema.vue`
// e o `ConfiguracaoAcessos.vue` do web.

/** Códigos de permissão do servidor (contrato §13.1) e o nome que o web mostra. */
object CodigoPermissao {
    /** "Sistema": ver e alterar os parâmetros. */
    const val PARAMETROS = "parametros"

    /** "Acessos": conceder e retirar permissões. */
    const val PERMISSOES = "permissoes"
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

data class PermissaoSistema(val codigo: String, val descricao: String)

data class UsuarioAcessos(val id: Long, val nome: String, val login: String, val permissoes: Set<String>)

/** `GET /permissoes`: o que cada permissão libera, quem tem o quê, e se ninguém tem "Acessos" ainda. */
data class Acessos(val permissoes: List<PermissaoSistema>, val usuarios: List<UsuarioAcessos>, val modoAberto: Boolean)

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
