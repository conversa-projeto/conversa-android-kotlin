package com.conversa.app.core.model

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/** Catálogo de figurinhas (igual ao `utils/figurinhas.ts` do web). */
class FigurinhasTest {
    @Test
    fun `o seletor tem so o pacote Noto do web, 55, com o credito da licenca`() {
        assertThat(PACOTES_FIGURINHAS.map { it.id to it.figurinhas.size }).containsExactly("noto" to 55)
        assertThat(PACOTE_NOTO.credito?.texto).isEqualTo("Animated Noto Emoji © Google, CC BY 4.0")
        assertThat(PACOTE_NOTO.figurinhas.first()).isEqualTo(Figurinha("noto/chorando-de-rir", "Chorando de rir"))
    }

    @Test
    fun `os pacotes antigos continuam desenhando as mensagens que ja os usaram`() {
        assertThat(
            PACOTES_ANTIGOS.map {
                it.id to it.figurinhas.size
            },
        ).containsExactly("basico" to 8, "rostos" to 7, "coisas" to 9).inOrder()
        assertThat(nomeFigurinha("basico/coracao")).isEqualTo("Coração")
    }

    @Test
    fun `nome e caminho so para o que esta no catalogo`() {
        assertThat(nomeFigurinha("coisas/coracao-partido")).isEqualTo("Coração partido")
        assertThat(caminhoFigurinha("noto/arco-iris")).isEqualTo("figurinhas/noto/arco-iris.json")
        assertThat(nomeFigurinha("noto/arco-iris")).isEqualTo("Arco-íris")
        assertThat(caminhoFigurinha("rostos/legal")).isEqualTo("figurinhas/rostos/legal.json")
        assertThat(nomeFigurinha("pacote/novo")).isNull()
        assertThat(caminhoFigurinha("../../shared_prefs/sessao")).isNull()
    }

    @Test
    fun `toda figurinha do catalogo tem a animacao nos assets`() {
        val assets = File("../../feature/chat/src/main/assets").takeIf { it.isDirectory } ?: return
        val faltando = (PACOTES_FIGURINHAS + PACOTES_ANTIGOS).flatMap {
            it.figurinhas
        }.filterNot { File(assets, caminhoFigurinha(it.id)!!).isFile }
        assertThat(faltando).isEmpty()
    }
}
