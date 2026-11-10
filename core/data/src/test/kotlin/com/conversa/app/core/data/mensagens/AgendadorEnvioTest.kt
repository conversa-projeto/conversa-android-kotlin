package com.conversa.app.core.data.mensagens

import android.net.NetworkCapabilities
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** O envio roda em rede sem a internet confirmada (o Wi-Fi interno do trabalho, 2026-11-10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgendadorEnvioTest {
    @Test
    fun `pede rede conectada, aceita VPN e nao pede a internet confirmada`() {
        val rede = AgendadorEnvioWorkManager.restricoes().requiredNetworkRequest!!

        assertThat(rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue()
        assertThat(rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).isFalse()
        assertThat(rede.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)).isFalse()
    }
}
