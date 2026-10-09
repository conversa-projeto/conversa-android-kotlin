package com.conversa.app.core.model

// Perfil do usuário (8.3, FC-802, AUT-06/07/08), como o `ProfileSettingsModal.vue`.

/** Mínimo da nova senha (o web confere antes de mandar). */
const val MINIMO_SENHA = 6

/** Lado da foto de perfil, em pixels (o web reduz para 256×256). */
const val LADO_AVATAR = 256

/** Qualidade do JPEG da foto de perfil (o web usa 0,85). */
const val QUALIDADE_AVATAR = 85

enum class ErroSenha { CAMPOS_VAZIOS, CURTA, NAO_CONFERE }

/** As mesmas conferências do web, na mesma ordem; nulo = pode mandar. */
fun validarTrocaDeSenha(atual: String, nova: String, confirmacao: String): ErroSenha? = when {
    atual.isEmpty() || nova.isEmpty() || confirmacao.isEmpty() -> ErroSenha.CAMPOS_VAZIOS
    nova.length < MINIMO_SENHA -> ErroSenha.CURTA
    nova != confirmacao -> ErroSenha.NAO_CONFERE
    else -> null
}

/** "Preencha nome e email." quando falta um dos dois. */
fun dadosDoPerfilValidos(nome: String, email: String): Boolean = nome.isNotBlank() && email.isNotBlank()

/** O quadrado do meio da imagem (o recorte da foto de perfil): x, y e o lado, em pixels da original. */
data class Recorte(val x: Int, val y: Int, val lado: Int)

fun recorteQuadradoCentral(largura: Int, altura: Int): Recorte {
    val lado = minOf(largura, altura)
    return Recorte(x = (largura - lado) / 2, y = (altura - lado) / 2, lado = lado)
}

/** O perfil de outra pessoa (8.4, AUT-10), como o `UsuarioPopup` do web. E-mail e telefone vazios viram nulo. */
data class FichaUsuario(val id: Long, val nome: String, val email: String?, val telefone: String?, val avatarUrl: String?)

/**
 * Como o `resolverUsuarioDaConversa` do web: só na conversa direta. Os dados vêm do contato
 * (`GET /usuario/contatos`, que não traz foto: ela vem da conversa); sem o contato, da própria conversa.
 */
fun fichaDaConversa(conversa: Conversa, contatos: List<Contato>): FichaUsuario? {
    if (conversa.tipo != TipoConversa.DIRETA) return null
    val foto = conversa.avatarUrl?.takeIf { it.isNotBlank() }
    val contato = contatos.firstOrNull { it.id == conversa.destinatarioId }
        ?: return FichaUsuario(conversa.destinatarioId ?: conversa.id, conversa.titulo, null, null, foto)
    return FichaUsuario(
        id = contato.id,
        nome = contato.nome,
        email = contato.email?.takeIf { it.isNotBlank() },
        telefone = contato.telefone?.takeIf { it.isNotBlank() },
        avatarUrl = contato.avatarUrl?.takeIf { it.isNotBlank() } ?: foto,
    )
}
