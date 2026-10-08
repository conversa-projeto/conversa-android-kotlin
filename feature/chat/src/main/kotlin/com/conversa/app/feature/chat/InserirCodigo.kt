package com.conversa.app.feature.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.app.core.model.LINGUAGENS_CODIGO
import com.conversa.app.core.model.blocoDeCodigo
import com.conversa.app.core.model.substituirAtalhoAntesDoCursor
import com.conversa.app.core.model.textoLongo

/**
 * "Inserir código" (7.11), versão simples do `CodigoModal.vue`: a linguagem e o código em
 * monoespaçado; "Enviar" manda o bloco cercado. Vindo de um texto longo colado, chega
 * preenchido e "Cancelar" cola o texto como estava (era só uma sugestão).
 */
@Composable
internal fun InserirCodigo(codigoInicial: String, aoEnviar: (String) -> Unit, aoCancelar: () -> Unit) {
    val codigo = rememberTextFieldState(codigoInicial)
    // Com ``` dentro, o texto colado é Markdown (como o web); sem detecção automática de linguagem.
    var linguagem by rememberSaveable { mutableStateOf(if (codigoInicial.lines().any { it.startsWith("```") }) "markdown" else "texto") }
    var escolhendo by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoCancelar,
        title = { Text(stringResource(R.string.inserir_codigo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { escolhendo = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(linguagem, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = stringResource(R.string.linguagem))
                    }
                    DropdownMenu(expanded = escolhendo, onDismissRequest = { escolhendo = false }) {
                        LINGUAGENS_CODIGO.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = {
                                linguagem = it
                                escolhendo = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    state = codigo,
                    placeholder = { Text(stringResource(R.string.codigo_placeholder)) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 8, maxHeightInLines = 12),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = codigo.text.isNotBlank(),
                onClick = { aoEnviar(blocoDeCodigo(codigo.text.toString().trimEnd('\n'), linguagem)) },
            ) { Text(stringResource(R.string.enviar)) }
        },
        dismissButton = { TextButton(onClick = aoCancelar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/**
 * Campo de mensagem (7.11): texto colado com mais de 10 linhas não entra (vai para o
 * "Inserir código", [aoColarLongo]); espaço ou quebra logo depois de um atalho (":)",
 * "<3"…) troca o atalho pelo emoji, fora de código, como o web.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun transformacaoDoCampo(aoColarLongo: (String) -> Unit) = InputTransformation {
    if (changes.changeCount != 1) return@InputTransformation
    val faixa = changes.getRange(0)
    val inserido = asCharSequence().substring(faixa.min, faixa.max)
    if (inserido.length > 1 && textoLongo(inserido)) {
        revertAllChanges()
        aoColarLongo(inserido)
        return@InputTransformation
    }
    if (inserido.isEmpty() || !inserido.last().isWhitespace() || !selection.collapsed || selection.start != faixa.max) {
        return@InputTransformation
    }
    val troca = substituirAtalhoAntesDoCursor(asCharSequence().toString(), selection.start) ?: return@InputTransformation
    replace(0, length, troca.texto)
    selection = TextRange(troca.cursor)
}
