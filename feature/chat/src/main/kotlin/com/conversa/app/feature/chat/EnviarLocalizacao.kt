package com.conversa.app.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.model.ENQUADRAMENTO_PADRAO
import com.conversa.app.core.model.Localizacao
import com.conversa.app.core.model.PontoMapa
import com.conversa.app.core.model.distanciaReta
import com.conversa.app.core.model.formatarDistancia
import com.conversa.app.core.model.formatarTempoTrajeto
import com.conversa.app.core.model.nomePonto
import com.conversa.app.core.ui.tema.ConversaTema

/** Zoom de quando o mapa vai para um ponto (o `centralizar` do web). */
private const val ZOOM_DO_PONTO = 16.0

/**
 * "Enviar localização" em tela cheia (ENV-30; web `LocalizacaoModal.vue`): Local ou Trajeto; cada
 * campo busca o endereço (Enter ou a lupa), aceita coordenadas e tem "Usar minha posição"; o toque
 * no mapa marca o campo ativo. Embaixo, a dica, o andamento ou o aviso; "Enviar" manda na hora,
 * numa mensagem só dela. ("Inserir na mensagem" depende do campo rico, FC-416.)
 */
@Composable
internal fun EnviarLocalizacao(viewModel: EnviarLocalizacaoViewModel, aoEnviar: (Localizacao) -> Unit, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val densidade = LocalDensity.current
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val mapa = remember { EstadoMapa(ENQUADRAMENTO_PADRAO) }
    var tamanhoMapa by remember { mutableStateOf(IntSize.Zero) }

    fun ajustar() {
        if (tamanhoMapa == IntSize.Zero) return
        val noMapa = viewModel.estado.value.noMapa
        mapa.enquadrar(with(densidade) { enquadramentoDe(noMapa, tamanhoMapa.width.toFloat(), tamanhoMapa.height.toFloat()) })
    }

    fun fechar() {
        viewModel.fechar()
        aoFechar()
    }

    // A permissão do aparelho é pedida na hora do toque em "Usar minha posição" (o campo fica guardado).
    var pedindoPara by rememberSaveable { mutableStateOf<Int?>(null) }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { respostas ->
        val indice = pedindoPara ?: 0
        pedindoPara = null
        if (respostas.values.any { it }) viewModel.usarMinhaPosicao(indice) else viewModel.posicaoNegada(indice)
    }
    fun minhaPosicao(indice: Int) {
        if (contexto.temLocalizacao()) {
            viewModel.usarMinhaPosicao(indice)
        } else {
            pedindoPara = indice
            permissao.launch(PERMISSOES_LOCALIZACAO)
        }
    }

    LaunchedEffect(viewModel) {
        // Abrindo do zero, já tenta a posição do aparelho se a permissão já foi dada; sem ela, a dica explica o resto.
        if (viewModel.abrir() && contexto.temLocalizacao()) viewModel.usarMinhaPosicao(0, silencioso = true)
        viewModel.mapa.collect { pedido ->
            when (pedido) {
                is PedidoMapa.Centralizar -> mapa.centralizar(pedido.ponto, ZOOM_DO_PONTO)
                PedidoMapa.Ajustar -> ajustar()
            }
        }
    }
    // O toque no mapa marca o campo ativo (e fecha o teclado); a mesma função sempre, para o gesto não reiniciar.
    val foco = LocalFocusManager.current
    val aoTocarMapa = remember(viewModel, foco) {
        { ponto: PontoMapa ->
            foco.clearFocus()
            viewModel.definirPonto(viewModel.estado.value.ativo, ponto, buscarNome = true)
        }
    }

    Dialog(onDismissRequest = ::fechar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.enviar_localizacao),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = ::fechar) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fechar)) }
                }
                Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ModoLocalizacao.entries.forEachIndexed { i, modo ->
                            SegmentedButton(
                                selected = estado.modo == modo,
                                onClick = { viewModel.trocarModo(modo) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = ModoLocalizacao.entries.size),
                            ) { Text(stringResource(if (modo == ModoLocalizacao.LOCAL) R.string.localizacao_local else R.string.trajeto)) }
                        }
                    }
                    estado.indices.forEach { indice ->
                        CampoDoPonto(
                            indice = indice,
                            estado = estado,
                            texto = viewModel.textos[indice],
                            aoFocar = { viewModel.ativar(indice) },
                            aoBuscar = { viewModel.buscar(indice) },
                            aoMinhaPosicao = { minhaPosicao(indice) },
                            aoEscolher = {
                                foco.clearFocus()
                                viewModel.escolherResultado(indice, it)
                            },
                            aoFecharResultados = viewModel::fecharResultados,
                        )
                    }
                    MapaDeTiles(
                        mapa,
                        estado.noMapa,
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .onSizeChanged { tamanho ->
                                val primeiro = tamanhoMapa == IntSize.Zero
                                tamanhoMapa = tamanho
                                // Girou a tela com pontos marcados: o mapa novo enquadra o que já estava.
                                if (primeiro) ajustar()
                            },
                        interativo = true,
                        aoTocar = aoTocarMapa,
                    )
                    LinhaDeStatus(estado)
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = ::fechar) { Text(stringResource(R.string.cancelar)) }
                    Button(
                        enabled = estado.pronta,
                        onClick = {
                            val localizacao = viewModel.estado.value.resultado() ?: return@Button
                            viewModel.fechar()
                            aoEnviar(localizacao)
                        },
                    ) { Text(stringResource(R.string.enviar)) }
                }
            }
        }
    }
}

