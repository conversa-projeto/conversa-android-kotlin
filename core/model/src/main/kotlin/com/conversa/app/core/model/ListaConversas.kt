package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Regras puras da lista de conversas (CON-01, CON-02), separadas da tela para
// poder testar sem Android.

/** O que aparece na segunda linha de um item da lista. Os textos fixos ficam no strings.xml. */
sealed interface PreviaConversa {
    data object SemMensagens : PreviaConversa

    data object Imagem : PreviaConversa

    data object Figurinha : PreviaConversa

    /** Arquivo, áudio, gravação ou chamada: o servidor manda `""` (contrato §11.2). */
    data object SemTexto : PreviaConversa

    data class Texto(val texto: String) : PreviaConversa
}

/**
 * Prévia a partir de `ultima_mensagem_texto` (contrato §11.2): texto cru do primeiro
 * conteúdo, `"imagem"`, `"figurinha"`, `"Mensagem oculta"` ou `""`.
 * Diferença do web: lá, `""` com mensagem mostra "Sem mensagens"; aqui vira [PreviaConversa.SemTexto].
 */
fun previaDaConversa(conversa: Conversa): PreviaConversa {
    val bruto = conversa.ultimaMensagemTexto.orEmpty()
    val temMensagem = conversa.ultimaMensagemId > 0
    return when {
        bruto == "imagem" -> PreviaConversa.Imagem
        bruto == "figurinha" -> PreviaConversa.Figurinha
        bruto.isBlank() -> if (temMensagem) PreviaConversa.SemTexto else PreviaConversa.SemMensagens
        else -> resumirTexto(bruto).let { if (it.isEmpty()) PreviaConversa.SemTexto else PreviaConversa.Texto(it) }
    }
}

/** Rótulo de data do item: hoje → hora, ontem → "Ontem", senão `dd/MM/aa`. */
sealed interface RotuloData {
    data class Hora(val texto: String) : RotuloData

    data object Ontem : RotuloData

    data class Data(val texto: String) : RotuloData
}

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm")
private val FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yy")

fun rotuloData(instante: Instant, agora: Instant, zona: ZoneId): RotuloData {
    val data = instante.atZone(zona)
    val hoje = LocalDate.ofInstant(agora, zona)
    return when (data.toLocalDate()) {
        hoje -> RotuloData.Hora(FORMATO_HORA.format(data))
        hoje.minusDays(1) -> RotuloData.Ontem
        else -> RotuloData.Data(FORMATO_DATA.format(data))
    }
}

/**
 * Ordem da lista principal: fixadas por `fixada_ordem` (1 = primeira); depois as
 * demais pela última mensagem (`mensagem_id` desc), e as sem mensagem por id desc.
 */
fun ordenarConversas(conversas: List<Conversa>): List<Conversa> {
    val (fixadas, demais) = conversas.partition { it.fixada }
    return fixadas.sortedWith(compareBy<Conversa> { it.fixadaOrdem }.thenByDescending { it.id }) +
        demais.sortedWith(compareByDescending<Conversa> { it.ultimaMensagemId }.thenByDescending { it.id })
}

/** Busca sem diferenciar maiúsculas nem acentos. */
fun normalizarBusca(texto: String): String = java.text.Normalizer.normalize(texto.trim(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .lowercase()

/** Filtro da lista (CON-02): título e prévia. */
fun conversaCombina(conversa: Conversa, termo: String): Boolean {
    val alvo = normalizarBusca(termo)
    if (alvo.isEmpty()) return true
    val previa = (previaDaConversa(conversa) as? PreviaConversa.Texto)?.texto.orEmpty()
    return normalizarBusca(conversa.titulo).contains(alvo) || normalizarBusca(previa).contains(alvo)
}

/** Filtro de contatos (CON-02, CON-07): nome, login ou e-mail. */
fun contatoCombina(contato: Contato, termo: String): Boolean {
    val alvo = normalizarBusca(termo)
    if (alvo.isEmpty()) return true
    return listOfNotNull(contato.nome, contato.login, contato.email).any { normalizarBusca(it).contains(alvo) }
}

/** Conversa direta já existente com o contato (CON-06): `tipo = 1 && destinatario_id = contato`. */
fun List<Conversa>.diretaCom(usuarioId: Long): Conversa? = firstOrNull { it.tipo == TipoConversa.DIRETA && it.destinatarioId == usuarioId }

/**
 * Avatar de um contato (CON-11): `/usuario/contatos` não traz foto; usa a da conversa
 * direta com ele, quando existe.
 */
fun avatarDoContato(contato: Contato, conversas: List<Conversa>): String? =
    contato.avatarUrl?.takeIf { it.isNotBlank() } ?: conversas.diretaCom(contato.id)?.avatarUrl

/** Nova ordem das fixadas ao fixar [id]: entra no fim (CON-03). */
fun fixadasAoFixar(conversas: List<Conversa>, id: Long): List<Long> =
    fixadasAtuais(conversas).filter { it != id } + id

fun fixadasAoDesafixar(conversas: List<Conversa>, id: Long): List<Long> = fixadasAtuais(conversas).filter { it != id }

/** Move [id] uma posição para cima (`-1`) ou para baixo (`+1`) entre as fixadas; `null` se não muda. */
fun fixadasAoMover(conversas: List<Conversa>, id: Long, deslocamento: Int): List<Long>? {
    val atual = fixadasAtuais(conversas).toMutableList()
    val de = atual.indexOf(id)
    val para = de + deslocamento
    if (de < 0 || para !in atual.indices) return null
    atual.add(para, atual.removeAt(de))
    return atual
}

private fun fixadasAtuais(conversas: List<Conversa>): List<Long> =
    conversas.filter { it.fixada && !it.arquivada }.sortedBy { it.fixadaOrdem }.map { it.id }
