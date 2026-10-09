package com.conversa.app.feature.chamada

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.conversa.app.core.data.preferencias.PreferenciasRepositorio
import com.conversa.app.core.data.tempoReal.MonitorPrimeiroPlano
import com.conversa.app.core.model.PreferenciaTema
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
    @Inject lateinit var preferencias: PreferenciasRepositorio

    /** "Atender" da notificação: atende assim que a tela abre. */
    private val pedidoAtender = mutableStateOf(false)
    private val emPip = mutableStateOf(false)

    /** Chamada de vídeo em andamento: sair do app (ou voltar) vira picture-in-picture (6.12). */
    private var podePip = false
    private var areaDoVideo: Rect? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pedidoAtender.value = intent?.action == ACAO_ATENDER
        enableEdgeToEdge()
        // Tela acesa durante a chamada (o sensor de proximidade é a 6.8).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        addOnPictureInPictureModeChangedListener { emPip.value = it.isInPictureInPictureMode }
        setContent {
            // A tela da chamada é escura nos dois temas; o tema vale para o que abre por cima (8.5).
            val tema = preferencias.tema.collectAsStateWithLifecycle(PreferenciaTema.PADRAO).value
            ConversaTema(escuro = tema.escuro(isSystemInDarkTheme())) {
                AreaDeAvisos {
                    TelaChamadaRota(
                        aoFechar = ::finish,
                        atenderAoAbrir = pedidoAtender.value,
                        aoAtenderAoAbrir = { pedidoAtender.value = false },
                        emPip = emPip.value,
                        aoPodePip = ::atualizarPip,
                        aoMinimizar = ::minimizar,
                        aoAreaDoVideo = ::novaAreaDoVideo,
                        aoAbrirChat = ::abrirChat,
                    )
                }
            }
        }
    }

    /** Android 12+: entra sozinho ao sair do app; antes disso, pelo [onUserLeaveHint]. */
    private fun atualizarPip(pode: Boolean) {
        podePip = pode && temPip()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && temPip()) setPictureInPictureParams(parametrosPip(podePip))
    }

    /**
     * Chat da chamada (6.13): a conversa no app; em vídeo, a chamada vai para o picture-in-picture.
     * [focar]: recém-criada, abre com o cursor no campo.
     */
    private fun abrirChat(conversaId: Long, focar: Boolean) {
        if (podePip) entrarEmPip()
        startActivity(
            Intent(
                Intent.ACTION_VIEW,
                (if (focar) "conversa://chat/$conversaId?focar=1" else "conversa://chat/$conversaId").toUri(),
            ).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun novaAreaDoVideo(area: Rect) {
        if (area == areaDoVideo) return
        areaDoVideo = area
        if (podePip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setPictureInPictureParams(parametrosPip(true))
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (podePip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) entrarEmPip()
    }

    /** Voltar minimiza: em vídeo vira picture-in-picture; senão a tela fecha e a chamada segue (faixa no topo do app). */
    private fun minimizar() {
        if (podePip) entrarEmPip() else finish()
    }

    private fun entrarEmPip() {
        try {
            enterPictureInPictureMode(parametrosPip(true))
        } catch (_: IllegalStateException) {
            finish()
        }
    }

    private fun parametrosPip(automatico: Boolean): PictureInPictureParams = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(9, 16))
        .apply { areaDoVideo?.let { setSourceRectHint(it) } }
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (automatico) setAutoEnterEnabled(true) else setAutoEnterEnabled(false)
            }
        }
        .build()

    private fun temPip() = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

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
