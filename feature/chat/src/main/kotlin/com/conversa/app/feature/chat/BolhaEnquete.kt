package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.Enquete
import com.conversa.app.core.model.ErroPrazo
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.OpcaoEnquete
import com.conversa.app.core.model.idDaEnquete
import com.conversa.app.core.model.momentoDoEncerramento
import com.conversa.app.core.model.porcentagemDaOpcao
import com.conversa.app.core.model.sugestaoPrazoEnquete
import com.conversa.app.core.model.validarPrazoEnquete
import com.conversa.app.core.model.vencedoras
import com.conversa.app.core.model.votosAoTocar
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * O que a bolha de votação pede (7.12): o [ChatViewModel] liga ao `EnquetesRepositorio`.
 * Em falha, a mensagem da exceção é a do servidor (ou o texto amigável do erro de rede).
 */
interface AcoesEnquete {
    fun observar(id: Long): Flow<Enquete?>

    suspend fun carregar(id: Long): Result<Unit>

    suspend fun votar(id: Long, opcoes: List<Long>): Result<Unit>

    suspend fun encerrar(id: Long): Result<Unit>

    suspend fun alterarPrazo(id: Long, encerraEm: Instant?): Result<Unit>
}

/** Agora, refeito na hora de [ate]: a data final que vence com a bolha aberta encerra na hora (como o web). */
@Composable
private fun agoraAte(ate: Instant?): Instant {
    val agora by produceState(Instant.now(), ate) {
        val falta = ate?.let { Duration.between(Instant.now(), it).toMillis() } ?: return@produceState
        if (falta > 0) delay(falta)
        value = maxOf(Instant.now(), ate)
    }
    return agora
}

/** A enquete da mensagem, lida do cache e pedida ao servidor ao aparecer. Falha → a mensagem do erro. */
@Composable
private fun enqueteDa(id: Long, acoes: AcoesEnquete): Pair<Enquete?, String?> {
    val enquete by remember(id) { acoes.observar(id) }.collectAsStateWithLifecycle(null)
    var erro by remember(id) { mutableStateOf<String?>(null) }
    val padrao = stringResource(R.string.votacao_carregar_falhou)
    LaunchedEffect(id) { acoes.carregar(id).onFailure { erro = it.message?.ifBlank { null } ?: padrao } }
    return enquete to erro
}

/**
 * Votação (MSG-20, FC-516), como o `BolhaEnquete.vue`: "📊 pergunta", "Escolha uma opção" (ou
 * "uma ou mais"; com a data final, "· encerra …"), cada opção com círculo ou quadrado, contagem,
 * barra e quem votou; "N pessoas votaram". Encerrada (à mão ou prazo vencido, inclusive com a
 * bolha aberta): "🔒 Votação encerrada …", sem votar e 🏆 na mais votada. Quem criou muda a data
 * final; quem criou a votação ou o grupo encerra.
 */
@Composable
internal fun BolhaEnquete(mensagem: Mensagem, propria: Boolean, acoes: AcoesBolha) {
    val cor = if (propria) ConversaTema.cores.textoBolhaPropria else ConversaTema.cores.textoBolhaOutro
    val id = mensagem.idDaEnquete()
    val enquetes = acoes.enquetes
    Column(Modifier.widthIn(min = 220.dp, max = 300.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (id == null || enquetes == null) {
            Text("📊 " + stringResource(R.string.votacao), color = cor, fontWeight = FontWeight.SemiBold)
        } else {
            val (enquete, erro) = enqueteDa(id, enquetes)
            when {
                enquete != null -> ConteudoEnquete(enquete, cor, enquetes)
                else -> Text(
                    erro ?: stringResource(R.string.carregando_votacao),
                    color = cor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.7f).padding(vertical = 8.dp),
                )
            }
        }
        Rodape(mensagem, propria, Modifier.align(Alignment.End))
    }
}

