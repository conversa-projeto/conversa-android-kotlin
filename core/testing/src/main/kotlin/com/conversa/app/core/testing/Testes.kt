package com.conversa.app.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Troca o `Dispatchers.Main` por um dispatcher de teste (ViewModels). */
class RegraDispatcherPrincipal(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** JSONs do contrato do servidor, em `src/main/resources/fixtures/`. */
object Fixtures {
    fun ler(nome: String): String {
        val caminho = "fixtures/$nome"
        val recurso = Fixtures::class.java.classLoader?.getResourceAsStream(caminho)
            ?: error("Fixture não encontrada: $caminho")
        return recurso.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

/**
 * Escopo "da aplicação" para os testes, no mesmo relógio virtual do teste
 * (o `backgroundScope` não avança com `advanceUntilIdle`). Cancele no fim se
 * houver laços infinitos (`escopo.cancel()`).
 */
fun TestScope.escopoDoTeste(): CoroutineScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
