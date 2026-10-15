package com.conversa.app.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.ConfirmacaoLeitura
import com.conversa.app.core.model.MembroConversa
import com.conversa.app.core.model.ResumoConfirmacao
import com.conversa.app.core.model.faltaConfirmar
import com.conversa.app.core.model.filtrarMembros
import com.conversa.app.core.model.resumoConfirmacao
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.tema.ConversaTema

// Confirmação de leitura (FC-521, FC-525; web `MessageBubble.vue` e `ConfirmacaoLeituraModal.vue`).

/**
 * O chip embaixo da bolha. Quem está na lista vê "Confirmar leitura" (a caixa; o toque confirma)
 * ou "Leitura confirmada". Quem enviou, e quem não está na lista, vê "Aguardando confirmação" /
 * "Confirmada por N de T". O toque (ou o toque longo, na caixa) abre quem confirmou e quem falta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConfirmacaoNaBolha(
    confirmacao: ConfirmacaoLeitura,
    aoConfirmar: () -> Unit,
    aoVerDetalhe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(50)
    val comDetalhe = confirmacao.usuarios.isNotEmpty() || faltaConfirmar(confirmacao).isNotEmpty()
    val rotuloDetalhe = stringResource(R.string.ver_confirmacoes)
    if (confirmacao.podeConfirmar || confirmacao.confirmou) {
        val confirmou = confirmacao.confirmou
        val rotulo = stringResource(if (confirmou) R.string.leitura_confirmada else R.string.confirmar_leitura)
        Surface(
            shape = forma,
            color = if (confirmou) MaterialTheme.colorScheme.primaryContainer else ConversaTema.cores.campoEntrada,
            border = if (confirmou) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = modifier
                .clip(forma)
                .combinedClickable(
                    role = Role.Checkbox,
                    onClick = { if (confirmou) aoVerDetalhe() else aoConfirmar() },
                    onLongClickLabel = rotuloDetalhe.takeIf { comDetalhe },
                    onLongClick = aoVerDetalhe.takeIf { comDetalhe },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = rotulo
                    stateDescription = rotulo
                },
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CaixaDeConfirmar(confirmou)
                Text(rotulo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    } else {
        Surface(
            shape = forma,
            color = ConversaTema.cores.campoEntrada,
            modifier = modifier
                .clip(forma)
                .clickable(enabled = comDetalhe, onClickLabel = rotuloDetalhe, onClick = aoVerDetalhe),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    textoDoResumo(resumoConfirmacao(confirmacao)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** A caixa desenhada (como no web: a nativa some quando a cor principal é escura). */
@Composable
private fun CaixaDeConfirmar(marcada: Boolean) {
    val forma = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(14.dp)
            .clip(forma)
            .background(if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, forma),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
private fun textoDoResumo(resumo: ResumoConfirmacao): String = when (resumo) {
    ResumoConfirmacao.Aguardando -> stringResource(R.string.aguardando_confirmacao)
    is ResumoConfirmacao.PorDe -> stringResource(R.string.confirmada_por_de, resumo.quantos, resumo.total)
    is ResumoConfirmacao.Por -> stringResource(R.string.confirmada_por, resumo.quantos)
}

/** Quem confirmou (foto, nome e hora) e "Falta confirmar" (o que no web aparece ao passar o mouse). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetalheConfirmacao(confirmacao: ConfirmacaoLeitura, aoFechar: () -> Unit) {
    val falta = faltaConfirmar(confirmacao)
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            stringResource(R.string.confirmacao_de_leitura),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            textoDoResumo(resumoConfirmacao(confirmacao)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.heightIn(max = 480.dp).padding(bottom = 16.dp)) {
            items(confirmacao.usuarios, key = { "c${it.usuarioId}" }) { usuario ->
                ListItem(
                    leadingContent = { Avatar(usuario.nome, usuario.avatarUrl, tamanho = 40.dp) },
                    headlineContent = { Text(usuario.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = usuario.confirmadaEm?.let { { Text(horaDaReacao(it)) } },
                    trailingContent = {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            if (falta.isNotEmpty()) {
                item(key = "falta") {
                    Text(
                        stringResource(R.string.falta_confirmar),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = ConversaTema.cores.textoTerciario,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                items(falta, key = { "f${it.usuarioId}" }) { destinatario ->
                    ListItem(
                        leadingContent = { Avatar(destinatario.nome, null, tamanho = 40.dp) },
                        headlineContent = { Text(destinatario.nome, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

/**
 * "Quem precisa confirmar" (grupo): filtro pelo nome, "Todos (N)" e um a um. Começa com a escolha
 * anterior ou com todos. Todos marcados devolve nulo (como o web): quem entrar no grupo antes do
 * envio também entra na lista.
 */
@Composable
internal fun QuemPrecisaConfirmar(
    membros: List<MembroConversa>,
    inicial: List<Long>?,
    textoConfirmar: String,
    aoEscolher: (List<Long>?) -> Unit,
    aoFechar: () -> Unit,
) {
    var escolhidos by remember { mutableStateOf(inicial?.toSet()) }
    var filtro by remember { mutableStateOf("") }
    val ids = membros.map { it.usuarioId }
    val marcados = escolhidos ?: ids.toSet()
    val todos = membros.isNotEmpty() && ids.all { it in marcados }
    val filtrados = filtrarMembros(membros, filtro)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.quem_precisa_confirmar)) },
        text = {
            Column {
                OutlinedTextField(
                    value = filtro,
                    onValueChange = { filtro = it },
                    placeholder = { Text(stringResource(R.string.filtrar_por_nome)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LinhaMarcar(
                    marcada = todos,
                    texto = stringResource(R.string.todos_n, membros.size),
                    negrito = true,
                    aoAlternar = { escolhidos = if (todos) emptySet() else ids.toSet() },
                )
                HorizontalDivider()
                when {
                    membros.isEmpty() -> Aviso(stringResource(R.string.carregando_participantes))
                    filtrados.isEmpty() -> Aviso(stringResource(R.string.ninguem_com, filtro.trim()))
                    else -> LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtrados, key = { it.usuarioId }) { membro ->
                            LinhaMarcar(
                                marcada = membro.usuarioId in marcados,
                                texto = membro.nome,
                                avatar = { Avatar(membro.nome, membro.avatarUrl, tamanho = 24.dp) },
                                aoAlternar = {
                                    escolhidos =
                                        if (membro.usuarioId in marcados) marcados - membro.usuarioId else marcados + membro.usuarioId
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = marcados.isNotEmpty(), onClick = { aoEscolher(if (todos) null else ids.filter { it in marcados }) }) {
                Text(textoConfirmar)
            }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun LinhaMarcar(
    marcada: Boolean,
    texto: String,
    aoAlternar: () -> Unit,
    negrito: Boolean = false,
    avatar: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = marcada, role = Role.Checkbox, onValueChange = { aoAlternar() }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = marcada, onCheckedChange = null)
        avatar?.invoke()
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (negrito) FontWeight.Medium else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

/** A faixa do campo: "A mensagem vai pedir confirmação de leitura" (em grupo, "de todos" / "de N pessoas" e "Escolher"). */
@Composable
internal fun textoDaFaixaConfirmacao(grupo: Boolean, usuarios: List<Long>?): String = when {
    !grupo -> stringResource(R.string.confirmacao_faixa)
    usuarios == null -> stringResource(R.string.confirmacao_faixa_todos)
    else -> pluralStringResource(R.plurals.confirmacao_faixa_pessoas, usuarios.size, usuarios.size)
}
