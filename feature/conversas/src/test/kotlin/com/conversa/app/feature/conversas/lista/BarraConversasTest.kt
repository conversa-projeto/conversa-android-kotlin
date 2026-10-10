package com.conversa.app.feature.conversas.lista

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A pesquisa só na barra de título: a lupa abre o campo; ← e o voltar fecham e limpam. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarraConversasTest {
    @get:Rule val regra = createAndroidComposeRule<ComponentActivity>()

    private val termos = mutableListOf<String>()
    private val emTodos = mutableListOf<String>()

    private fun montar(termoInicial: String = "") {
        regra.setContent {
            BarraConversas("Conversas", termoInicial, aoAlterar = { termos += it }, aoPesquisarEmTodos = { emTodos += it })
        }
    }

    private val campo get() = regra.onNode(hasSetTextAction())

    @Test
    fun `fechada - so o titulo e a lupa`() {
        montar()

        regra.onNodeWithText("Conversas").assertIsDisplayed()
        regra.onNodeWithContentDescription("Pesquisar").assertIsDisplayed()
        regra.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun `a lupa abre o campo focado, que filtra e pesquisa em todos os chats`() {
        montar()

        regra.onNodeWithContentDescription("Pesquisar").performClick()
        campo.assertIsFocused()
        campo.performTextInput("lista")
        regra.onNodeWithContentDescription("Pesquisar em todos os chats").performClick()

        assertThat(termos.last()).isEqualTo("lista")
        assertThat(emTodos).containsExactly("lista")
    }

    @Test
    fun `o x apaga o texto e o campo continua aberto`() {
        montar()
        regra.onNodeWithContentDescription("Pesquisar").performClick()
        campo.performTextInput("abc")

        regra.onNodeWithContentDescription("Limpar a pesquisa").performClick()

        assertThat(termos.last()).isEqualTo("")
        campo.assertIsDisplayed()
    }

    @Test
    fun `a seta fecha e limpa o filtro`() {
        montar()
        regra.onNodeWithContentDescription("Pesquisar").performClick()
        campo.performTextInput("abc")

        regra.onNodeWithContentDescription("Fechar pesquisa").performClick()
        regra.waitForIdle()

        assertThat(termos.last()).isEqualTo("")
        regra.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        regra.onNodeWithContentDescription("Pesquisar").assertIsDisplayed()
    }

    @Test
    fun `o voltar do sistema fecha a pesquisa antes de sair da tela`() {
        montar()
        regra.onNodeWithContentDescription("Pesquisar").performClick()
        campo.performTextInput("abc")

        regra.runOnUiThread { regra.activity.onBackPressedDispatcher.onBackPressed() }
        regra.waitForIdle()

        assertThat(termos.last()).isEqualTo("")
        regra.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        assertThat(regra.activity.isFinishing).isFalse()
    }

    @Test
    fun `com um termo guardado ja abre aberta e com o texto`() {
        montar(termoInicial = "grupo")

        campo.assertIsDisplayed()
        regra.onNodeWithText("grupo").assertIsDisplayed()
    }
}
