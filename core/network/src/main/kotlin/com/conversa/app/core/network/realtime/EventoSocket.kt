package com.conversa.app.core.network.realtime

import com.conversa.app.core.network.json.ConversaJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Eventos do WebSocket (contrato §6.4–6.6). Todos chegam como JSON com `tipo`
 * numérico e os campos na raiz (exceto o 57, que traz `dados`).
 */
sealed interface EventoSocket {
    /** 0 — falha no login do socket (token inválido/expirado). */
    data class ErroLogin(val mensagem: String) : EventoSocket

    /** 9 — o servidor não entendeu a mensagem enviada. */
    data class ErroLeitura(val mensagem: String) : EventoSocket

    /** 2 — mensagem nova. **Não traz `conversa_id`**: serve de gatilho para sincronizar. */
    data class NovaMensagem(val titulo: String, val texto: String) : EventoSocket

    /** 3 — mudou o status de mensagens (`mensagens` vem como CSV). */
    data class StatusMensagens(val conversaId: Long, val mensagens: List<Long>) : EventoSocket

    data class Digitando(val conversaId: Long, val usuarioId: Long) : EventoSocket

    data class GravandoAudio(val conversaId: Long, val usuarioId: Long) : EventoSocket

    data class Reacao(val conversaId: Long, val mensagemId: Long, val usuarioId: Long, val emoji: String, val adicionada: Boolean) :
        EventoSocket

    /** 40 — conversa nova ou alterada (recarregar a lista). */
    data class ConversaAtualizada(val conversaId: Long, val usuarioId: Long?) : EventoSocket

