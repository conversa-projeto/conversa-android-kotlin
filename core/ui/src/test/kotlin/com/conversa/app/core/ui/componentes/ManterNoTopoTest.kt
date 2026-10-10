package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A conversa que recebe mensagem sobe para o topo e tem de aparecer (lista maior que a tela). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManterNoTopoTest {
    @get:Rule val regra = createComposeRule()

    private var itens by mutableStateOf((1..30).toList())
    private lateinit var lista: LazyListState

    /**
     * [viaParametro]: a lista chega pela recomposição, como no app (o estado vem do ViewModel);
     * sem ele, o LazyColumn lê o estado direto e pode medir a lista nova antes da composição.
     */
    private fun montar(manter: Boolean, viaParametro: Boolean = false) {
        regra.setContent {
            lista = rememberLazyListState()
            if (viaParametro) Lista(itens, manter) else Lista(manter)
        }
    }

    @Composable
    private fun Lista(manter: Boolean) {
        if (manter) ManterNoTopo(lista, itens.firstOrNull())
        LazyColumn(state = lista, modifier = Modifier.height(300.dp)) {
            items(itens, key = { it }) { Text("Item $it", Modifier.height(50.dp)) }
        }
    }

    @Composable
    private fun Lista(atuais: List<Int>, manter: Boolean) {
        if (manter) ManterNoTopo(lista, atuais.firstOrNull())
        LazyColumn(state = lista, modifier = Modifier.height(300.dp)) {
            items(atuais, key = { it }) { Text("Item $it", Modifier.height(50.dp)) }
        }
    }

    /** Uma mensagem nova em [item]: ele vai para o topo. */
    private fun subir(item: Int) {
        itens = listOf(item) + (itens - item)
        regra.waitForIdle()
    }

    @Test
    fun `sem o ajuste, o LazyColumn esconde acima do topo o item que subiu`() {
        montar(manter = false)

        subir(15)

        regra.onNodeWithText("Item 15").assertIsNotDisplayed()
        regra.onNodeWithText("Item 1").assertIsDisplayed()
    }

    @Test
    fun `no topo, o item que subiu aparece em primeiro`() {
        montar(manter = true)

        subir(15)
        regra.onNodeWithText("Item 15").assertIsDisplayed()
        assertThat(lista.firstVisibleItemIndex).isEqualTo(0)

        subir(22)
        regra.onNodeWithText("Item 22").assertIsDisplayed()
        regra.onNodeWithText("Item 15").assertIsDisplayed()
    }

    @Test
    fun `com a lista vinda do estado da tela, o item que subiu tambem aparece`() {
        montar(manter = true, viaParametro = true)

        subir(15)
        regra.onNodeWithText("Item 15").assertIsDisplayed()
        assertThat(lista.firstVisibleItemIndex).isEqualTo(0)
    }

    @Test
    fun `sem o ajuste, com a lista vinda do estado da tela, o item tambem some`() {
        montar(manter = false, viaParametro = true)

        subir(15)

        regra.onNodeWithText("Item 15").assertIsNotDisplayed()
    }

    @Test
    fun `quem rolou para baixo continua onde estava`() {
        montar(manter = true)
        regra.runOnIdle { runBlocking { lista.scrollToItem(10) } }
        regra.waitForIdle()

        subir(25)

        regra.onNodeWithText("Item 11").assertIsDisplayed()
        regra.onNodeWithText("Item 25").assertIsNotDisplayed()
    }
}
