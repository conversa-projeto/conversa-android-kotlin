package com.conversa.app.core.model

/**
 * O que a pessoa pode fazer no chat (GER-09, servidor `ef13c22`), como o `auth.pode` do web:
 * [codigos] nulo enquanto não carregou, e aí tudo pode (o servidor confere de qualquer jeito e
 * responde 403 "Você não tem permissão para: …"). No modo aberto, o servidor manda todas.
 */
data class PermissoesDeUso(val codigos: Set<String>? = null) {
    fun pode(codigo: String): Boolean = codigos?.contains(codigo) ?: true

    /** Algum tipo de anexo (o "Documento" do "+" abre qualquer arquivo, como o web). */
    val algumAnexo: Boolean
        get() = listOf(CodigoPermissao.IMAGEM, CodigoPermissao.VIDEO, CodigoPermissao.AUDIO, CodigoPermissao.ARQUIVO).any(::pode)
}

/**
 * A permissão que um anexo pede, como o servidor confere no `PUT /mensagem`: imagem, áudio,
 * vídeo (arquivo com extensão de vídeo, ou mime de vídeo) ou arquivo.
 */
fun codigoDoAnexo(tipo: TipoConteudo, nome: String, mime: String?): String = when {
    tipo == TipoConteudo.IMAGEM -> CodigoPermissao.IMAGEM
    tipo == TipoConteudo.AUDIO || tipo == TipoConteudo.GRAVACAO_AUDIO -> CodigoPermissao.AUDIO
    tipo == TipoConteudo.FIGURINHA -> CodigoPermissao.FIGURINHA
    mime?.startsWith("video/") == true || ehNomeDeVideo(nome) -> CodigoPermissao.VIDEO
    else -> CodigoPermissao.ARQUIVO
}

/** Os códigos que faltam para [anexos] (tipo, nome, mime), sem repetir, na ordem do web. */
fun anexosBarrados(permissoes: PermissoesDeUso, anexos: List<Triple<TipoConteudo, String, String?>>): List<String> {
    val faltam = anexos.map { (tipo, nome, mime) -> codigoDoAnexo(tipo, nome, mime) }.filterNot(permissoes::pode).toSet()
    return listOf(CodigoPermissao.IMAGEM, CodigoPermissao.VIDEO, CodigoPermissao.AUDIO, CodigoPermissao.ARQUIVO).filter { it in faltam }
}
