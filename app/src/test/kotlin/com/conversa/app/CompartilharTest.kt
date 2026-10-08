package com.conversa.app

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Compartilhar" de outro app (4.9): o que é lido de cada intent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompartilharTest {
    @Test
    fun `texto, um arquivo e varios arquivos`() {
        val texto = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "olha isso https://exemplo.test")
        assertThat(lerCompartilhamento(texto)).isEqualTo(CompartilhamentoRecebido("olha isso https://exemplo.test", emptyList()))

        val um = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://outro.app/1"))
        assertThat(lerCompartilhamento(um)).isEqualTo(CompartilhamentoRecebido(null, listOf("content://outro.app/1")))

        val varios = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(
                Intent.EXTRA_STREAM,
                arrayListOf(Uri.parse("content://outro.app/1"), Uri.parse("content://outro.app/2")),
            )
            .putExtra(Intent.EXTRA_TEXT, "dois")
        assertThat(
            lerCompartilhamento(varios),
        ).isEqualTo(CompartilhamentoRecebido("dois", listOf("content://outro.app/1", "content://outro.app/2")))
    }

    @Test
    fun `outro intent ou compartilhamento vazio nao conta`() {
        assertThat(lerCompartilhamento(Intent(Intent.ACTION_MAIN))).isNull()
        assertThat(lerCompartilhamento(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "   "))).isNull()
    }
}
