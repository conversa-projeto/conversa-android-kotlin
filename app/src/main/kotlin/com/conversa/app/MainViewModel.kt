package com.conversa.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.data.SessaoRepositorio
import com.conversa.app.navegacao.RotaInicio
import com.conversa.app.navegacao.RotaServidor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Decide a primeira tela: sem servidor → "Servidor"; senão → início (login entra na etapa 2). */
@HiltViewModel
class MainViewModel @Inject constructor(servidor: ServidorRepositorio, sessao: SessaoRepositorio) : ViewModel() {
    private val _destinoInicial = MutableStateFlow<Any?>(null)
    val destinoInicial: StateFlow<Any?> = _destinoInicial.asStateFlow()

    init {
        viewModelScope.launch {
            servidor.carregado.first { it }
            sessao.carregada.first { it }
            _destinoInicial.value = if (servidor.atual.value == null) RotaServidor(podeVoltar = false) else RotaInicio
        }
    }
}
