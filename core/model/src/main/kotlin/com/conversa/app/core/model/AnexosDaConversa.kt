package com.conversa.app.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Anexos da conversa (8.6, FC-809, ANX-13), como a `AnexosLista.vue` do web: `GET /anexos`.

/** "Todos / Enviados / Recebidos" (o `direcao` da rota). */
enum class DirecaoAnexos(val chave: String) {
    TODOS(""),
    ENVIADOS("enviados"),
    RECEBIDOS("recebidos"),
}

/** "Todos / Imagens / Arquivos / Áudios / Gravações" (o `tipos` da rota). Vídeo é arquivo. */
enum class FiltroAnexos(val tipos: List<TipoConteudo>) {
    TODOS(listOf(TipoConteudo.IMAGEM, TipoConteudo.ARQUIVO, TipoConteudo.AUDIO, TipoConteudo.GRAVACAO_AUDIO)),
    IMAGENS(listOf(TipoConteudo.IMAGEM)),
    ARQUIVOS(listOf(TipoConteudo.ARQUIVO)),
    AUDIOS(listOf(TipoConteudo.AUDIO)),
    GRAVACOES(listOf(TipoConteudo.GRAVACAO_AUDIO)),
    ;

    /** CSV da rota: "2,3,4,5". */
    val parametro: String get() = tipos.joinToString(",") { it.codigo.toString() }
}

/** Página do web: 60 por vez; menos que isso = acabou. */
const val LIMITE_ANEXOS = 60

/** Um anexo da galeria. O mesmo anexo enviado em várias mensagens vem uma vez por mensagem. */
data class AnexoDaConversa(
    val anexoId: Long,
    val identificador: String,
    val nome: String,
    val extensao: String,
    val tamanho: Long,
    val criadoEm: Instant?,
    val tipo: TipoConteudo,
    val mensagemId: Long,
    val conversaId: Long,
    val autorNome: String,
) {
    /** Como conteúdo de mensagem: o visualizador, o "abrir" e o "baixar" do chat servem para ele. */
    fun paraConteudo(): Conteudo = Conteudo(id = null, ordem = 0, tipo = tipo, conteudo = identificador, nome = nome, extensao = extensao)
}

private val HORA = DateTimeFormatter.ofPattern("HH:mm")
private val DIA = DateTimeFormatter.ofPattern("dd/MM/yy")

/** Como o web (`formatarDataCurta`): hoje "HH:mm", senão "dd/MM/aa". */
fun dataDoAnexo(em: Instant, zona: ZoneId = ZoneId.systemDefault(), hoje: LocalDate = LocalDate.now(zona)): String {
    val local = em.atZone(zona)
    return if (local.toLocalDate() == hoje) HORA.format(local) else DIA.format(local)
}

/** Fim da lista: veio menos que uma página. */
fun acabouAPagina(recebidos: Int): Boolean = recebidos < LIMITE_ANEXOS
