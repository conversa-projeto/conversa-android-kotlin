package com.conversa.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.ui.componentes.AreaDeAvisos
import com.conversa.app.core.ui.tema.ConversaTema
import com.conversa.app.navegacao.ConversaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A splash fica até saber para onde ir (servidor configurado? sessão?).
        splash.setKeepOnScreenCondition { viewModel.destinoInicial.value == null }
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.receberLink(intent?.data)
        setContent {
            ConversaTema {
                AreaDeAvisos {
                    val destino = viewModel.destinoInicial.collectAsStateWithLifecycle().value
                    if (destino != null) ConversaNavHost(destinoInicial = destino, principal = viewModel)
                }
            }
        }
    }

    /** `singleTask`: links com o app aberto chegam aqui. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.receberLink(intent.data)
    }
}
