package com.conversa.app

import android.net.Uri
import com.conversa.app.navegacao.RotaChat
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `conversa://chat/{id}?mensagem={id}` (TODO 2.5, CON-12). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkConversaTest {
    private fun ler(texto: String) = MainViewModel.rotaDoLink(Uri.parse(texto))

    @Test
    fun `link com conversa e mensagem`() {
        assertThat(ler("conversa://chat/42?mensagem=105")).isEqualTo(RotaChat(42, 105))
    }

    @Test
    fun `link so com a conversa`() {
        assertThat(ler("conversa://chat/42")).isEqualTo(RotaChat(42, 0))
    }

    @Test
    fun `links invalidos sao ignorados`() {
        assertThat(ler("conversa://chat/abc")).isNull()
        assertThat(ler("conversa://chat/0")).isNull()
        assertThat(ler("conversa://outra/42")).isNull()
        assertThat(ler("https://chat/42")).isNull()
        assertThat(MainViewModel.rotaDoLink(null)).isNull()
    }
}
