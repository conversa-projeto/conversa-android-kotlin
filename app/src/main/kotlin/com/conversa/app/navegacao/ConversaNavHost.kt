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
import com.conversa.app.feature.chat.ChatRotaTela
import com.conversa.app.feature.conversas.enviarpara.EnviarParaRotaTela
import com.conversa.app.feature.conversas.grupo.CriarGrupoRotaTela
import com.conversa.app.feature.conversas.lista.ConversasRotaTela
import com.conversa.app.feature.conversas.membros.MembrosRotaTela
import com.conversa.app.feature.conversas.novaconversa.NovaConversaRotaTela
import com.conversa.app.principal.ConfiguracoesProvisorias
import com.conversa.app.principal.PrincipalTela

@Composable
fun ConversaNavHost(destinoInicial: Any, principal: MainViewModel) {
    val nav = rememberNavController()

    ColetarEventos(principal.navegacao.fluxo) { evento ->
        when (evento) {
            is NavegacaoGlobal.IrParaLogin -> nav.irParaRaiz(RotaLogin(aviso = evento.aviso))
            is NavegacaoGlobal.AbrirConversa -> nav.navigate(evento.rota) { launchSingleTop = true }
            NavegacaoGlobal.EnviarPara -> nav.navigate(RotaEnviarPara) { launchSingleTop = true }
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
                    if (principal.consumirCompartilhamentoPendente()) nav.navigate(RotaEnviarPara)
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
                conversas = { modificador ->
                    ConversasRotaTela(
                        aoAbrirConversa = { nav.navigate(RotaChat(it)) },
                        aoNovaConversa = { nav.navigate(RotaNovaConversa) },
                        aoMembros = { nav.navigate(RotaMembros(it)) },
                        modifier = modificador,
                    )
                },
                configuracoes = { modificador ->
                    ConfiguracoesProvisorias(
                        aoTrocarServidor = { nav.navigate(RotaServidor(podeVoltar = true)) },
                        modifier = modificador,
                    )
                },
            )
        }
        composable<RotaChat> {
            ChatRotaTela(
                aoVoltar = { nav.popBackStack() },
                aoMembros = { nav.navigate(RotaMembros(it)) },
                aoAbrirConversa = { nav.navigate(RotaChat(it)) },
            )
        }
        composable<RotaEnviarPara> {
            EnviarParaRotaTela(
                // A conversa abre no lugar desta tela (voltar leva a onde a pessoa estava).
                aoAbrirConversa = { id ->
                    nav.navigate(RotaChat(id, comCompartilhamento = true)) {
                        popUpTo<RotaEnviarPara> {
                            inclusive =
                                true
                        }
                    }
                },
                aoVoltar = { nav.popBackStack() },
            )
        }
        composable<RotaNovaConversa> {
            NovaConversaRotaTela(
                // Abre a conversa no lugar desta tela (voltar leva à lista).
                aoAbrirConversa = { id -> nav.navigate(RotaChat(id)) { popUpTo<RotaNovaConversa> { inclusive = true } } },
                aoNovoGrupo = { nav.navigate(RotaCriarGrupo) },
                aoVoltar = { nav.popBackStack() },
            )
        }
        composable<RotaCriarGrupo> {
            CriarGrupoRotaTela(
                aoCriado = { id -> nav.navigate(RotaChat(id)) { popUpTo<RotaPrincipal>() } },
                aoVoltar = { nav.popBackStack() },
            )
        }
        composable<RotaMembros> {
            MembrosRotaTela(
                // Saiu do grupo: a conversa some; volta à lista.
                aoSair = { nav.popBackStack<RotaPrincipal>(inclusive = false) },
                aoVoltar = { nav.popBackStack() },
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
