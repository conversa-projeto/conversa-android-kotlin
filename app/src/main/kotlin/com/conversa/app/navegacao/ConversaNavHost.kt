package com.conversa.app.navegacao

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.conversa.app.MainViewModel
import com.conversa.app.NavegacaoGlobal
import com.conversa.app.core.ui.estado.ColetarEventos
import com.conversa.app.feature.atividades.AtividadesRotaTela
import com.conversa.app.feature.auth.cadastro.CadastroRotaTela
import com.conversa.app.feature.auth.login.AvisoLogin
import com.conversa.app.feature.auth.login.LoginRotaTela
import com.conversa.app.feature.auth.servidor.ServidorRotaTela
import com.conversa.app.feature.chamada.HistoricoChamadasRota
import com.conversa.app.feature.chamada.rememberLigarParaUsuario
import com.conversa.app.feature.chat.ChatRotaTela
import com.conversa.app.feature.config.ConfiguracoesRotaTela
import com.conversa.app.feature.config.PerfilRotaTela
import com.conversa.app.feature.config.SobreRotaTela
import com.conversa.app.feature.conversas.enviarpara.EnviarParaRotaTela
import com.conversa.app.feature.conversas.grupo.CriarGrupoRotaTela
import com.conversa.app.feature.conversas.lista.ConversasRotaTela
import com.conversa.app.feature.conversas.membros.MembrosRotaTela
import com.conversa.app.feature.conversas.novaconversa.NovaConversaRotaTela
import com.conversa.app.feature.pesquisa.PesquisaRotaTela
import com.conversa.app.principal.PrincipalTela

@Composable
fun ConversaNavHost(destinoInicial: Any, principal: MainViewModel) {
    val nav = rememberNavController()

    ColetarEventos(principal.navegacao.fluxo) { evento ->
        when (evento) {
            is NavegacaoGlobal.IrParaLogin -> nav.irParaRaiz(RotaLogin(aviso = evento.aviso))
            is NavegacaoGlobal.AbrirConversa -> nav.abrirConversa(evento.rota)
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
                    // "Ligar" do perfil da pessoa (8.4): pede o microfone e liga, como os botões da conversa.
                    val ligar = rememberLigarParaUsuario()
                    ConversasRotaTela(
                        aoAbrirConversa = { nav.navigate(RotaChat(it)) },
                        aoNovaConversa = { nav.navigate(RotaNovaConversa) },
                        aoMembros = { nav.navigate(RotaMembros(it)) },
                        aoPesquisarEmTodos = { termo -> nav.navigate(RotaPesquisa(termo)) },
                        aoLigar = ligar,
                        modifier = modificador,
                    )
                },
                chamadas = { modificador ->
                    HistoricoChamadasRota(aoAbrirConversa = { nav.navigate(RotaChat(it)) }, modifier = modificador)
                },
                atividades = { modificador ->
                    AtividadesRotaTela(
                        aoAbrirMensagem = { conversa, mensagem -> nav.navigate(RotaChat(conversa, mensagem)) },
                        aoAbrirConversa = { nav.navigate(RotaChat(it)) },
                        modifier = modificador,
                    )
                },
                configuracoes = { modificador ->
                    ConfiguracoesRotaTela(
                        aoAbrirPerfil = { nav.navigate(RotaPerfil) },
                        aoTrocarServidor = { nav.navigate(RotaServidor(podeVoltar = true)) },
                        aoAbrirSobre = { nav.navigate(RotaSobre) },
                        modifier = modificador,
                    )
                },
            )
        }
        composable<RotaChat> {
            ChatRotaTela(
                aoVoltar = { nav.popBackStack() },
                aoMembros = { nav.navigate(RotaMembros(it)) },
                aoAbrirConversa = { conversa, mensagem, encaminharDe ->
                    nav.navigate(RotaChat(conversa, mensagem, encaminharDe = encaminharDe))
                },
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
        composable<RotaPesquisa> {
            PesquisaRotaTela(
                aoAbrirMensagem = { conversa, mensagem -> nav.navigate(RotaChat(conversa, mensagem)) },
                aoVoltar = { nav.popBackStack() },
            )
        }
        composable<RotaSobre> { SobreRotaTela(aoVoltar = { nav.popBackStack() }) }
        composable<RotaPerfil> { PerfilRotaTela(aoVoltar = { nav.popBackStack() }) }
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

/**
 * Link ou notificação de uma conversa. Com um chat aberto, ele é trocado pelo pedido (o
 * `launchSingleTop` reaproveitava a entrada do topo, com o ViewModel da conversa antiga, e a
 * tela não mudava); a mesma conversa sem mensagem para mostrar fica como está.
 */
private fun NavHostController.abrirConversa(rota: RotaChat) {
    val topo = currentBackStackEntry?.takeIf { it.destination.hasRoute<RotaChat>() }?.toRoute<RotaChat>()
    if (topo != null && topo.conversaId == rota.conversaId && rota.mensagemId == 0L) return
    navigate(rota) { if (topo != null) popUpTo<RotaChat> { inclusive = true } }
}
