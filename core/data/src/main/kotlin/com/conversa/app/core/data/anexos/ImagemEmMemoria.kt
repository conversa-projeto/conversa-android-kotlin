package com.conversa.app.core.data.anexos

import java.io.ByteArrayInputStream

/** Uma imagem já preparada (JPEG em memória: a foto de perfil, a imagem do grupo), como arquivo para o envio de anexos. */
internal class ImagemEmMemoria(private val jpeg: ByteArray) : FonteArquivo {
    override val nome = "avatar.jpg"
    override val tamanho = jpeg.size.toLong()
    override val mime = "image/jpeg"

    override fun abrir() = ByteArrayInputStream(jpeg)
}
