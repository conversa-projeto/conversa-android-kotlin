package com.conversa.app.feature.chamada

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.network.di.EscopoAplicacao
import com.conversa.app.core.ui.componentes.AreaDeAvisos
import com.conversa.app.core.ui.tema.ConversaTema
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Tela da chamada (recebida, chamando e em andamento), por cima de tudo e da tela
 * bloqueada (CHA-03). Fecha sozinha quando a chamada volta a "inativo".
 */
@AndroidEntryPoint
class ChamadaActivity : ComponentActivity() {
    /** "Atender" da notificação: atende assim que a tela abre. */
    private val pedidoAtender = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pedidoAtender.value = intent?.action == ACAO_ATENDER
        enableEdgeToEdge()
        // Tela acesa durante a chamada (o sensor de proximidade é a 6.8).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ConversaTema {
                AreaDeAvisos {
                    TelaChamadaRota(
                        aoFechar = ::finish,
                        atenderAoAbrir = pedidoAtender.value,
                        aoAtenderAoAbrir = { pedidoAtender.value = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACAO_ATENDER) pedidoAtender.value = true
    }

    companion object {
        const val ACAO_ATENDER = "com.conversa.app.chamada.ATENDER"

        fun intencao(contexto: Context): Intent =
            Intent(contexto, ChamadaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}

/**
 * Abre a [ChamadaActivity] quando uma chamada começa (liguei, ou está tocando) e o
 * app está na frente. Em segundo plano quem abre é a notificação de tela cheia (6.5).
 */
@Singleton
class ApresentadorChamada @Inject constructor(
    @ApplicationContext private val contexto: Context,
    private val gerenciador: GerenciadorChamadas,
    private val primeiroPlano: MonitorPrimeiroPlano,
    @EscopoAplicacao private val escopo: CoroutineScope,
) {
    private var iniciado = false

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        escopo.launch(Dispatchers.Main) {
            gerenciador.estado
                .map { it.fase != FaseChamada.INATIVO && (it.fase != FaseChamada.RECEBENDO || it.tocando) }
                .distinctUntilChanged()
                .collect { mostrar ->
                    if (mostrar &&
                        primeiroPlano.emPrimeiroPlano.value
                    ) {
                        contexto.startActivity(ChamadaActivity.intencao(contexto))
                    }
                }
        }
    }
}
