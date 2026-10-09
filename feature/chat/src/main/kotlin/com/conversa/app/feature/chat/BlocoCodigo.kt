package com.conversa.app.feature.chat

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conversa.app.core.model.SegmentoCodigo
import com.conversa.app.core.model.TipoToken
import com.conversa.app.core.model.destacarCodigo
import com.conversa.app.core.model.ehLinguagemMarkdown
import com.conversa.app.core.model.ehLinguagemMermaid
import com.conversa.app.core.ui.tema.ConversaTema
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import com.mikepenz.markdown.model.markdownAlertColors
import kotlinx.coroutines.delay
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

/** Altura do bloco recolhido (o `max-h-60` do web). */
private val ALTURA_RECOLHIDO = 240.dp

/** Quanto tempo o "Copiado!" fica (como o web). */
private const val COPIADO_MS = 2_000L

/**
 * Bloco de código na bolha (MSG-15, TODO 7.9), como o `MessageContent.vue`: cabeçalho com a
 * linguagem ("code" sem ela) e "Copiar" → "Copiado!"; o código com destaque de sintaxe, rolagem
 * horizontal e, acima de 240 dp, recolhido com "Expandir código" / "Recolher código".
 */
@Composable
internal fun BlocoCodigo(codigo: SegmentoCodigo.Codigo) {
    val contexto = LocalContext.current
    var copiado by remember { mutableStateOf(false) }
    var expandido by rememberSaveable(codigo.conteudo) { mutableStateOf(false) }
    var longo by remember(codigo.conteudo) { mutableStateOf(false) }
    LaunchedEffect(copiado) {
        if (copiado) {
            delay(COPIADO_MS)
            copiado = false
        }
    }
    val corFundo = MaterialTheme.colorScheme.surface
    val corCabecalho = ConversaTema.cores.campoEntrada
    val texto = textoDestacado(codigo)
    // ```md formatado e ```mermaid como diagrama, por padrão; "Código" mostra o texto cru (7.9, como o web).
    // Diagrama inválido fica como código, sem a alternância.
    val markdown = ehLinguagemMarkdown(codigo.linguagem)
    var mermaidFalhou by remember(codigo.conteudo) { mutableStateOf(false) }
    val mermaid = ehLinguagemMermaid(codigo.linguagem) && !mermaidFalhou
    var verCodigo by rememberSaveable(codigo.conteudo) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(corFundo)) {
        Row(
            Modifier.fillMaxWidth().background(corCabecalho).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                codigo.linguagem ?: "code",
                style = MaterialTheme.typography.labelSmall,
                color = ConversaTema.cores.textoTerciario,
                modifier = Modifier.weight(1f),
            )
            if (markdown || mermaid) AlternarVisual(verCodigo) { verCodigo = it }
            Text(
                stringResource(if (copiado) R.string.copiado else R.string.copiar),
                style = MaterialTheme.typography.labelSmall,
                color = if (copiado) ConversaTema.cores.chamadaAtender else ConversaTema.cores.iconeAcao,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        contexto.getSystemService(
                            ClipboardManager::class.java,
                        )?.setPrimaryClip(ClipData.newPlainText(null, codigo.conteudo))
                        copiado = true
                    }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Box {
            if (markdown && !verCodigo) {
                Markdown(
                    content = codigo.conteudo,
                    colors = coresDoMarkdown(),
                    typography = tipografiaDoMarkdown(),
                    components = componentesDoMarkdown(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .recolhivel(expandido, ALTURA_RECOLHIDO) { if (it) longo = true }
                        .padding(10.dp),
                )
            } else if (mermaid && !verCodigo) {
                DiagramaMermaid(
                    codigo.conteudo,
                    aoFalhar = { mermaidFalhou = true },
                    modifier = Modifier.recolhivel(expandido, ALTURA_RECOLHIDO) { if (it) longo = true }.padding(10.dp),
                )
            } else {
                Text(
                    texto,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    onTextLayout = { if (it.didOverflowHeight) longo = true },
                    modifier = Modifier
                        .then(if (expandido) Modifier else Modifier.heightIn(max = ALTURA_RECOLHIDO))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
            }
            if (longo && !expandido) {
                // O fim some num degradê, como no web.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, corFundo))),
                )
            }
        }
        if (longo) {
            Text(
                stringResource(if (expandido) R.string.recolher_codigo else R.string.expandir_codigo),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(corCabecalho)
                    .clickable { expandido = !expandido }
                    .padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * As cores do destaque vêm de tokens que já existem (`docs/design/cores.md` §6.4): o FMX não
 * tem destaque de sintaxe.
 */
@Composable
private fun textoDestacado(codigo: SegmentoCodigo.Codigo): AnnotatedString {
    val palavra = MaterialTheme.colorScheme.primary
    val cores = ConversaTema.cores
    return remember(codigo, palavra, cores) {
        val tokens = destacarCodigo(codigo.conteudo, codigo.linguagem)
        buildAnnotatedString {
            append(codigo.conteudo)
            for (token in tokens) {
                val estilo = when (token.tipo) {
                    TipoToken.PALAVRA_CHAVE, TipoToken.TAG -> SpanStyle(color = palavra)
                    TipoToken.TEXTO -> SpanStyle(color = cores.chamadaAtender)
                    TipoToken.COMENTARIO -> SpanStyle(color = cores.textoTerciario, fontStyle = FontStyle.Italic)
                    TipoToken.NUMERO -> SpanStyle(color = cores.chamadaEncerrar)
                    TipoToken.ATRIBUTO -> SpanStyle(color = cores.link)
                }
                addStyle(estilo, token.inicio, token.fim)
            }
        }
    }
}

/** "Visualizar" | "Código" no cabeçalho de um bloco ```md (o web mostra os dois lado a lado). */
@Composable
private fun AlternarVisual(verCodigo: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(4.dp)).background(ConversaTema.cores.divisorLista).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OpcaoAlternar(stringResource(R.string.visualizar), selecionada = !verCodigo) { aoMudar(false) }
        OpcaoAlternar(stringResource(R.string.codigo), selecionada = verCodigo) { aoMudar(true) }
    }
}

@Composable
private fun OpcaoAlternar(texto: String, selecionada: Boolean, aoTocar: () -> Unit) {
    Text(
        texto,
        style = MaterialTheme.typography.labelSmall,
        color = if (selecionada) MaterialTheme.colorScheme.onSurface else ConversaTema.cores.textoTerciario,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (selecionada) MaterialTheme.colorScheme.surface else Color.Transparent)
            .selectable(selected = selecionada, role = Role.Tab, onClick = aoTocar)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Altura limitada a [maximo] enquanto não está [expandido] (o conteúdo é medido inteiro e
 * cortado). [aoMedir] diz se passou do limite, para mostrar o degradê e "Expandir código".
 */
private fun Modifier.recolhivel(expandido: Boolean, maximo: Dp, aoMedir: (Boolean) -> Unit) = clipToBounds().layout { medivel, limites ->
    val medido = medivel.measure(limites.copy(maxHeight = Constraints.Infinity))
    val limite = maximo.roundToPx()
    aoMedir(medido.height > limite)
    val altura = if (expandido) medido.height else minOf(medido.height, limite)
    layout(medido.width, altura) { medido.placeRelative(0, 0) }
}

/** Cores do Markdown só com tokens (`docs/design/cores.md` §6.4); os alertas do GitHub também. */
@Composable
private fun coresDoMarkdown(): MarkdownColors {
    val texto = MaterialTheme.colorScheme.onSurface
    val cores = ConversaTema.cores
    return markdownColor(
        text = texto,
        codeBackground = cores.campoEntrada,
        dividerColor = cores.divisorLista,
        tableBackground = cores.campoEntrada,
        alert = markdownAlertColors(
            note = MaterialTheme.colorScheme.primary,
            tip = cores.chamadaAtender,
            important = cores.link,
            warning = cores.avisoConexao,
            caution = cores.chamadaEncerrar,
        ),
    )
}

/** Tamanhos de bolha, como o web (`CLASSES_MARKDOWN`: h1 em `text-lg`, h2 em `text-base`, texto em `text-sm`). */
@Composable
private fun tipografiaDoMarkdown(): MarkdownTypography {
    val tipos = MaterialTheme.typography
    val titulo = { estilo: TextStyle -> estilo.copy(fontWeight = FontWeight.SemiBold) }
    val texto = tipos.bodyMedium
    return markdownTypography(
        h1 = titulo(tipos.titleLarge),
        h2 = titulo(tipos.titleMedium),
        h3 = titulo(tipos.titleSmall),
        h4 = titulo(texto),
        h5 = titulo(texto),
        h6 = titulo(texto),
        text = texto,
        code = tipos.bodySmall.copy(fontFamily = FontFamily.Monospace),
        quote = texto.copy(fontStyle = FontStyle.Italic),
        paragraph = texto,
        ordered = texto,
        bullet = texto,
        list = texto,
        textLink = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
        table = texto,
        alertTitle = titulo(texto),
    )
}

/** Os blocos de código dentro do Markdown: ```mermaid vira diagrama (como o web); o resto fica como a biblioteca desenha. */
@Composable
private fun componentesDoMarkdown() = markdownComponents(
    checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
    codeFence = { modelo ->
        val linguagem = modelo.node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(modelo.content)?.toString()
        if (ehLinguagemMermaid(linguagem)) {
            MarkdownCodeFence(modelo.content, modelo.node, modelo.typography.code) { codigo, _, estilo -> DiagramaOuCodigo(codigo, estilo) }
        } else {
            MarkdownCodeFence(modelo.content, modelo.node, modelo.typography.code)
        }
    },
)

/** Diagrama dentro do Markdown; inválido fica como código. */
@Composable
private fun DiagramaOuCodigo(codigo: String, estilo: TextStyle) {
    var falhou by remember(codigo) { mutableStateOf(false) }
    if (falhou) {
        Text(
            codigo,
            style = estilo,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(ConversaTema.cores.campoEntrada)
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        )
    } else {
        DiagramaMermaid(codigo, aoFalhar = { falhou = true })
    }
}
