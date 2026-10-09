package com.conversa.app.core.ui.componentes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * O `Switch` do app. Desligado, o Material pinta o polegar e a borda com `outline` sobre o
 * trilho `surfaceContainerHighest`; nas cores do FMX os dois são `#C8C8C8` e o polegar some.
 * Aqui eles usam `onSurfaceVariant`. [aoMudar] nulo = a linha inteira é que alterna (`toggleable`).
 */
@Composable
fun Interruptor(ligado: Boolean, modifier: Modifier = Modifier, aoMudar: ((Boolean) -> Unit)? = null) {
    Switch(
        checked = ligado,
        onCheckedChange = aoMudar,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
