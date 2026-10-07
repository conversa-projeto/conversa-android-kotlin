package com.conversa.app.navegacao

import com.conversa.app.feature.auth.login.AvisoLogin
import kotlinx.serialization.Serializable

// Rotas tipadas do app (TODO 2.5). Cada uma é uma tela do NavHost.

/** Configuração do servidor. */
@Serializable
data class RotaServidor(val podeVoltar: Boolean)

/** Login; [usuario] vem do cadastro e [aviso] explica por que a pessoa caiu aqui. */
@Serializable
data class RotaLogin(val usuario: String? = null, val aviso: AvisoLogin = AvisoLogin.NENHUM)

@Serializable
data object RotaCadastro

/** Tela principal com a barra inferior (Conversas, Chamadas, Atividades, Configurações). */
@Serializable
data object RotaPrincipal

/** Conversa aberta. [mensagemId] = 0 quando não há mensagem para destacar. */
@Serializable
data class RotaChat(val conversaId: Long, val mensagemId: Long = 0)

/** Contatos para começar uma conversa (e o atalho para criar grupo). */
@Serializable
data object RotaNovaConversa

@Serializable
data object RotaCriarGrupo

@Serializable
data class RotaMembros(val conversaId: Long)
