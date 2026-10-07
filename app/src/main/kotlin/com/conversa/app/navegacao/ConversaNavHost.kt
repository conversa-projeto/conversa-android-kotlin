package com.conversa.app.navegacao

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.conversa.app.MainViewModel
import com.conversa.app.NavegacaoGlobal
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.feature.auth.cadastro.CadastroRotaTela
import com.conversa.app.feature.auth.login.AvisoLogin
import com.conversa.app.feature.auth.login.LoginRotaTela
import com.conversa.app.feature.auth.servidor.ServidorRotaTela
import com.conversa.app.principal.ConfiguracoesProvisorias
import com.conversa.app.principal.ConversasProvisorias
import com.conversa.app.principal.PrincipalTela

@Composable
fun ConversaNavHost(destinoInicial: Any, principal: MainViewModel) {
    val nav = rememberNavController()

    ColetarEventos(principal.navegacao.fluxo) { evento ->
        when (evento) {
            is NavegacaoGlobal.IrParaLogin -> nav.irParaRaiz(RotaLogin(aviso = evento.aviso))
            is NavegacaoGlobal.AbrirConversa -> nav.navigate(evento.rota) { launchSingleTop = true }
        }
    }

    NavHost(navController = nav, startDestination = destinoInicial) {
        composable<RotaServidor> { entrada ->
            val rota = entrada.toRoute<RotaServidor>()
            ServidorRotaTela(
                aoSalvar = {
                    // Primeira configuração: segue para o login. Troca a partir de outra tela: volta.
                    if (rota.podeVoltar) nav.popBackStack() else nav.irParaRaiz(RotaLogin())
                },
                aoVoltar = if (rota.podeVoltar) ({ nav.popBackStack() }) else null,
            )
        }
        composable<RotaLogin> { entrada ->
            val rota = entrada.toRoute<RotaLogin>()
            LoginRotaTela(
                usuarioInicial = rota.usuario,
                aviso = rota.aviso,
                aoEntrar = {
                    nav.irParaRaiz(RotaPrincipal)
                    principal.consumirLinkPendente()?.let { nav.navigate(it) }
                },
                aoCriarConta = { nav.navigate(RotaCadastro) },
                aoTrocarServidor = { nav.navigate(RotaServidor(podeVoltar = true)) },
            )
        }
        composable<RotaCadastro> {
            CadastroRotaTela(
                aoCriar = { usuario -> nav.irParaRaiz(RotaLogin(usuario = usuario, aviso = AvisoLogin.CONTA_CRIADA)) },
                aoVoltar = { nav.popBackStack() },
            )
        }
        composable<RotaPrincipal> {
            PrincipalTela(
                conversas = { modificador -> ConversasProvisorias(modificador) },
                configuracoes = { modificador ->
                    ConfiguracoesProvisorias(
                        aoTrocarServidor = { nav.navigate(RotaServidor(podeVoltar = true)) },
                        modifier = modificador,
                    )
                },
            )
        }
    }
}

/** Abre [rota] limpando toda a pilha (login, principal). */
private fun NavHostController.irParaRaiz(rota: Any) {
    navigate(rota) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
