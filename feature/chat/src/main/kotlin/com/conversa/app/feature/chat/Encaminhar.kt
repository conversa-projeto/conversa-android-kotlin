package com.conversa.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.conversa.app.core.model.Contato
import com.conversa.app.core.model.Conversa
import com.conversa.app.core.model.Mensagem
import com.conversa.app.core.model.TipoConversa
import com.conversa.app.core.model.resumoDaMensagem
import com.conversa.app.core.ui.tema.ConversaTema

/** Para onde encaminhar (7.6, `ForwardMessageModal.vue`): uma conversa ou um contato ainda sem direta. */
sealed interface DestinoEncaminhar {
    val chave: String

    /** Conversa existente; [grupo] decide o título padrão e a descrição. */
    data class ParaConversa(val conversaId: Long, val nome: String?, val grupo: Boolean) : DestinoEncaminhar {
        override val chave = "conversa-$conversaId"
    }

    /** Contato sem conversa direta: a direta é criada antes de enviar. */
    data class ParaContato(val contatoId: Long, val nome: String, val email: String?) : DestinoEncaminhar {
        override val chave = "contato-$contatoId"
    }
}

/**
 * Como o web: as conversas (menos a de origem) e os contatos que ainda não têm conversa
 * direta comigo, nessa ordem.
 */
fun destinosParaEncaminhar(conversas: List<Conversa>, contatos: List<Contato>, origem: Long): List<DestinoEncaminhar> {
    val comDireta = conversas.filter { it.tipo == TipoConversa.DIRETA }.mapNotNullTo(mutableSetOf()) { it.destinatarioId }
    val paraConversas = conversas.filter { it.id != origem }.map { conversa ->
        val grupo = conversa.tipo == TipoConversa.GRUPO
        val nome = if (grupo) conversa.descricao else conversa.nome ?: conversa.descricao
        DestinoEncaminhar.ParaConversa(conversa.id, nome?.takeIf { it.isNotBlank() }, grupo)
    }
    val paraContatos = contatos.filter { it.id !in comDireta }.map { DestinoEncaminhar.ParaContato(it.id, it.nome, it.email) }
    return paraConversas + paraContatos
}

/** Busca do web: o termo no título ou na descrição (para contato, o e-mail), sem diferenciar maiúsculas. */
fun filtrarDestinos(destinos: List<DestinoEncaminhar>, termo: String): List<DestinoEncaminhar> {
    val busca = termo.trim().lowercase()
    if (busca.isEmpty()) return destinos
    return destinos.filter { destino ->
        val texto = when (destino) {
            is DestinoEncaminhar.ParaConversa -> destino.nome.orEmpty()
            is DestinoEncaminhar.ParaContato -> "${destino.nome} ${destino.email.orEmpty()}"
        }
        busca in texto.lowercase()
    }
}

/** "Encaminhar mensagem" (textos do web): a mensagem escolhida, a busca e os destinos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncaminharMensagem(
    mensagem: Mensagem,
    destinos: List<DestinoEncaminhar>,
    aoEscolher: (DestinoEncaminhar) -> Unit,
    aoFechar: () -> Unit,
) {
    // Texto do campo em estado local (síncrono), como pede o CLAUDE.md.
    var busca by rememberSaveable { mutableStateOf("") }
    val filtrados = remember(destinos, busca) { filtrarDestinos(destinos, busca) }
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.encaminhar_mensagem), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.encaminhar_descricao),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .background(ConversaTema.cores.campoEntrada, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(R.string.mensagem_selecionada),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    textoDoResumo(resumoDaMensagem(mensagem)),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                label = { Text(stringResource(R.string.buscar_destino)) },
                placeholder = { Text(stringResource(R.string.nome_da_conversa_ou_contato)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (filtrados.isEmpty()) {
                Text(
                    stringResource(R.string.nenhum_destino),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp, bottom = 16.dp)) {
                items(filtrados, key = { it.chave }) { destino ->
                    LinhaDestino(destino, aoEscolher)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LinhaDestino(destino: DestinoEncaminhar, aoEscolher: (DestinoEncaminhar) -> Unit) {
    val (titulo, descricao, etiqueta) = when (destino) {
        is DestinoEncaminhar.ParaConversa -> Triple(
            destino.nome ?: stringResource(if (destino.grupo) R.string.grupo_sem_nome else R.string.conversa_direta),
            stringResource(if (destino.grupo) R.string.grupo_existente else R.string.conversa_direta_existente),
            stringResource(R.string.etiqueta_conversa),
        )
        is DestinoEncaminhar.ParaContato -> Triple(
            destino.nome,
            destino.email?.takeIf { it.isNotBlank() } ?: stringResource(R.string.novo_chat_direto),
            stringResource(R.string.etiqueta_contato),
        )
    }
    Row(
        Modifier.fillMaxWidth().clickable { aoEscolher(destino) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                descricao,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Conversa em azul e contato em verde, como o web.
        val corEtiqueta = when (destino) {
            is DestinoEncaminhar.ParaConversa -> MaterialTheme.colorScheme.primary
            is DestinoEncaminhar.ParaContato -> ConversaTema.cores.chamadaRecebida
        }
        Surface(shape = RoundedCornerShape(50), color = ConversaTema.cores.campoEntrada) {
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = corEtiqueta,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
