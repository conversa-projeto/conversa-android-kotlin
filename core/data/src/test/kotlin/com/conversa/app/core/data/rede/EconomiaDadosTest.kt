package com.conversa.app.core.data.rede

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Quando imagens e vídeos esperam o toque (FC-414). */
class EconomiaDadosTest {
    @Test
    fun `conexao lenta economiza em qualquer rede`() {
        assertThat(deveEconomizar(redeMedida = false, economiaDoAndroid = false, kbpsDescida = 50)).isTrue()
        assertThat(deveEconomizar(redeMedida = true, economiaDoAndroid = false, kbpsDescida = 149)).isTrue()
    }

    @Test
    fun `economia de dados do Android so vale em rede medida`() {
        assertThat(deveEconomizar(redeMedida = true, economiaDoAndroid = true, kbpsDescida = 30_000)).isTrue()
        assertThat(deveEconomizar(redeMedida = false, economiaDoAndroid = true, kbpsDescida = 30_000)).isFalse()
    }

    @Test
    fun `rede boa ou velocidade desconhecida nao economiza`() {
        assertThat(deveEconomizar(redeMedida = true, economiaDoAndroid = false, kbpsDescida = 30_000)).isFalse()
        // 0 = o Android não sabe a velocidade: não bloqueia (como o web sem a API de conexão).
        assertThat(deveEconomizar(redeMedida = false, economiaDoAndroid = false, kbpsDescida = 0)).isFalse()
    }
}
