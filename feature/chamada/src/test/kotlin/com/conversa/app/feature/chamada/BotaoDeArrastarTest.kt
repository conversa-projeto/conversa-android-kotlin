package com.conversa.app.feature.chamada

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Atender/recusar só arrastando, para qualquer lado; tocar não faz nada (não atende no bolso). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BotaoDeArrastarTest {
    @get:Rule val regra = createComposeRule()

    private var concluiu = 0

    @Before
    fun montar() {
        regra.setContent {
            BotaoDeArrastar(Icons.Filled.Call, "Atender", Color.Green, aoConcluir = { concluiu++ })
        }
    }

    private fun arrastar(dx: Float, dy: Float) {
        regra.onNodeWithContentDescription("Atender").performTouchInput {
            swipe(center, center + Offset(dx, dy), durationMillis = 300)
        }
        regra.waitForIdle()
    }

    private fun px(dp: Float) = with(regra.density) { dp.dp.toPx() }

    @Test
    fun `tocar nao faz nada`() {
        regra.onNodeWithContentDescription("Atender").performTouchInput { click() }
        regra.waitForIdle()

        assertThat(concluiu).isEqualTo(0)
    }

    @Test
    fun `arrastar pouco nao faz nada`() {
        arrastar(px(40f), 0f)

        assertThat(concluiu).isEqualTo(0)
    }

    @Test
    fun `arrastar alem do limiar age - para cima, para o lado e na diagonal`() {
        val longe = px(LIMIAR_ARRASTO.value + 30f)
        arrastar(0f, -longe)
        assertThat(concluiu).isEqualTo(1)

        // Depois de concluir, volta a valer (a tela pode não ter mudado).
        regra.mainClock.advanceTimeBy(3_000)
        arrastar(-longe, 0f)
        assertThat(concluiu).isEqualTo(2)

        regra.mainClock.advanceTimeBy(3_000)
        arrastar(longe * 0.75f, longe * 0.75f)
        assertThat(concluiu).isEqualTo(3)
    }

    @Test
    fun `um arrasto longo age uma vez so`() {
        arrastar(px(LIMIAR_ARRASTO.value * 3), 0f)

        assertThat(concluiu).isEqualTo(1)
    }

    @Test
    fun `no TalkBack o toque duplo age`() {
        regra.onNodeWithContentDescription("Atender").performSemanticsAction(SemanticsActions.OnClick)

        assertThat(concluiu).isEqualTo(1)
    }
}
