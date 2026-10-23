package com.conversa.app.feature.chat

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.conversa.app.core.media.ReprodutorVideo
import com.conversa.app.core.model.CodigoPermissao
import com.conversa.app.core.model.TipoConteudo
import com.conversa.app.core.ui.componentes.LocalAvisos
import com.conversa.app.core.ui.componentes.LocalPermissoesDeUso
import com.conversa.app.core.ui.componentes.mostrarErro
import com.conversa.app.core.ui.tema.ConversaTema
import java.io.File
import kotlinx.coroutines.launch

/**
 * "+" › "Câmera" (ANX-20; web `77c7821`, `CameraModal.vue`): foto ou vídeo pela câmera do sistema
 * (com a permissão de uso `imagem` ou `video`; com as duas, a pessoa escolhe antes), e a prévia com
 * "Adicione uma legenda…", "Refazer" e "Enviar", que manda na hora, o arquivo antes da legenda.
 * Devolve a ação de abrir. O app declara a permissão de câmera (chamadas), então o Android exige que
 * ela esteja dada para abrir a câmera do sistema: é pedida no toque.
 */
@Composable
internal fun rememberCameraDoCampo(acoes: AcoesChat): () -> Unit {
    val contexto = LocalContext.current
    val avisos = LocalAvisos.current
    val escopo = rememberCoroutineScope()
    val permissoes = LocalPermissoesDeUso.current
    val semCamera = stringResource(R.string.sem_app_de_camera)
    val semPermissao = stringResource(R.string.camera_sem_permissao)
    // O arquivo da captura, o tipo e a prévia sobrevivem à recriação da Activity enquanto a câmera está aberta.
    var capturando by rememberSaveable { mutableStateOf<String?>(null) }
    var video by rememberSaveable { mutableStateOf(false) }
    var previa by rememberSaveable { mutableStateOf<String?>(null) }
    var escolhendo by rememberSaveable { mutableStateOf(false) }
    var esperandoPermissao by rememberSaveable { mutableStateOf<Boolean?>(null) }

    fun aoCapturar(ok: Boolean) {
        val arquivo = capturando?.let(::File)
        capturando = null
        if (arquivo == null) return
        if (ok && arquivo.length() > 0) previa = arquivo.path else arquivo.delete()
    }
    val foto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { aoCapturar(it) }
    val filmagem = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { aoCapturar(it) }

    fun lancar(comVideo: Boolean) {
        val pasta = acoes.pastaCamera() ?: return
        val arquivo = File(pasta, if (comVideo) "video-${System.currentTimeMillis()}.mp4" else "foto-${System.currentTimeMillis()}.jpg")
        capturando = arquivo.path
        video = comVideo
        try {
            val uri = uriCompartilhado(contexto, arquivo)
            if (comVideo) filmagem.launch(uri) else foto.launch(uri)
        } catch (_: ActivityNotFoundException) {
            capturando = null
            escopo.launch { avisos.mostrarErro(semCamera) }
        } catch (_: SecurityException) {
            capturando = null
            escopo.launch { avisos.mostrarErro(semPermissao) }
        }
    }
    val permissaoCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        val comVideo = esperandoPermissao ?: return@rememberLauncherForActivityResult
        esperandoPermissao = null
        if (concedida) lancar(comVideo) else escopo.launch { avisos.mostrarErro(semPermissao) }
    }
    fun capturar(comVideo: Boolean) {
        if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            lancar(comVideo)
        } else {
            esperandoPermissao = comVideo
            permissaoCamera.launch(Manifest.permission.CAMERA)
        }
    }

    if (escolhendo) {
        AlertDialog(
            onDismissRequest = { escolhendo = false },
            title = { Text(stringResource(R.string.anexar_camera)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.camera_foto)) },
                        leadingContent = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
                        modifier = Modifier.clickable {
                            escolhendo = false
                            capturar(comVideo = false)
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.camera_video)) },
                        leadingContent = { Icon(Icons.Outlined.Videocam, contentDescription = null) },
                        modifier = Modifier.clickable {
                            escolhendo = false
                            capturar(comVideo = true)
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { escolhendo = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }

    previa?.let { caminho ->
        val arquivo = File(caminho)
        PreviaDaCamera(
            arquivo = arquivo,
            video = video,
            aoRefazer = {
                arquivo.delete()
                previa = null
                capturar(video)
            },
            aoEnviar = { legenda ->
                previa = null
                acoes.aoEnviarDaCamera(uriCompartilhado(contexto, arquivo).toString(), if (video) null else TipoConteudo.IMAGEM, legenda)
            },
            aoFechar = {
                arquivo.delete()
                previa = null
            },
        )
    }

    return {
        val podeFoto = permissoes.pode(CodigoPermissao.IMAGEM)
        val podeVideo = permissoes.pode(CodigoPermissao.VIDEO)
        when {
            podeFoto && podeVideo -> escolhendo = true
            podeVideo -> capturar(comVideo = true)
            else -> capturar(comVideo = false)
        }
    }
}

/** A prévia da foto ou do vídeo em tela cheia, com a legenda, "Refazer" e "Enviar" (web `CameraModal.vue`). */
@Composable
private fun PreviaDaCamera(arquivo: File, video: Boolean, aoRefazer: () -> Unit, aoEnviar: (String) -> Unit, aoFechar: () -> Unit) {
    val legenda = rememberTextFieldState()
    val enviar = { aoEnviar(legenda.text.toString().trim()) }
    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(ConversaTema.cores.fundoVisualizadorMidia).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = aoFechar) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.fechar), tint = Color.White)
                }
                Text(stringResource(R.string.enviar), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (video) {
                    ReprodutorVideo(arquivo.toURI().toString(), ativo = true, aoFalhar = {}, modifier = Modifier.fillMaxSize())
                } else {
                    AsyncImage(
                        model = arquivo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            OutlinedTextField(
                state = legenda,
                placeholder = { Text(stringResource(R.string.camera_legenda), color = Color.White.copy(alpha = 0.6f)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                onKeyboardAction = { enviar() },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.6f),
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = aoRefazer) { Text(stringResource(R.string.camera_refazer), color = Color.White) }
                Button(onClick = enviar) { Text(stringResource(R.string.enviar)) }
            }
        }
    }
}
