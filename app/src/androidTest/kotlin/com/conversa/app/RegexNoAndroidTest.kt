package com.conversa.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.SegmentoTexto
import com.conversa.app.core.model.contatoCombina
import com.conversa.app.core.model.ehSoEmoji
import com.conversa.app.core.model.normalizarBusca
import com.conversa.app.core.model.resumirTexto
import com.conversa.app.core.model.separarTexto
import com.conversa.app.core.model.temBlocoDeCodigo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * As regras de texto rodando no Android de verdade. O regex do Android é o do ICU,
 * diferente do da JVM dos testes unitários: um padrão aceito na JVM pode fechar o app
 * (aconteceu com `(?U)`). Este teste roda no emulador: `gradlew :app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class RegexNoAndroidTest {
    @Test
    fun regrasDeTextoFuncionamNoIcu() {
        assertThat(resumirTexto("oi  @[Ana](7)\n```kotlin\nval a = 1\n```\n  fim")).isEqualTo("oi @Ana Código (kotlin) fim")
        assertThat(temBlocoDeCodigo("```md\n# a\n```")).isTrue()
        // \w no ICU aceitaria "café" como linguagem; o app usa [A-Za-z0-9_] como o web.
        assertThat(temBlocoDeCodigo("```café\nx\n```")).isFalse()
        assertThat(separarTexto("veja https://a.b/c, @[Bruno](8)")).containsExactly(
            SegmentoTexto.Texto("veja "),
            SegmentoTexto.Link("https://a.b/c"),
            SegmentoTexto.Texto(", "),
            SegmentoTexto.Mencao("Bruno", 8),
        ).inOrder()
        assertThat(normalizarBusca("Família")).isEqualTo("familia")
        assertThat(contatoCombina(Contato(1, "Élcio", "elcio", "e@x.com", null), "elc")).isTrue()
        assertThat(ehSoEmoji("👍🏽 ❤️")).isTrue()
    }
}
