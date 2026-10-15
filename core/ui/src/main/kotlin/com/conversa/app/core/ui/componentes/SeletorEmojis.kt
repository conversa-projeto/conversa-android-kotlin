package com.conversa.app.core.ui.componentes

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.conversa.app.core.model.CategoriaEmojis
import com.conversa.app.core.model.EMOJIS_POPULARES
import com.conversa.app.core.model.EmojiDoCatalogo
import com.conversa.app.core.model.pesquisarEmojis
import com.conversa.app.core.model.textoDeBuscaDoEmoji
import com.conversa.app.core.model.usarEmojiRecente
import com.conversa.app.core.ui.R
import com.conversa.app.core.ui.tema.ConversaTema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * O catálogo de emojis (`assets/emojis/pt.json`, o mesmo do web), lido uma vez por processo.
 * Fica de fora o que a fonte do aparelho não desenha (como o `suporteEmoji.ts` do web).
 */
object CatalogoEmojis {
    private val trava = Mutex()
    private var categorias: List<CategoriaEmojis>? = null

    suspend fun carregar(contexto: Context): List<CategoriaEmojis> = trava.withLock {
        categorias ?: withContext(Dispatchers.Default) { ler(contexto) }.also { categorias = it }
    }

    private fun ler(contexto: Context): List<CategoriaEmojis> {
        val texto = contexto.assets.open("emojis/pt.json").bufferedReader().use { it.readText() }
        val lista = JSONObject(texto).getJSONArray("categorias")
        val pincel = Paint()
        return (0 until lista.length()).map { i ->
            val categoria = lista.getJSONObject(i)
            val emojis: JSONArray = categoria.getJSONArray("emojis")
            CategoriaEmojis(
                id = categoria.getString("id"),
                nome = categoria.getString("nome"),
                icone = categoria.getString("icone"),
                emojis = (0 until emojis.length()).mapNotNull { j ->
                    val item = emojis.getJSONArray(j)
                    val emoji = item.getString(0)
                    if (!pincel.hasGlyph(emoji)) return@mapNotNull null
                    EmojiDoCatalogo(emoji, item.getString(1), textoDeBuscaDoEmoji(item.getString(1), item.getString(2)))
                },
            )
        }.filter { it.emojis.isNotEmpty() }
    }
}

/** Os usados por último, no aparelho (o web guarda no navegador). */
object EmojisRecentes {
    private const val ARQUIVO = "conversa_emojis"
    private const val CHAVE = "recentes"

    fun ler(contexto: Context): List<String> {
        val salvo = contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).getString(CHAVE, null)
        val lista = salvo?.let {
            runCatching { JSONArray(it) }.getOrNull()
        }?.let { json -> (0 until json.length()).map { json.getString(it) } }
        return lista?.takeIf { it.isNotEmpty() } ?: EMOJIS_POPULARES
    }

    fun usar(contexto: Context, emoji: String): List<String> {
        val novos = usarEmojiRecente(ler(contexto), emoji)
        contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE).edit { putString(CHAVE, JSONArray(novos).toString()) }
        return novos
    }
}

/**
 * Seletor de emojis como o do web (FC-418): "Pesquisar emoji", "Recentes" e as categorias, sem
 * tons de pele nem bandeiras (a mesma reação não se divide entre o web e o Android). A barra de
 * cima leva até cada categoria. O emoji escolhido vai para os recentes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeletorEmojis(aoEscolher: (String) -> Unit, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val categorias by produceState<List<CategoriaEmojis>?>(null) { value = CatalogoEmojis.carregar(contexto) }
    var recentes by remember { mutableStateOf(EmojisRecentes.ler(contexto)) }
    val termo = rememberTextFieldState()
    val grade = rememberLazyGridState()
    val escopo = rememberCoroutineScope()
    val escolher: (String) -> Unit = { emoji ->
        recentes = EmojisRecentes.usar(contexto, emoji)
        aoEscolher(emoji)
    }
    val nomes = remember(categorias) { categorias.orEmpty().flatMap { it.emojis }.associate { it.emoji to it.nome } }
    val rotuloRecentes = stringResource(R.string.emojis_recentes)
    ModalBottomSheet(onDismissRequest = aoFechar) {
        Column(Modifier.fillMaxWidth().height(440.dp)) {
            TextField(
                state = termo,
                placeholder = { Text(stringResource(R.string.pesquisar_emoji)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ConversaTema.cores.campoEntrada,
                    unfocusedContainerColor = ConversaTema.cores.campoEntrada,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            val pesquisando = termo.text.isNotBlank()
            val lista = categorias
            if (!pesquisando && lista != null) {
                // Recentes, depois cada categoria: o índice do cabeçalho na grade.
                val indices = remember(lista, recentes) { indicesDosCabecalhos(lista, recentes.size) }
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    item(key = "recentes") { BotaoCategoria("🕒", rotuloRecentes) { escopo.launch { grade.scrollToItem(0) } } }
                    items(lista, key = { it.id }) { categoria ->
                        BotaoCategoria(categoria.icone, categoria.nome) {
                            escopo.launch { grade.scrollToItem(indices.getValue(categoria.id)) }
                        }
                    }
                }
            }
            when {
                lista == null -> Aviso(stringResource(R.string.emojis_carregando))
                pesquisando -> {
                    val resultados = remember(lista, termo.text) { pesquisarEmojis(lista, termo.text.toString()) }
                    if (resultados.isEmpty()) {
                        Aviso(stringResource(R.string.emojis_nenhum))
                    } else {
                        LazyVerticalGrid(GridCells.Adaptive(48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                            items(resultados, key = { "r" + it.emoji }) { item -> Emoji(item.emoji, item.nome, escolher) }
                        }
                    }
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(48.dp), state = grade, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    cabecalho("cab-recentes", rotuloRecentes)
                    items(recentes, key = { "rec$it" }) { emoji -> Emoji(emoji, nomes[emoji] ?: emoji, escolher) }
                    for (categoria in lista) {
                        cabecalho("cab-${categoria.id}", categoria.nome)
                        items(categoria.emojis, key = { "${categoria.id}-${it.emoji}" }) { item -> Emoji(item.emoji, item.nome, escolher) }
                    }
                }
            }
        }
    }
}

/** O índice do cabeçalho de cada categoria na grade (antes vêm o cabeçalho e os [recentes]). */
private fun indicesDosCabecalhos(categorias: List<CategoriaEmojis>, recentes: Int): Map<String, Int> {
    var indice = 1 + recentes
    return categorias.associate { categoria ->
        val esta = indice
        indice += 1 + categoria.emojis.size
        categoria.id to esta
    }
}

private fun LazyGridScope.cabecalho(chave: String, texto: String) {
    item(key = chave, span = { GridItemSpan(maxLineSpan) }) {
        Text(
            texto,
            style = MaterialTheme.typography.labelMedium,
            color = ConversaTema.cores.textoTerciario,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun Emoji(emoji: String, nome: String, aoEscolher: (String) -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = nome) { aoEscolher(emoji) }
            .semantics { contentDescription = nome },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}

@Composable
private fun BotaoCategoria(icone: String, nome: String, aoTocar: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = nome, onClick = aoTocar)
            .semantics { contentDescription = nome },
        contentAlignment = Alignment.Center,
    ) {
        Text(icone, fontSize = 20.sp)
    }
}

@Composable
private fun Aviso(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = ConversaTema.cores.textoTerciario,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
