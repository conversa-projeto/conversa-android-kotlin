package com.conversa.app.feature.atividades

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.Atividade
import com.conversa.app.core.model.DiaAtividade
import com.conversa.app.core.model.PreviaAtividade
import com.conversa.app.core.model.TipoAtividade
import com.conversa.app.core.model.TipoChamada
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.model.agruparAtividades
import com.conversa.app.core.model.ondeFoi
import com.conversa.app.core.model.previa
import com.conversa.app.core.model.textoDoSelo
import com.conversa.app.core.ui.componentes.Avatar
import com.conversa.app.core.ui.componentes.Carregando
import com.conversa.app.core.ui.componentes.EstadoErro
import com.conversa.app.core.ui.componentes.EstadoVazio
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Quantos itens antes do fim a próxima página começa a vir (o web: 200 px do fim). */
private const val ANTECEDENCIA_PAGINA = 5

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Aba Atividades (8.1, FC-800, ATV-01/02), como o `AtividadesPage.vue`: agrupada por dia, cada
 * item com o avatar de quem fez e o selo do tipo, "**autor** descrição", "em <grupo>", a prévia,
 * a hora e "Nova". Aberta (em primeiro plano), relê e marca como vistas; o toque leva à
 * mensagem ou à conversa.
 */
@Composable
fun AtividadesRotaTela(
    aoAbrirMensagem: (conversaId: Long, mensagemId: Long) -> Unit,
    aoAbrirConversa: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AtividadesViewModel = hiltViewModel(),
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.abrir()
        onPauseOrDispose { viewModel.fechar() }
    }
    AtividadesTela(
        estado = estado,
        aoAbrir = { atividade ->
            val conversa = atividade.conversaId ?: return@AtividadesTela
            val mensagem = atividade.mensagemId
            if (mensagem != null && mensagem > 0) aoAbrirMensagem(conversa, mensagem) else aoAbrirConversa(conversa)
        },
        aoTentarDeNovo = viewModel::abrir,
        aoChegarPertoDoFim = viewModel::carregarMais,
        modifier = modifier,
    )
}

@Composable
internal fun AtividadesTela(
    estado: AtividadesUiState,
    aoAbrir: (Atividade) -> Unit,
    aoTentarDeNovo: () -> Unit,
    aoChegarPertoDoFim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.atividades),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            estado.carregando -> Carregando(texto = stringResource(R.string.carregando))
            estado.atividades.isEmpty() && estado.erro != null -> EstadoErro(estado.erro, aoTentarDeNovo = aoTentarDeNovo)
            estado.atividades.isEmpty() -> EstadoVazio(
                titulo = stringResource(R.string.nenhuma_atividade),
                descricao = stringResource(R.string.nenhuma_atividade_texto),
                icone = Icons.Outlined.Notifications,
            )
            else -> ListaAtividades(estado, aoAbrir, aoChegarPertoDoFim)
        }
    }
}

