package com.conversa.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.DiaPrazo
import com.conversa.app.core.model.ErroAgendamento
import com.conversa.app.core.model.EtapaEntrega
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.SecaoStatus
import com.conversa.app.core.model.StatusDestinatario
import com.conversa.app.core.model.etapasDaDireta
import com.conversa.app.core.model.horaDoStatus
import com.conversa.app.core.model.quandoPrazo
import com.conversa.app.core.model.resumoDaMensagem
import com.conversa.app.core.model.secoesDoGrupo
import com.conversa.app.core.model.sugestaoAgendamento
import com.conversa.app.core.model.validarAgendamento
import com.conversa.app.core.ui.tema.ConversaTema
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Toque no status de uma mensagem minha (7.10): a linha dá a ação, o rodapé da bolha a usa.
 * Nulo = sem detalhe (dos outros, ou ainda saindo).
 */
internal val LocalAoVerStatus = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * Detalhe do status (FC-513), como o `DetalheStatusMensagem.vue`: na direta, o horário de cada
 * etapa ("Aguardando" sem data); no grupo, quem viu, quem só recebeu e quem ainda não recebeu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetalheStatus(
    mensagem: Mensagem,
    grupo: Boolean,
    carregar: suspend (Long) -> Result<List<StatusDestinatario>>,
    aoFechar: () -> Unit,
) {
    var resultado by remember(mensagem.id) { mutableStateOf<Result<List<StatusDestinatario>>?>(null) }
    LaunchedEffect(mensagem.id) { resultado = carregar(mensagem.id) }
    val agora = Instant.now()
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val atual = resultado
            val destinatarios = atual?.getOrNull()
            when {
                atual == null -> TextoDiscreto(stringResource(R.string.status_carregando))
                destinatarios == null -> TextoDiscreto(stringResource(R.string.status_erro))
                !grupo -> etapasDaDireta(mensagem, destinatarios).forEach { (etapa, quando) ->
                    LinhaStatus(stringResource(etapa.rotulo()), quando?.let { horaDoStatus(it, agora) }, titulo = true)
                }
                else -> {
                    mensagem.excluidaEm?.let {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                stringResource(R.string.etapa_oculta),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(horaDoStatus(it, agora))
                        }
                    }
                    secoesDoGrupo(destinatarios).forEach { (secao, itens) ->
                        Text(
                            stringResource(secao.rotulo(), itens.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = ConversaTema.cores.textoTerciario,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        itens.forEach { (nome, quando) -> LinhaStatus(nome, quando?.let { horaDoStatus(it, agora) }, titulo = false) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextoDiscreto(texto: String) {
    Text(texto, color = ConversaTema.cores.textoTerciario, modifier = Modifier.padding(vertical = 8.dp))
}

/** Etapa (direta) ou pessoa (grupo) à esquerda, horário à direita; na etapa sem data, "Aguardando". */
@Composable
private fun LinhaStatus(rotulo: String, quando: String?, titulo: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(rotulo, color = if (titulo) ConversaTema.cores.textoTerciario else MaterialTheme.colorScheme.onSurface)
        when {
            quando != null -> Text(quando, color = if (titulo) MaterialTheme.colorScheme.onSurface else ConversaTema.cores.textoTerciario)
            titulo -> Text(stringResource(R.string.etapa_aguardando), color = ConversaTema.cores.textoTerciario)
        }
    }
}

private fun EtapaEntrega.rotulo() = when (this) {
    EtapaEntrega.ENVIADA -> R.string.etapa_enviada
    EtapaEntrega.RECEBIDA -> R.string.etapa_recebida
    EtapaEntrega.VISUALIZADA -> R.string.etapa_visualizada
    EtapaEntrega.OUVIDA -> R.string.etapa_ouvida
    EtapaEntrega.OCULTA -> R.string.etapa_oculta
}

private fun SecaoStatus.rotulo() = when (this) {
    SecaoStatus.VISUALIZADA_POR -> R.string.secao_visualizada_por
    SecaoStatus.RECEBIDA_POR -> R.string.secao_recebida_por
    SecaoStatus.AGUARDANDO -> R.string.secao_aguardando
}

/**
 * "Agendar mensagem" (FC-514), como o `AgendarMensagemModal.vue`: data e hora (sugestão: amanhã
 * 08:00), pelo menos 5 minutos no futuro e no máximo 1 ano; com erro, "Agendar" fica desabilitado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AgendarMensagem(aoConfirmar: (Instant) -> Unit, aoFechar: () -> Unit) {
    val zona = ZoneId.systemDefault()
    val sugestao = remember { sugestaoAgendamento(Instant.now(), zona) }
    var data by rememberSaveable { mutableStateOf(sugestao.toLocalDate()) }
    var hora by rememberSaveable { mutableStateOf(sugestao.toLocalTime()) }
    var escolhendoData by rememberSaveable { mutableStateOf(false) }
    var escolhendoHora by rememberSaveable { mutableStateOf(false) }
    val quando = LocalDateTime.of(data, hora)
    val erro = validarAgendamento(quando, Instant.now(), zona)
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(stringResource(R.string.agendar_mensagem)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoEscolha(stringResource(R.string.agendar_data), FORMATO_DATA.format(data)) { escolhendoData = true }
                CampoEscolha(stringResource(R.string.agendar_hora), FORMATO_HORA_CAMPO.format(hora)) { escolhendoHora = true }
                erro?.let {
                    Text(
                        stringResource(if (it == ErroAgendamento.MUITO_CEDO) R.string.agendar_muito_cedo else R.string.agendar_muito_longe),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = erro == null,
                onClick = {
                    // Confere de novo: o relógio andou com o diálogo aberto.
                    if (validarAgendamento(quando, Instant.now(), zona) == null) aoConfirmar(quando.atZone(zona).toInstant())
                },
            ) { Text(stringResource(R.string.agendar)) }
        },
        dismissButton = { TextButton(onClick = aoFechar) { Text(stringResource(R.string.cancelar)) } },
    )
    if (escolhendoData) {
        // O DatePicker trabalha com a meia-noite UTC do dia escolhido.
        val estado = rememberDatePickerState(initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { data = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    escolhendoData = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { escolhendoData = false }) { Text(stringResource(R.string.cancelar)) } },
        ) { DatePicker(estado) }
    }
    if (escolhendoHora) {
        val estado = rememberTimePickerState(initialHour = hora.hour, initialMinute = hora.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { escolhendoHora = false },
            text = { TimePicker(estado) },
            confirmButton = {
                TextButton(onClick = {
                    hora = LocalTime.of(estado.hour, estado.minute)
                    escolhendoHora = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { escolhendoHora = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
private fun CampoEscolha(rotulo: String, valor: String, aoTocar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = ConversaTema.cores.textoTerciario)
        OutlinedButton(onClick = aoTocar, modifier = Modifier.fillMaxWidth()) { Text(valor) }
    }
}

private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val FORMATO_HORA_CAMPO = DateTimeFormatter.ofPattern("HH:mm")

/** "hoje 18:00", "amanhã 08:30" ou "12/10 18:00" (o `formatarPrazo` do web): agendadas e data final da votação. */
@Composable
internal fun textoDoPrazo(instante: Instant): String {
    val (dia, quando) = quandoPrazo(instante, Instant.now())
    return when (dia) {
        DiaPrazo.HOJE -> stringResource(R.string.prazo_hoje, quando)
        DiaPrazo.AMANHA -> stringResource(R.string.prazo_amanha, quando)
        DiaPrazo.OUTRO -> quando
    }
}

/** Relógio ao lado do microfone, com o campo vazio: quantas agendadas há (🆕 web `7322e83`). */
@Composable
internal fun RelogioAgendadas(quantas: Int, aoAbrir: () -> Unit) {
    val descricao = pluralStringResource(R.plurals.mensagens_agendadas_quantas, quantas, quantas)
    IconButton(onClick = aoAbrir, modifier = Modifier.size(48.dp)) {
        BadgedBox(
            badge = {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clearAndSetSemantics {},
                ) { Text("$quantas") }
            },
        ) { Icon(Icons.Outlined.Schedule, contentDescription = descricao, tint = MaterialTheme.colorScheme.primary) }
    }
}

/**
 * "Mensagens agendadas" (🆕 web `7322e83`, `MensagensAgendadasModal.vue`): as minhas desta
 * conversa que ainda não saíram, com o horário, o resumo e "Cancelar" (com a confirmação de
 * sempre). Fecha sozinha quando a última sai (na hora ou cancelada).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MensagensAgendadas(agendadas: List<Mensagem>, aoCancelar: suspend (Mensagem) -> Boolean, aoFechar: () -> Unit) {
    if (agendadas.isEmpty()) {
        LaunchedEffect(Unit) { aoFechar() }
        return
    }
    var confirmando by remember { mutableStateOf<Mensagem?>(null) }
    var cancelando by remember { mutableStateOf<Long?>(null) }
    var erro by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Text(
            stringResource(R.string.mensagens_agendadas),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(agendadas, key = { it.id }) { mensagem ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp).size(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            mensagem.visivelEm?.let { textoDoPrazo(it) }.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            textoDoResumo(resumoDaMensagem(mensagem)),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val esta = cancelando == mensagem.id
                    TextButton(enabled = !esta, onClick = { confirmando = mensagem }) {
                        Text(stringResource(if (esta) R.string.cancelando else R.string.cancelar), color = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()
            }
        }
        if (erro) {
            Text(
                stringResource(R.string.cancelar_agendada_falhou),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
    confirmando?.let { mensagem ->
        ConfirmarOcultar(
            agendada = true,
            aoConfirmar = {
                confirmando = null
                cancelando = mensagem.id
                erro = false
                escopo.launch {
                    erro = !aoCancelar(mensagem)
                    cancelando = null
                }
            },
            aoCancelar = { confirmando = null },
        )
    }
}
