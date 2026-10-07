package com.conversa.app.core.ui.componentes

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * Diálogo de confirmação padrão (GER-03). Na variante [perigo] o botão de
 * confirmar fica vermelho e o foco começa em "Cancelar", como no web.
 */
@Composable
fun DialogoConfirmacao(
    titulo: String,
    mensagem: String,
    aoConfirmar: () -> Unit,
    aoCancelar: () -> Unit,
    textoConfirmar: String = stringResource(R.string.ok),
    textoCancelar: String = stringResource(R.string.cancelar),
    perigo: Boolean = false,
) {
    val focoCancelar = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(titulo) },
        text = { Text(mensagem) },
        confirmButton = {
            TextButton(
                onClick = aoConfirmar,
                colors = if (perigo) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(textoConfirmar) }
        },
        dismissButton = {
            TextButton(onClick = aoCancelar, modifier = Modifier.focusRequester(focoCancelar)) { Text(textoCancelar) }
        },
    )
    if (perigo) {
        LaunchedEffect(Unit) { runCatching { focoCancelar.requestFocus() } }
    }
}

/** Só um aviso, com "OK". */
@Composable
fun DialogoAviso(titulo: String, mensagem: String, aoFechar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = { Text(mensagem) },
        confirmButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.ok)) } },
    )
}

@Preview
@Composable
private fun DialogoConfirmacaoPreview() {
    ConversaTema {
        DialogoConfirmacao(
            titulo = "Ocultar mensagem",
            mensagem = "Ela continua na conversa, marcada como oculta.",
            textoConfirmar = "Ocultar",
            perigo = true,
            aoConfirmar = {},
            aoCancelar = {},
        )
    }
}