@Composable
private fun ConteudoEnquete(enquete: Enquete, cor: Color, acoes: AcoesEnquete) {
    val agora = agoraAte(enquete.encerraEm?.takeIf { !enquete.encerrada })
    val encerrada = enquete.fechada(agora)
    val vencedoras = enquete.vencedoras(agora)
    val escopo = rememberCoroutineScope()
    var votando by remember { mutableStateOf(false) }
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var editandoPrazo by rememberSaveable { mutableStateOf(false) }
    var confirmandoEncerrar by rememberSaveable { mutableStateOf(false) }
    val falhaVotar = stringResource(R.string.votar_falhou)
    val falhaPrazo = stringResource(R.string.prazo_falhou)
    val falhaEncerrar = stringResource(R.string.encerrar_falhou)
    val mensagemDe = { falha: Throwable, padrao: String -> falha.message?.ifBlank { null } ?: padrao }

    Text("📊 ${enquete.pergunta}", color = cor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    if (encerrada) {
        val momento = enquete.momentoDoEncerramento?.let { textoDoPrazo(it) }
        Text(
            if (momento == null) stringResource(R.string.votacao_encerrada) else stringResource(R.string.votacao_encerrada_em, momento),
            color = cor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    } else {
        val escolha = stringResource(if (enquete.multipla) R.string.escolha_uma_ou_mais else R.string.escolha_uma_opcao)
        val prazo = enquete.encerraEm?.let { textoDoPrazo(it) }
        Text(
            if (prazo == null) escolha else stringResource(R.string.escolha_e_prazo, escolha, prazo),
            color = cor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.alpha(0.7f).padding(bottom = 6.dp),
        )
    }
    enquete.opcoes.forEach { opcao ->
        LinhaOpcao(
            opcao = opcao,
            enquete = enquete,
            cor = cor,
            vencedora = opcao.id in vencedoras,
            habilitada = !votando && !encerrada,
            aoTocar = {
                votando = true
                erro = null
                escopo.launch {
                    acoes.votar(enquete.id, enquete.votosAoTocar(opcao.id)).onFailure { erro = mensagemDe(it, falhaVotar) }
                    votando = false
                }
            },
        )
    }
    erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
    Text(
        pluralStringResource(R.plurals.pessoas_votaram, enquete.totalVotantes, enquete.totalVotantes),
        color = cor,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.alpha(0.7f),
    )
    if (!encerrada && (enquete.podeAlterarPrazo || enquete.podeEncerrar)) {
        HorizontalDivider(Modifier.padding(top = 4.dp), color = cor.copy(alpha = 0.2f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (enquete.podeAlterarPrazo) {
                TextButton(onClick = {
                    erro = null
                    editandoPrazo = true
                }) {
                    Text(stringResource(if (enquete.encerraEm == null) R.string.definir_data_final else R.string.alterar_data_final))
                }
            }
            if (enquete.podeEncerrar) {
                TextButton(enabled = !salvando, onClick = { confirmandoEncerrar = true }) {
                    Text(stringResource(R.string.encerrar_votacao), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (editandoPrazo) {
        EditarPrazo(
            atual = enquete.encerraEm,
            salvando = salvando,
            aoSalvar = { prazo ->
                salvando = true
                erro = null
                escopo.launch {
                    acoes.alterarPrazo(enquete.id, prazo)
                        .onSuccess { editandoPrazo = false }
                        .onFailure {
                            editandoPrazo = false
                            erro = mensagemDe(it, falhaPrazo)
                        }
                    salvando = false
                }
            },
            aoFechar = { editandoPrazo = false },
        )
    }
    if (confirmandoEncerrar) {
        AlertDialog(
            onDismissRequest = { confirmandoEncerrar = false },
            title = { Text(stringResource(R.string.encerrar_votacao)) },
            text = { Text(stringResource(R.string.encerrar_votacao_texto)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoEncerrar = false
                    salvando = true
                    erro = null
                    escopo.launch {
                        acoes.encerrar(enquete.id).onFailure { erro = mensagemDe(it, falhaEncerrar) }
                        salvando = false
                    }
                }) { Text(stringResource(R.string.encerrar), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmandoEncerrar = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

/** Uma opção: círculo (única) ou quadrado (múltipla) com ✓, o texto (🏆 na vencedora), a contagem, a barra e quem votou. */
@Composable
private fun LinhaOpcao(opcao: OpcaoEnquete, enquete: Enquete, cor: Color, vencedora: Boolean, habilitada: Boolean, aoTocar: () -> Unit) {
    val marcada = opcao.id in enquete.meusVotos
    val votos = opcao.votantes.size
    val primaria = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .selectable(
                selected = marcada,
                enabled = habilitada,
                role = if (enquete.multipla) Role.Checkbox else Role.RadioButton,
                onClick = aoTocar,
            )
            .padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val forma = if (enquete.multipla) RoundedCornerShape(4.dp) else CircleShape
            Box(
                Modifier
                    .size(18.dp)
                    .clip(forma)
                    .border(2.dp, primaria, forma)
                    .background(if (marcada) primaria else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (marcada) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Text(
                (if (vencedora) "🏆 " else "") + opcao.texto,
                color = cor,
                fontWeight = if (vencedora) FontWeight.SemiBold else null,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text("$votos", color = cor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { porcentagemDaOpcao(votos, enquete.totalVotantes) / 100f },
            color = primaria,
            trackColor = primaria.copy(alpha = 0.2f),
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(50)),
        )
        if (votos > 0) {
            Text(
                opcao.votantes.joinToString(", ") { it.nome },
                color = cor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(0.7f).padding(top = 2.dp),
            )
        }
    }
}

/** "Data final" (quem criou): "Tirar data" (se tem), "Cancelar" e "Salvar"; com erro, "Salvar" desabilitado. */
@Composable
private fun EditarPrazo(atual: Instant?, salvando: Boolean, aoSalvar: (Instant?) -> Unit, aoFechar: () -> Unit) {
    val zona = ZoneId.systemDefault()
    var quando by rememberSaveable {
        mutableStateOf(atual?.let { LocalDateTime.ofInstant(it, zona) } ?: sugestaoPrazoEnquete(Instant.now(), zona))
    }
    val erro = validarPrazoEnquete(quando, Instant.now(), zona)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.data_final)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CamposDataHora(quando) { quando = it }
                erro?.let {
                    Text(stringResource(it.texto()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = erro == null && !salvando, onClick = { aoSalvar(quando.atZone(zona).toInstant()) }) {
                Text(stringResource(R.string.salvar))
            }
        },
        dismissButton = {
            Row {
                if (atual != null) {
                    TextButton(enabled = !salvando, onClick = { aoSalvar(null) }) {
                        Text(stringResource(R.string.tirar_data), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) }
            }
        },
    )
}

internal fun ErroPrazo.texto() = when (this) {
    ErroPrazo.SEM_DATA -> R.string.prazo_sem_data
    ErroPrazo.NO_PASSADO -> R.string.prazo_no_passado
    ErroPrazo.MUITO_LONGE -> R.string.prazo_muito_longe
}

/**
 * Votação só para leitura (`EnqueteResumo.vue`): na citação e na mensagem oculta revelada.
 * "📊 pergunta", "Votação encerrada" se for o caso e a contagem de cada opção.
 */
@Composable
internal fun ResumoEnquete(id: Long?, cor: Color, acoes: AcoesEnquete?) {
    if (id == null || acoes == null) {
        Text("📊 " + stringResource(R.string.votacao), color = cor, fontWeight = FontWeight.SemiBold)
        return
    }
    val (enquete, erro) = enqueteDa(id, acoes)
    Column(Modifier.widthIn(min = 180.dp)) {
        if (enquete == null) {
            Text(
                erro ?: stringResource(R.string.carregando_votacao),
                color = cor,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.7f),
            )
        } else {
            Text("📊 ${enquete.pergunta}", color = cor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            if (enquete.encerrada) {
                Text(
                    stringResource(R.string.votacao_encerrada_resumo),
                    color = cor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.alpha(0.7f),
                )
            }
            enquete.opcoes.forEach { opcao ->
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(opcao.texto, color = cor, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(
                        "${opcao.votantes.size}",
                        color = cor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
