package com.conversa.app.core.ui.componentes

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Mantém a lista no topo quando outro item passa a ser o primeiro, por exemplo a conversa
 * que recebeu mensagem e subiu. Sem isso, o `LazyColumn` mantém na tela, pela chave, o item
 * que já estava em primeiro, e o que subiu fica escondido acima dele.
 *
 * Só age se a lista estava no topo: quem rolou para baixo continua onde estava.
 * [primeiraChave] é a chave (a mesma do `items(key = …)`) do primeiro item da lista.
 *
 * A posição é lida sem observação (não recompõe a cada rolagem) e só quando o primeiro item
 * muda: daí o `@Suppress` do aviso do lint.
 */
@Suppress("FrequentlyChangingValue")
@Composable
fun ManterNoTopo(lista: LazyListState, primeiraChave: Any?) {
    val anterior = remember { UltimaChave() }
    val antiga = anterior.valor
    if (antiga == primeiraChave) return
    anterior.valor = primeiraChave
    if (antiga == null) return
    // Estava no topo se quem era o primeiro continua sendo o primeiro visível, sem deslocamento.
    // Vale antes e depois de o LazyColumn medir a lista nova (depois, ele já está mais abaixo).
    val estavaNoTopo = Snapshot.withoutReadObservation {
        lista.firstVisibleItemScrollOffset == 0 && lista.layoutInfo.visibleItemsInfo.firstOrNull()?.key == antiga
    }
    if (estavaNoTopo) lista.requestScrollToItem(0)
}

private class UltimaChave(var valor: Any? = null)
