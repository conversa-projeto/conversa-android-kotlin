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
