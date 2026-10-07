package com.conversa.app.navegacao

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.conversa.app.feature.auth.servidor.ServidorRotaTela
import com.conversa.app.inicio.InicioTela
import kotlinx.serialization.Serializable

/** Tela de configuração do servidor. */
@Serializable
data class RotaServidor(val podeVoltar: Boolean)

/** Tela inicial provisória da fundação (a lista de conversas chega na etapa 2). */
@Serializable
data object RotaInicio

@Composable
fun ConversaNavHost(destinoInicial: Any) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = destinoInicial) {
        composable<RotaServidor> { entrada ->
            val rota = entrada.toRoute<RotaServidor>()
            ServidorRotaTela(
                aoSalvar = {
                    nav.navigate(RotaInicio) {
                        popUpTo(nav.graph.id) { inclusive = true }
                    }
                },
                aoVoltar = if (rota.podeVoltar) ({ nav.popBackStack() }) else null,
            )
        }
        composable<RotaInicio> {
            InicioTela(aoTrocarServidor = { nav.navigate(RotaServidor(podeVoltar = true)) })
        }
    }
}