@Composable
private fun ListaAtividades(estado: AtividadesUiState, aoAbrir: (Atividade) -> Unit, aoChegarPertoDoFim: () -> Unit) {
    val grupos = remember(estado.atividades) { agruparAtividades(estado.atividades, Instant.now()) }
    val lista = rememberLazyListState()
    val pertoDoFim by remember {
        derivedStateOf {
            val ultimoVisivel = lista.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimoVisivel >= lista.layoutInfo.totalItemsCount - ANTECEDENCIA_PAGINA
        }
    }
    LaunchedEffect(pertoDoFim, estado.atividades.size) { if (pertoDoFim) aoChegarPertoDoFim() }
    LazyColumn(state = lista, modifier = Modifier.fillMaxSize()) {
        grupos.forEach { (dia, itens) ->
            stickyHeader(key = "dia-$dia") { TituloDoDia(dia) }
            items(itens, key = { it.id }) { LinhaAtividade(it, aoAbrir = { aoAbrir(it) }) }
        }
        if (estado.carregandoMais) {
            item(key = "mais") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun TituloDoDia(dia: DiaAtividade) {
    Text(
        when (dia) {
            DiaAtividade.Hoje -> stringResource(R.string.hoje)
            DiaAtividade.Ontem -> stringResource(R.string.ontem)
            is DiaAtividade.Data -> dia.texto
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun LinhaAtividade(atividade: Atividade, aoAbrir: () -> Unit) {
    val primaria = MaterialTheme.colorScheme.primary
    val rotuloNova = stringResource(R.string.nova)
    Row(
        Modifier
            .fillMaxWidth()
            // Nova: fundo levemente azul (a primária com transparência), como o web.
            .background(if (atividade.nova) primaria.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .clickable(enabled = atividade.conversaId != null, onClick = aoAbrir)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Avatar(atividade.autorNome, atividade.autorAvatarUrl, tamanho = 40.dp)
            Selo(atividade, Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp))
        }
        Column(Modifier.weight(1f)) {
            val descricao = descricaoDa(atividade)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(atividade.autorNome.orEmpty()) }
                    append(" ")
                    append(descricao)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            atividade.ondeFoi()?.let {
                Text(
                    stringResource(R.string.em_grupo, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = ConversaTema.cores.textoTerciario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            textoDaPrevia(atividade.previa())?.let {
                Text(
                    stringResource(R.string.atividade_previa_entre_aspas, it),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            atividade.criadoEm?.let {
                Text(
                    FORMATO_HORA.format(it.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = ConversaTema.cores.textoTerciario,
                )
            }
            if (atividade.nova) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(primaria).semantics { contentDescription = rotuloNova })
            }
        }
    }
}

/** O selo no canto do avatar: o emoji (reação) sobre a superfície; ↩ e @ na primária; ✆ no vermelho. */
@Composable
private fun Selo(atividade: Atividade, modifier: Modifier = Modifier) {
    val cores = ConversaTema.cores
    val (fundo, texto) = when (atividade.tipo) {
        TipoAtividade.RESPOSTA, TipoAtividade.MENCAO -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        TipoAtividade.CHAMADA_PERDIDA -> cores.chamadaEncerrar to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            .background(fundo),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            atividade.textoDoSelo(),
            color = texto,
            fontSize = 10.sp,
            fontWeight = if (atividade.tipo == TipoAtividade.MENCAO) FontWeight.Bold else null,
        )
    }
}

@Composable
private fun descricaoDa(atividade: Atividade): String = when (atividade.tipo) {
    TipoAtividade.REACAO -> stringResource(R.string.reagiu, atividade.emoji.orEmpty())
    TipoAtividade.RESPOSTA -> stringResource(R.string.respondeu)
    TipoAtividade.MENCAO -> stringResource(R.string.mencionou)
    TipoAtividade.CHAMADA_PERDIDA ->
        stringResource(if (atividade.chamadaTipo == TipoChamada.VIDEO) R.string.ligou_video_perdida else R.string.ligou_perdida)
    TipoAtividade.DESCONHECIDO -> ""
}

@Composable
private fun textoDaPrevia(previa: PreviaAtividade?): String? = when (previa) {
    null -> null
    is PreviaAtividade.Texto -> previa.texto
    is PreviaAtividade.Tipo -> when (previa.tipo) {
        TipoConteudo.IMAGEM -> stringResource(R.string.atividade_previa_imagem)
        TipoConteudo.AUDIO, TipoConteudo.GRAVACAO_AUDIO -> stringResource(R.string.atividade_previa_audio)
        TipoConteudo.FIGURINHA -> stringResource(R.string.atividade_previa_figurinha)
        TipoConteudo.ENQUETE -> stringResource(R.string.atividade_previa_votacao)
        else -> stringResource(R.string.atividade_previa_arquivo)
    }
}
