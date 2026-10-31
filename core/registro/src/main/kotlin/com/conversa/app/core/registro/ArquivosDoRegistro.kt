package com.conversa.app.core.registro

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Um arquivo do registro: o dia e a parte (um dia grande demais continua em `AAAA-MM-DD.2.log`). */
data class ArquivoDoRegistro(val arquivo: File, val dia: LocalDate, val parte: Int)

/**
 * Os arquivos do registro do app, um por dia em [pasta] (`AAAA-MM-DD.log`), sem nada de Android
 * (os testes rodam na JVM). Guarda os últimos [dias] dias, contando hoje; um dia passa para a parte
 * seguinte ao chegar em [limiteArquivo], e o total nunca passa de [limiteTotal] (sai o mais antigo).
 *
 * Quem escreve é uma thread só ([Registro]); a falha fatal escreve da thread dela. Por isso tudo
 * que mexe no arquivo aberto é `@Synchronized`.
 */
class ArquivosDoRegistro(
    val pasta: File,
    private val relogio: Clock = Clock.systemDefaultZone(),
    private val limiteArquivo: Long = LIMITE_ARQUIVO,
    private val limiteTotal: Long = LIMITE_TOTAL,
    private val dias: Long = DIAS,
) {
    private var saida: OutputStream? = null
    private var aberto: File? = null
    private var diaAberto: LocalDate? = null
    private var tamanhoAberto = 0L

    /** Acrescenta as linhas no arquivo de hoje e grava no disco. Um erro de disco descarta o lote. */
    @Synchronized
    fun escrever(linhas: List<String>) {
        if (linhas.isEmpty()) return
        try {
            val hoje = LocalDate.now(relogio)
            if (saida == null || hoje != diaAberto) abrir(hoje, proximaParte = false)
            for (linha in linhas) {
                if (tamanhoAberto >= limiteArquivo) abrir(hoje, proximaParte = true)
                val bytes = (linha + "\n").toByteArray(Charsets.UTF_8)
                saida?.write(bytes)
                tamanhoAberto += bytes.size
            }
            saida?.flush()
        } catch (_: IOException) {
            // Sem onde registrar a falha do próprio registro: fecha e tenta abrir de novo no próximo lote.
            fechar()
        }
    }

    /** Apaga o que passou de [dias] dias e, se ainda passar de [limiteTotal], os mais antigos. */
    @Synchronized
    fun limparAntigos() {
        val primeiroDia = LocalDate.now(relogio).minusDays(dias - 1)
        val (velhos, recentes) = lista().partition { it.dia < primeiroDia }
        velhos.forEach { it.arquivo.delete() }
        var total = recentes.sumOf { it.arquivo.length() }
        for (a in recentes) {
            if (total <= limiteTotal) break
            if (a.arquivo == aberto) continue
            total -= a.arquivo.length()
            a.arquivo.delete()
        }
    }

    /** Os arquivos, do mais antigo ao mais novo. */
    fun lista(): List<ArquivoDoRegistro> = pasta.listFiles().orEmpty()
        .mapNotNull(::lerNome)
        .sortedWith(compareBy({ it.dia }, { it.parte }))

    fun tamanhoTotal(): Long = lista().sumOf { it.arquivo.length() }

    /** As últimas [maximo] linhas, da mais antiga à mais nova (pode começar no meio de uma entrada). */
    @Synchronized
    fun ultimasLinhas(maximo: Int): List<String> {
        val resultado = ArrayDeque<String>()
        for (a in lista().asReversed()) {
            val linhas = runCatching { a.arquivo.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
            for (linha in linhas.asReversed()) {
                if (resultado.size >= maximo) return resultado.toList()
                resultado.addFirst(linha)
            }
        }
        return resultado.toList()
    }

    /** Junta todos os arquivos num zip em [destino] (para compartilhar). */
    @Synchronized
    fun compactar(destino: File): File {
        destino.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destino))).use { zip ->
            for (a in lista()) {
                zip.putNextEntry(ZipEntry(a.arquivo.name))
                a.arquivo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return destino
    }

    /** Apaga todo o registro (a pessoa pediu). O próximo lote começa um arquivo novo. */
    @Synchronized
    fun apagarTudo() {
        fechar()
        lista().forEach { it.arquivo.delete() }
    }

    @Synchronized
    fun fechar() {
        runCatching { saida?.close() }
        saida = null
        aberto = null
        diaAberto = null
        tamanhoAberto = 0
    }

    private fun abrir(dia: LocalDate, proximaParte: Boolean) {
        fechar()
        pasta.mkdirs()
        val ultima = lista().lastOrNull { it.dia == dia }
        val arquivo = when {
            ultima == null -> File(pasta, nomeDoArquivo(dia, 1))
            proximaParte || ultima.arquivo.length() >= limiteArquivo -> File(pasta, nomeDoArquivo(dia, ultima.parte + 1))
            else -> ultima.arquivo
        }
        saida = BufferedOutputStream(FileOutputStream(arquivo, true))
        aberto = arquivo
        diaAberto = dia
        tamanhoAberto = arquivo.length()
        limparAntigos()
    }

    companion object {
        /** Sete dias, contando hoje. */
        const val DIAS = 7L

        /** Um dia de uso normal fica bem abaixo disto; passou, continua na parte seguinte. */
        const val LIMITE_ARQUIVO = 5L * 1024 * 1024

        /** Teto de segurança para tudo junto (um laço de erros não enche o aparelho). */
        const val LIMITE_TOTAL = 60L * 1024 * 1024

        fun nomeDoArquivo(dia: LocalDate, parte: Int): String = if (parte <= 1) "$dia.log" else "$dia.$parte.log"

        /** `AAAA-MM-DD.log` ou `AAAA-MM-DD.N.log`; qualquer outro nome não é do registro. */
        fun lerNome(arquivo: File): ArquivoDoRegistro? {
            if (!arquivo.isFile || !arquivo.name.endsWith(".log")) return null
            val partes = arquivo.name.removeSuffix(".log").split('.')
            if (partes.size > 2) return null
            val dia = runCatching { LocalDate.parse(partes[0]) }.getOrNull() ?: return null
            val parte = if (partes.size == 1) 1 else partes[1].toIntOrNull()?.takeIf { it >= 2 } ?: return null
            return ArquivoDoRegistro(arquivo, dia, parte)
        }
    }
}