/** Um campo: o marcador (•, A ou B), o texto, a lupa e "Usar minha posição"; os resultados da busca embaixo. */
@Composable
private fun CampoDoPonto(
    indice: Int,
    estado: EstadoEnviarLocalizacao,
    texto: TextFieldState,
    aoFocar: () -> Unit,
    aoBuscar: () -> Unit,
    aoMinhaPosicao: () -> Unit,
    aoEscolher: (PontoMapa) -> Unit,
    aoFecharResultados: () -> Unit,
) {
    val local = estado.modo == ModoLocalizacao.LOCAL
    val (letra, cor) = when {
        local -> "•" to MaterialTheme.colorScheme.primary
        indice == 0 -> "A" to ConversaTema.cores.chamadaAtender
        else -> "B" to ConversaTema.cores.chamadaEncerrar
    }
    val dica = when {
        local -> R.string.localizacao_campo_local
        indice == 0 -> R.string.localizacao_campo_origem
        else -> R.string.localizacao_campo_destino
    }
    Box {
        OutlinedTextField(
            state = texto,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) aoFocar() },
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text(stringResource(dica), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Box(Modifier.size(24.dp).clip(CircleShape).background(cor), contentAlignment = Alignment.Center) {
                    Text(letra, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            trailingIcon = {
                Row {
                    IconButton(onClick = aoBuscar, enabled = texto.text.isNotBlank()) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.localizacao_buscar))
                    }
                    IconButton(onClick = aoMinhaPosicao, enabled = estado.localizando == null) {
                        if (estado.localizando == indice) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.MyLocation, contentDescription = stringResource(R.string.localizacao_minha_posicao))
                        }
                    }
                }
            },
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { aoBuscar() },
            // Só o campo ativo (o que recebe o toque no mapa) fica com a borda da cor principal, com ou sem o foco.
            colors = if (estado.ativo == indice) {
                OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.primary)
            } else {
                OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.outline)
            },
        )
        DropdownMenu(
            expanded = estado.resultados.isNotEmpty() && estado.campoResultados == indice,
            onDismissRequest = aoFecharResultados,
            properties = PopupProperties(focusable = false),
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            estado.resultados.forEach { resultado ->
                DropdownMenuItem(
                    text = {
                        Text(
                            nomePonto(resultado),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = { aoEscolher(resultado) },
                )
            }
        }
    }
}

/** A linha embaixo do mapa (web): o aviso em vermelho, "Buscando…", "Calculando a rota…", a rota ou a dica. */
@Composable
private fun LinhaDeStatus(estado: EstadoEnviarLocalizacao) {
    val aviso = estado.aviso
    val origem = estado.pontos[0]
    val destino = estado.pontos[1]
    val texto = when {
        aviso != null -> textoDoAviso(aviso)
        estado.buscando -> stringResource(R.string.localizacao_buscando)
        estado.calculando -> stringResource(R.string.localizacao_calculando_rota)
        estado.modo == ModoLocalizacao.TRAJETO && origem != null && destino != null -> estado.rota?.let {
            stringResource(R.string.localizacao_rota_de_carro, formatarDistancia(it.distancia), formatarTempoTrajeto(it.duracao))
        } ?: stringResource(R.string.localizacao_sem_rota, formatarDistancia(distanciaReta(origem, destino)))
        estado.modo == ModoLocalizacao.LOCAL -> stringResource(R.string.localizacao_dica_local)
        estado.ativo == 0 -> stringResource(R.string.localizacao_dica_origem)
        else -> stringResource(R.string.localizacao_dica_destino)
    }
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = if (aviso != null) MaterialTheme.colorScheme.error else ConversaTema.cores.textoTerciario,
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
    )
}

@Composable
private fun textoDoAviso(aviso: AvisoLocalizacao): String = when (aviso) {
    AvisoLocalizacao.NadaEncontrado -> stringResource(R.string.localizacao_nada_encontrado)
    AvisoLocalizacao.BuscaFalhou -> stringResource(R.string.localizacao_busca_falhou)
    is AvisoLocalizacao.SemPosicao -> stringResource(
        R.string.localizacao_sem_posicao,
        stringResource(
            when (aviso.falha) {
                FalhaPosicao.NEGADA -> R.string.localizacao_negada
                FalhaPosicao.INDISPONIVEL -> R.string.localizacao_indisponivel
                FalhaPosicao.SEM_SENSOR -> R.string.localizacao_sem_sensor
            },
        ),
    )
}
