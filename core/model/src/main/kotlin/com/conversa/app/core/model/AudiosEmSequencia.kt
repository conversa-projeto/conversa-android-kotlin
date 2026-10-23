package com.conversa.app.core.model

/**
 * O áudio que toca depois que o de [mensagemId] (conteúdo [ordem]) termina (ANX-19; web
 * `useAudioPlayer`, `tocarProximo`): o seguinte na mesma mensagem ou, na mensagem logo abaixo (de
 * quem for, inclusive as minhas), o primeiro dela. Sem áudio nessa mensagem, a sequência para.
 * [mensagens] na ordem do chat (a mais antiga primeiro); mensagem oculta não tem áudio à mostra.
 */
fun proximoAudio(mensagens: List<Mensagem>, mensagemId: Long, ordem: Int): Pair<Mensagem, Conteudo>? {
    val i = mensagens.indexOfFirst { it.id == mensagemId }
    if (i < 0) return null
    val atual = mensagens[i]
    audiosDa(atual).firstOrNull { it.ordem > ordem }?.let { return atual to it }
    val abaixo = mensagens.getOrNull(i + 1) ?: return null
    return audiosDa(abaixo).firstOrNull()?.let { abaixo to it }
}

private fun audiosDa(mensagem: Mensagem): List<Conteudo> =
    if (mensagem.oculta) emptyList() else mensagem.conteudos.filter { it.tipo.ehAudio }.sortedBy { it.ordem }
