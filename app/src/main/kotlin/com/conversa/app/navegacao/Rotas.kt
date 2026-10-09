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

/**
 * Conversa aberta. [mensagemId] = 0 quando não há mensagem para destacar.
 * [comCompartilhamento]: veio do "Enviar para…" (os itens compartilhados entram na fila do campo).
 * [focar]: abrir com o cursor no campo (o chat da chamada recém-criado, 6.13).
 * [encaminharDe]: "Responder no privado" (7.6): a mensagem do grupo fica pendente como encaminhada.
 */
@Serializable
data class RotaChat(
    val conversaId: Long,
    val mensagemId: Long = 0,
    val comCompartilhamento: Boolean = false,
    val focar: Boolean = false,
    val encaminharDe: Long = 0,
)

/** "Enviar para…": o que outro app compartilhou (AND-10). */
@Serializable
data object RotaEnviarPara

/** Contatos para começar uma conversa (e o atalho para criar grupo). */
@Serializable
data object RotaNovaConversa

@Serializable
data object RotaCriarGrupo

@Serializable
data class RotaMembros(val conversaId: Long)

/** Pesquisa em todos os chats (8.2), já com o termo do campo da lista, se houver. */
@Serializable
data class RotaPesquisa(val termo: String = "")

/** Perfil do usuário logado: foto, nome e e-mail, senha (8.3). */
@Serializable
data object RotaPerfil

/** Sobre (8.5): versão, servidor e licenças. */
@Serializable
data object RotaSobre

/** Notificações (8.5): as do app e as de cada canal. */
@Serializable
data object RotaNotificacoes

/** Permissões (8.5): notificações, microfone, câmera, tela cheia e bateria. */
@Serializable
data object RotaPermissoes

/** Qualidade das chamadas (8.5): áudio e vídeo que este aparelho envia. */
@Serializable
data object RotaQualidadeChamadas
