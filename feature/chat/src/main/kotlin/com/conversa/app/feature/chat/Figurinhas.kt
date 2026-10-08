package com.conversa.app.feature.chat

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.conversa.app.core.model.PACOTES_FIGURINHAS
import com.conversa.app.core.model.caminhoFigurinha
import com.conversa.app.core.model.nomeFigurinha
import com.conversa.app.core.ui.tema.ConversaTema

/**
 * Figurinha animada (ENV-04, TODO 7.8), como o `FigurinhaLottie.vue`: a animação Lottie dos
 * assets, em loop. Só é desenhada enquanto está na lista (a LazyColumn descarta o que sai da
 * tela). Com "Remover animações" ligado, fica parada no meio. Desconhecida ou com falha:
 * o quadro "Figurinha".
 */
@Composable
fun FigurinhaAnimada(id: String, tamanho: Dp, modifier: Modifier = Modifier) {
    val nome = nomeFigurinha(id) ?: stringResource(R.string.conteudo_figurinha)
    val caminho = caminhoFigurinha(id)
    val modificador = modifier.size(tamanho).semantics {
        contentDescription = nome
        role = Role.Image
    }
    if (caminho == null) {
        FigurinhaQueFalhou(modificador)
        return
    }
    val resultado = rememberLottieComposition(LottieCompositionSpec.Asset(caminho))
    val composicao = resultado.value
    if (resultado.isFailure) {
        FigurinhaQueFalhou(modificador)
        return
    }
    val parada = animacoesDesligadas()
    val progresso by animateLottieCompositionAsState(composicao, iterations = LottieConstants.IterateForever, isPlaying = !parada)
    LottieAnimation(composicao, progress = { if (parada) 0.5f else progresso }, modifier = modificador)
}

@Composable
private fun FigurinhaQueFalhou(modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(ConversaTema.cores.campoEntrada),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.conteudo_figurinha), style = MaterialTheme.typography.labelSmall)
    }
}

/** "Remover animações" do Android (escala de animação zero): o equivalente ao `prefers-reduced-motion` do web. */
@Composable
private fun animacoesDesligadas(): Boolean {
    val contexto = LocalContext.current
    return remember { Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * "Figurinhas" (no "+" do campo): uma aba por pacote e a grade. Tocar envia na hora, como o
 * web com o campo vazio (como resposta, se houver uma pendente).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorDeFigurinhas(aoEscolher: (String) -> Unit, aoFechar: () -> Unit) {
    var aba by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.figurinhas),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // Abas sobre o fundo da folha (o padrão é a cor da superfície, que destoa dela).
            PrimaryScrollableTabRow(selectedTabIndex = aba, edgePadding = 8.dp, containerColor = Color.Transparent) {
                PACOTES_FIGURINHAS.forEachIndexed { i, pacote ->
                    Tab(selected = aba == i, onClick = { aba = i }, text = { Text(pacote.nome) })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier.fillMaxWidth().height(320.dp).padding(8.dp),
            ) {
                items(PACOTES_FIGURINHAS[aba].figurinhas, key = { it.id }) { figurinha ->
                    Box(
                        Modifier
                            .padding(4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel = stringResource(R.string.enviar)) { aoEscolher(figurinha.id) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FigurinhaAnimada(figurinha.id, 64.dp)
                    }
                }
            }
        }
    }
}
