package com.conversa.app.core.registro

import android.content.Context
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Singleton
import kotlin.concurrent.thread
import timber.log.Timber

/**
 * O registro interno do app: tudo o que passa pelo Timber (e as falhas fatais) vai para arquivos em
 * `files/registro/`, guardados por 7 dias ([ArquivosDoRegistro]). Fica só no aparelho; a pessoa vê e
 * compartilha pela tela "Registro do app". No debug, as mesmas linhas também vão para o logcat.
 *
 * Quem chama o Timber não espera o disco: as linhas entram numa fila e uma thread só grava.
 *
 * Um só por processo ([de]): o app liga antes do Hilt (`Application.onCreate`, antes do `super`),
 * senão o que os singletons registram ao serem criados se perde. O Hilt entrega o mesmo ([RegistroModulo]).
 */
class Registro private constructor(contexto: Context) {
    val arquivos = ArquivosDoRegistro(File(contexto.filesDir, PASTA))

    private val fila = LinkedBlockingQueue<String>(CAPACIDADE_FILA)
    private val descartadas = AtomicInteger()
    private val iniciado = AtomicBoolean(false)

    /** Liga a gravação, a árvore do Timber e a captura de falhas fatais. Uma vez, no `Application.onCreate`. */
    fun iniciar() {
        if (!iniciado.compareAndSet(false, true)) return
        thread(name = "registro", isDaemon = true) { gravarSempre() }
        Timber.plant(ArvoreDoRegistro(this))
        capturarFalhasFatais()
    }

    /** Põe a linha na fila. Com a fila cheia (rajada de erros), descarta e avisa depois quantas foram. */
    fun anotar(linha: String) {
        if (!fila.offer(linha)) descartadas.incrementAndGet()
    }

    /** O processo vai morrer: grava o que está na fila e a [linha] agora, na thread de quem chamou. */
    fun anotarAgora(linha: String) {
        val lote = ArrayList<String>()
        fila.drainTo(lote)
        lote += linha
        arquivos.escrever(lote)
    }

    /** Apaga tudo e registra quem apagou (a primeira linha do registro novo). */
    fun apagarTudo() {
        fila.clear()
        arquivos.apagarTudo()
        Timber.tag(TAG).i("Registro apagado pela pessoa, na tela do registro")
    }

    private fun gravarSempre() {
        arquivos.limparAntigos()
        while (true) {
            val lote = arrayListOf(fila.take())
            fila.drainTo(lote, LOTE - 1)
            val perdidas = descartadas.getAndSet(0)
            if (perdidas > 0) {
                lote.add(0, linhaDoRegistro(ZonedDateTime.now(), Log.WARN, TAG, "registro", "$perdidas linhas descartadas (fila cheia)"))
            }
            arquivos.escrever(lote)
        }
    }

    private fun capturarFalhasFatais() {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            runCatching {
                // stackTraceToString, e não Log.getStackTraceString: este esconde a pilha das UnknownHostException.
                val texto = "O app fechou por um erro não tratado\n" + erro.stackTraceToString().trimEnd()
                anotarAgora(linhaDoRegistro(ZonedDateTime.now(), Log.ASSERT, "Falha", thread.name, texto))
            }
            anterior?.uncaughtException(thread, erro)
        }
    }

    companion object {
        @Volatile private var instancia: Registro? = null

        fun de(contexto: Context): Registro = instancia ?: synchronized(this) {
            instancia ?: Registro(contexto.applicationContext ?: contexto).also { instancia = it }
        }

        const val PASTA = "registro"
        const val TAG = "Registro"
        private const val CAPACIDADE_FILA = 20_000
        private const val LOTE = 500
    }
}

@Module
@InstallIn(SingletonComponent::class)
object RegistroModulo {
    @Provides
    @Singleton
    fun registro(@ApplicationContext contexto: Context): Registro = Registro.de(contexto)
}

/**
 * A árvore do Timber que grava no [Registro]. Herda da `DebugTree` só para descobrir a tag pela
 * classe de quem chamou quando não há uma explícita (no release, com o R8, os nomes das classes do
 * app ficam: `proguard-rules.pro`). Do DEBUG para cima; nada vai para o logcat por aqui.
 */
private class ArvoreDoRegistro(private val registro: Registro) : Timber.DebugTree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.DEBUG

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        registro.anotar(linhaDoRegistro(ZonedDateTime.now(), priority, tag, Thread.currentThread().name, message.trimEnd()))
    }
}
