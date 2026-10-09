package com.conversa.app.core.ui.tema

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** As mesmas películas da barra de navegação que o `enableEdgeToEdge()` usa por padrão. */
private val PeliculaClara = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
private val PeliculaEscura = Color.argb(0x80, 0x1B, 0x1B, 0x1B)

/**
 * Ícones das barras de status e de navegação na cor do tema **do app** (8.5): com "Escuro"
 * escolhido num aparelho claro, o padrão deixaria ícones escuros sobre o fundo escuro.
 */
@Composable
fun BarrasDoSistema(atividade: ComponentActivity, escuro: Boolean) {
    LaunchedEffect(atividade, escuro) {
        atividade.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { escuro },
            navigationBarStyle = SystemBarStyle.auto(PeliculaClara, PeliculaEscura) { escuro },
        )
    }
}
