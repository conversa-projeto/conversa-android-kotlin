package com.conversa.app.feature.chat

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.conversa.app.core.ui.tema.ConversaTema
import org.json.JSONObject

/** A página do diagrama, nos assets (com o mermaid.min.js ao lado). */
private const val PAGINA_MERMAID = "file:///android_asset/mermaid/diagrama.html"

/** Altura enquanto desenha (o WebView precisa de algum tamanho para carregar). */
private const val ALTURA_INICIAL_DP = 1

/**
 * Diagrama ```mermaid (7.9, como o `useMermaid.ts` do web), desenhado offline num WebView:
 * só a página dos assets, sem rede, sem acesso a arquivos e sem navegar; o mermaid roda com
 * `securityLevel: 'strict'`. A altura vem da página depois de desenhar. Texto que não é um
 * diagrama válido chama [aoFalhar] (a tela mostra o código, como o web).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun DiagramaMermaid(codigo: String, aoFalhar: () -> Unit, modifier: Modifier = Modifier) {
    val cores = coresDoMermaid()
    var altura by remember(codigo, cores) { mutableIntStateOf(ALTURA_INICIAL_DP) }
    val falhou by rememberUpdatedState(aoFalhar)
    key(codigo, cores) {
        AndroidView(
            factory = { contexto ->
                WebView(contexto).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.blockNetworkLoads = true
                    // A página chama de volta pela ponte (numa thread do WebView: volta para a principal).
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun altura(px: Int) {
                                post { altura = px.coerceAtLeast(ALTURA_INICIAL_DP) }
                            }

                            @JavascriptInterface
                            fun falhou() {
                                post { falhou() }
                            }
                        },
                        "Conversa",
                    )
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("desenhar(${JSONObject.quote(codigo)}, $cores)", null)
                        }
                    }
                    loadUrl(PAGINA_MERMAID)
                }
            },
            // A página usa a largura do aparelho: 1 px de CSS = 1 dp.
            modifier = modifier.fillMaxWidth().height(altura.dp),
        )
    }
}

/** As cores do diagrama só com tokens (`docs/design/cores.md` §6.4), num objeto `themeVariables`. */
@Composable
private fun coresDoMermaid(): String {
    val esquema = MaterialTheme.colorScheme
    val cores = ConversaTema.cores
    val hex = { cor: Color -> "#%06X".format(cor.toArgb() and 0xFFFFFF) }
    return JSONObject(
        mapOf(
            "background" to hex(esquema.surface),
            "primaryColor" to hex(cores.campoEntrada),
            "primaryTextColor" to hex(esquema.onSurface),
            "primaryBorderColor" to hex(esquema.primary),
            "secondaryColor" to hex(cores.divisorLista),
            "tertiaryColor" to hex(esquema.surface),
            "lineColor" to hex(cores.textoTerciario),
            "textColor" to hex(esquema.onSurface),
            "noteBkgColor" to hex(cores.campoEntrada),
            "noteTextColor" to hex(esquema.onSurface),
            "fontFamily" to "sans-serif",
        ),
    ).toString()
}
