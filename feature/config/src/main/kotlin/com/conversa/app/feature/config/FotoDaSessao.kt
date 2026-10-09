package com.conversa.app.feature.config

import com.conversa.app.core.data.perfil.PerfilRepositorio
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Renovar a URL da foto no máximo a cada 30 s (como o `renovarAvatarExpirado` do web). */
private val INTERVALO_RENOVAR_FOTO: Duration = Duration.ofSeconds(30)

/**
 * A URL assinada da foto do usuário logado (8.3/8.5): acompanha o identificador da sessão e,
 * quando a imagem não carrega (URL vencida), [falhou] busca outra, no máximo a cada 30 s.
 */
internal class FotoDaSessao(private val perfil: PerfilRepositorio, private val relogio: Clock, private val escopo: CoroutineScope) {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()
    private var renovadaEm: Instant? = null

    init {
        escopo.launch {
            perfil.sessao.map { it?.avatarIdentificador }.distinctUntilChanged().collect { carregar(it) }
        }
    }

    private suspend fun carregar(identificador: String?) {
        _url.value = identificador?.let { perfil.urlDaFoto(it).getOrNull() }
    }

    fun falhou() {
        val identificador = perfil.sessao.value?.avatarIdentificador ?: return
        val agora = relogio.instant()
        val ultima = renovadaEm
        if (ultima != null && Duration.between(ultima, agora) < INTERVALO_RENOVAR_FOTO) return
        renovadaEm = agora
        perfil.esquecerUrl(identificador)
        escopo.launch { carregar(identificador) }
    }
}
