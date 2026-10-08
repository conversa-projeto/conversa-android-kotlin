package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Catálogo de figurinhas (igual ao `utils/figurinhas.ts` do web). */
class FigurinhasTest {
    @Test
    fun `os tres pacotes do web, 8 + 7 + 9`() {
        assertThat(
            PACOTES_FIGURINHAS.map {
                it.id to it.figurinhas.size
            },
        ).containsExactly("basico" to 8, "rostos" to 7, "coisas" to 9).inOrder()
    }

    @Test
    fun `nome e caminho so para o que esta no catalogo`() {
        assertThat(nomeFigurinha("coisas/coracao-partido")).isEqualTo("Coração partido")
        assertThat(caminhoFigurinha("rostos/legal")).isEqualTo("figurinhas/rostos/legal.json")
        assertThat(nomeFigurinha("pacote/novo")).isNull()
        assertThat(caminhoFigurinha("../../shared_prefs/sessao")).isNull()
    }

    @Test
    fun `toda figurinha do catalogo tem a animacao nos assets`() {
        val assets = File("../../feature/chat/src/main/assets").takeIf { it.isDirectory } ?: return
        val faltando = PACOTES_FIGURINHAS.flatMap { it.figurinhas }.filterNot { File(assets, caminhoFigurinha(it.id)!!).isFile }
        assertThat(faltando).isEmpty()
    }
}
