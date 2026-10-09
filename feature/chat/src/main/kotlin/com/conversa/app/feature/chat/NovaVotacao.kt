package com.conversa.app.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.LIMITE_OPCAO_ENQUETE
import com.conversa.app.core.model.LIMITE_PERGUNTA_ENQUETE
import com.conversa.app.core.model.MAXIMO_OPCOES_ENQUETE
import com.conversa.app.core.model.MINIMO_OPCOES_ENQUETE
import com.conversa.app.core.model.podeCriarEnquete
import com.conversa.app.core.model.sugestaoPrazoEnquete
import com.conversa.app.core.model.validarPrazoEnquete
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * "Nova votação" (ENV-22, FC-517), como o `EnqueteModal.vue`: pergunta (até 300), 2 a 12 opções
 * (até 200 cada), "Permitir várias escolhas" e, 🆕 `785bdef`, "Definir data final" (sugestão:
 * amanhã, na próxima hora cheia). "Criar votação" só com pergunta, 2 opções preenchidas e data
 * válida; o erro do servidor aparece na folha. [aoCriar] devolve o erro, ou nulo se deu certo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovaVotacao(
    aoCriar: suspend (pergunta: String, opcoes: List<String>, multipla: Boolean, encerraEm: Instant?) -> String?,
    aoFechar: () -> Unit,
) {
    val zona = ZoneId.systemDefault()
    var pergunta by rememberSaveable { mutableStateOf("") }
    // Opções: lista observável que sobrevive a girar a tela.
    val opcoes = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf("", "")
    }
    var multipla by rememberSaveable { mutableStateOf(false) }
    var comPrazo by rememberSaveable { mutableStateOf(false) }
    var encerraEm by rememberSaveable { mutableStateOf(sugestaoPrazoEnquete(Instant.now(), zona)) }
    var criando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()
    val falhaCriar = stringResource(R.string.criar_votacao_falhou)
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    val erroPrazo = if (comPrazo) validarPrazoEnquete(encerraEm, Instant.now(), zona) else null
    val valida = podeCriarEnquete(pergunta, opcoes, erroPrazo)

    ModalBottomSheet(onDismissRequest = aoFechar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.nova_votacao), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = pergunta,
                onValueChange = { pergunta = it.take(LIMITE_PERGUNTA_ENQUETE) },
                label = { Text(stringResource(R.string.pergunta)) },
                placeholder = { Text(stringResource(R.string.pergunta_exemplo)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
            )
            Text(stringResource(R.string.opcoes), style = MaterialTheme.typography.labelLarge)
            opcoes.forEachIndexed { indice, texto ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { novo -> opcoes[indice] = novo.take(LIMITE_OPCAO_ENQUETE) },
                        placeholder = { Text(stringResource(R.string.opcao_n, indice + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (opcoes.size > MINIMO_OPCOES_ENQUETE) {
                        IconButton(onClick = { opcoes.removeAt(indice) }) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.remover_opcao_n, indice + 1))
                        }
                    }
                }
            }
            if (opcoes.size < MAXIMO_OPCOES_ENQUETE) {
                TextButton(onClick = { opcoes.add("") }) { Text(stringResource(R.string.adicionar_opcao)) }
            }
            LinhaMarcar(
                marcado = multipla,
                titulo = stringResource(R.string.permitir_varias),
                explicacao = stringResource(R.string.permitir_varias_texto),
            ) { multipla = it }
            LinhaMarcar(
                marcado = comPrazo,
                titulo = stringResource(R.string.definir_data_final),
                explicacao = stringResource(R.string.definir_data_final_texto),
            ) { comPrazo = it }
            if (comPrazo) {
                Column(Modifier.padding(start = 48.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CamposDataHora(encerraEm) { encerraEm = it }
                    erroPrazo?.let {
                        Text(
                            stringResource(it.texto()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
                Button(
                    enabled = valida && !criando,
                    onClick = {
                        criando = true
                        erro = null
                        escopo.launch {
                            val falha =
                                aoCriar(pergunta, opcoes.toList(), multipla, if (comPrazo) encerraEm.atZone(zona).toInstant() else null)
                            criando = false
                            if (falha == null) aoFechar() else erro = falha.ifBlank { falhaCriar }
                        }
                    },
                ) { Text(stringResource(if (criando) R.string.criando else R.string.criar_votacao)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Caixa de marcar com título e explicação (a linha inteira é tocável). */
@Composable
private fun LinhaMarcar(marcado: Boolean, titulo: String, explicacao: String, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { aoMudar(!marcado) },
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = marcado, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.padding(top = 10.dp)) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium)
            Text(explicacao, style = MaterialTheme.typography.bodySmall, color = ConversaTema.cores.textoTerciario)
        }
    }
}
