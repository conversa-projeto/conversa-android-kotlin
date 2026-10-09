package com.conversa.app.feature.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.data.ServidorRepositorio
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Uma biblioteca de código aberto usada pelo app e a sua licença. */
internal data class Biblioteca(val nome: String, val autor: String, val licenca: String)

private const val APACHE = "Apache License 2.0"

/** O que vai dentro do APK (as de teste e de build ficam de fora). */
internal val BIBLIOTECAS = listOf(
    Biblioteca("Android Jetpack (AndroidX, Compose, Material 3, Room, DataStore, WorkManager, Media3, Core-Telecom)", "Google", APACHE),
    Biblioteca("Kotlin, kotlinx.coroutines e kotlinx.serialization", "JetBrains", APACHE),
    Biblioteca("Dagger e Hilt", "Google", APACHE),
    Biblioteca("OkHttp e Retrofit", "Square", APACHE),
    Biblioteca("Coil", "Coil Contributors", APACHE),
    Biblioteca("Tink", "Google", APACHE),
    Biblioteca("Timber", "Jake Wharton", APACHE),
    Biblioteca("Lottie", "Airbnb", APACHE),
    Biblioteca("Multiplatform Markdown Renderer", "Mike Penz", APACHE),
    Biblioteca("WebRTC", "The WebRTC project authors", "BSD 3-Clause"),
    Biblioteca("Mermaid", "Knut Sveidqvist e colaboradores", "MIT"),
)

@HiltViewModel
class SobreViewModel @Inject constructor(servidor: ServidorRepositorio) : ViewModel() {
    val servidor = servidor.atual
}

/** Sobre (8.5): a versão do app, o servidor conectado e as licenças de código aberto. */
@Composable
fun SobreRotaTela(aoVoltar: () -> Unit, viewModel: SobreViewModel = hiltViewModel()) {
    val servidor by viewModel.servidor.collectAsStateWithLifecycle()
    SubTela(titulo = stringResource(R.string.config_sobre), aoVoltar = aoVoltar) { modificador ->
        LazyColumn(modificador.fillMaxSize()) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sobre_nome_do_app), style = MaterialTheme.typography.titleLarge)
                    Text(versaoDoApp(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.sobre_servidor, servidor?.base?.toString().orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                Text(
                    stringResource(R.string.sobre_licencas),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
            items(BIBLIOTECAS) { biblioteca ->
                ListItem(
                    headlineContent = { Text(biblioteca.nome) },
                    supportingContent = {
                        Text("${biblioteca.autor} · ${biblioteca.licenca}", color = ConversaTema.cores.textoTerciario)
                    },
                )
            }
        }
    }
}
