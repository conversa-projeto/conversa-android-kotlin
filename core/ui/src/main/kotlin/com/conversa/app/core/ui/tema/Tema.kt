package com.conversa.app.core.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Tema do app. As cores seguem o cliente desktop (docs/design/cores.md).
 * O FMX só tem tema claro; o escuro é proposta e fica desligado por padrão.
 * Sem `dynamicColor`: a identidade vem do Conversa, não do papel de parede.
 */
@Composable
fun ConversaTema(
    escuro: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    val esquema = if (escuro) EsquemaEscuro else EsquemaClaro
    val cores = if (escuro) ConversaCoresEscuro else ConversaCoresClaro
    CompositionLocalProvider(LocalConversaCores provides cores) {
        MaterialTheme(colorScheme = esquema, typography = Typography(), shapes = Formas, content = conteudo)
    }
}

/** Atalho: `ConversaTema.cores.bolhaPropria`. */
object ConversaTema {
    val cores: ConversaCores
        @Composable
        @ReadOnlyComposable
        get() = LocalConversaCores.current
}
