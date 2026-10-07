package com.conversa.app.core.network.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerConfigTest {
    @Test
    fun `https sem porta gera wss na mesma origem com barra final`() {
        val config = ServerConfig.aPartirDe("https://x")!!
        assertThat(config.base.toString()).isEqualTo("https://x/")
        assertThat(config.api.toString()).isEqualTo("https://x/api/")
        assertThat(config.ws).isEqualTo("wss://x/ws/")
        assertThat(config.webrtc.toString()).isEqualTo("https://x/webrtc/")
    }

    @Test
    fun `http com porta gera ws com a mesma porta`() {
        val config = ServerConfig.aPartirDe("http://x:8080")!!
        assertThat(config.ws).isEqualTo("ws://x:8080/ws/")
        assertThat(config.api.toString()).isEqualTo("http://x:8080/api/")
    }

    @Test
    fun `sem esquema assume https`() {
        assertThat(ServerConfig.aPartirDe("192.168.2.5")!!.base.toString()).isEqualTo("https://192.168.2.5/")
    }

    @Test
    fun `remove o api do fim, query e fragmento`() {
        val config = ServerConfig.aPartirDe("  https://conversa.empresa.com/api/?a=1#x  ")!!
        assertThat(config.base.toString()).isEqualTo("https://conversa.empresa.com/")
    }

    @Test
    fun `mantem subcaminho`() {
        val config = ServerConfig.aPartirDe("https://empresa.com/conversa")!!
        assertThat(config.api.toString()).isEqualTo("https://empresa.com/conversa/api/")
        assertThat(config.ws).isEqualTo("wss://empresa.com/conversa/ws/")
    }

    @Test
    fun `porta padrao some e porta 443 explicita tambem`() {
        assertThat(ServerConfig.aPartirDe("https://x:443/")!!.ws).isEqualTo("wss://x/ws/")
    }

    @Test
    fun `rejeita enderecos invalidos`() {
        assertThat(ServerConfig.aPartirDe("")).isNull()
        assertThat(ServerConfig.aPartirDe("ftp://x")).isNull()
        assertThat(ServerConfig.aPartirDe("https://")).isNull()
        assertThat(ServerConfig.aPartirDe("com espaco")).isNull()
    }

    @Test
    fun `igualdade pela base`() {
        assertThat(ServerConfig.aPartirDe("x")).isEqualTo(ServerConfig.aPartirDe("https://x/api"))
    }
}