    data class ChamadaRecebida(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    data class ChamadaFinalizada(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    data class UsuarioRecusou(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    data class UsuarioEntrou(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    data class UsuarioSaiu(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    data class VideoAtivado(val chamadaId: Long, val usuarioId: Long) : EventoSocket

    /** 57 — sinal entre participantes (tela, ponteiro, chat). */
    data class SinalChamada(val chamadaId: Long, val usuarioId: Long, val dados: JsonObject) : EventoSocket

    data class StatusUsuario(val usuarioId: Long, val online: Boolean) : EventoSocket

    /** 61 — atividade nova ou removida (recarregar o contador). */
    data object NovaAtividade : EventoSocket

    /** 62 — alguém votou numa enquete. */
    data class EnqueteAtualizada(val enqueteId: Long, val conversaId: Long) : EventoSocket

    /** Tipo que o app ainda não conhece (o servidor evoluiu). */
    data class Desconhecido(val tipo: Int?, val json: String) : EventoSocket
}

object TipoSocket {
    const val ERRO = 0
    const val LOGIN = 1
    const val NOVA_MENSAGEM = 2
    const val STATUS_MENSAGEM = 3
    const val DIGITANDO = 4
    const val GRAVANDO_AUDIO = 5
    const val REACAO = 7
    const val ERRO_LEITURA = 9
    const val CONVERSA_ATUALIZADA = 40
    const val CHAMADA_RECEBIDA = 51
    const val CHAMADA_FINALIZADA = 52
    const val USUARIO_RECUSOU = 53
    const val USUARIO_ENTROU = 54
    const val USUARIO_SAIU = 55
    const val VIDEO_ATIVADO = 56
    const val SINAL_CHAMADA = 57
    const val STATUS_USUARIO = 60
    const val NOVA_ATIVIDADE = 61
    const val ENQUETE_ATUALIZADA = 62
}

/** Converte o texto recebido no socket em [EventoSocket]. Nunca lança exceção. */
object EventoSocketParser {
    fun ler(texto: String): EventoSocket {
        val obj = try {
            ConversaJson.parseToJsonElement(texto).jsonObject
        } catch (_: Exception) {
            return EventoSocket.Desconhecido(null, texto)
        }
        val tipo = obj.inteiro("tipo")
        return try {
            converter(tipo, obj) ?: EventoSocket.Desconhecido(tipo, texto)
        } catch (_: Exception) {
            EventoSocket.Desconhecido(tipo, texto)
        }
    }

    private fun converter(tipo: Int?, obj: JsonObject): EventoSocket? = when (tipo) {
        TipoSocket.ERRO -> EventoSocket.ErroLogin(obj.texto("message").orEmpty())
        TipoSocket.ERRO_LEITURA -> EventoSocket.ErroLeitura(obj.texto("message").orEmpty())
        TipoSocket.NOVA_MENSAGEM -> EventoSocket.NovaMensagem(obj.texto("titulo").orEmpty(), obj.texto("mensagem").orEmpty())
        TipoSocket.STATUS_MENSAGEM -> EventoSocket.StatusMensagens(
            conversaId = obj.longo("grupo") ?: return null,
            mensagens = obj.texto("mensagens").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() },
        )
        TipoSocket.DIGITANDO -> EventoSocket.Digitando(obj.longo("conversa_id") ?: return null, obj.longo("usuario_id") ?: return null)
        TipoSocket.GRAVANDO_AUDIO -> EventoSocket.GravandoAudio(
            obj.longo("conversa_id") ?: return null,
            obj.longo("usuario_id") ?: return null,
        )
        TipoSocket.REACAO -> EventoSocket.Reacao(
            conversaId = obj.longo("conversa_id") ?: return null,
            mensagemId = obj.longo("mensagem_id") ?: return null,
            usuarioId = obj.longo("usuario_id") ?: return null,
            emoji = obj.texto("emoji").orEmpty(),
            adicionada = obj.texto("acao") != "remove",
        )
        TipoSocket.CONVERSA_ATUALIZADA -> EventoSocket.ConversaAtualizada(obj.longo("conversa_id") ?: return null, obj.longo("usuario_id"))
        TipoSocket.CHAMADA_RECEBIDA -> chamada(obj, EventoSocket::ChamadaRecebida)
        TipoSocket.CHAMADA_FINALIZADA -> chamada(obj, EventoSocket::ChamadaFinalizada)
        TipoSocket.USUARIO_RECUSOU -> chamada(obj, EventoSocket::UsuarioRecusou)
        TipoSocket.USUARIO_ENTROU -> chamada(obj, EventoSocket::UsuarioEntrou)
        TipoSocket.USUARIO_SAIU -> chamada(obj, EventoSocket::UsuarioSaiu)
        TipoSocket.VIDEO_ATIVADO -> chamada(obj, EventoSocket::VideoAtivado)
        TipoSocket.SINAL_CHAMADA -> EventoSocket.SinalChamada(
            chamadaId = obj.longo("chamada_id") ?: return null,
            usuarioId = obj.longo("usuario_id") ?: return null,
            dados = obj["dados"] as? JsonObject ?: JsonObject(emptyMap()),
        )
        TipoSocket.STATUS_USUARIO -> EventoSocket.StatusUsuario(
            usuarioId = obj.longo("usuario_id") ?: return null,
            online = obj["online"]?.jsonPrimitive?.booleanOrNull ?: return null,
        )
        TipoSocket.NOVA_ATIVIDADE -> EventoSocket.NovaAtividade
        TipoSocket.ENQUETE_ATUALIZADA -> EventoSocket.EnqueteAtualizada(
            enqueteId = obj.longo("enquete_id") ?: return null,
            conversaId = obj.longo("conversa_id") ?: return null,
        )
        else -> null
    }

    private inline fun chamada(obj: JsonObject, criar: (Long, Long) -> EventoSocket): EventoSocket? {
        val chamadaId = obj.longo("chamada_id") ?: return null
        val usuarioId = obj.longo("usuario_id") ?: return null
        return criar(chamadaId, usuarioId)
    }

    private fun JsonObject.texto(campo: String): String? = (this[campo] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonObject.longo(campo: String): Long? {
        val primitivo = this[campo] as? kotlinx.serialization.json.JsonPrimitive ?: return null
        return primitivo.longOrNull ?: primitivo.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.inteiro(campo: String): Int? = (this[campo] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
}
