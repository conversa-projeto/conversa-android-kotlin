package com.conversa.app.core.model

/*
 * Figurinhas animadas (ENV-04, TODO 7.8), como o `utils/figurinhas.ts` do web. As animações
 * Lottie ficam nos assets do app (`figurinhas/<pacote>/<nome>.json`, as mesmas do web); a
 * mensagem leva só o identificador "pacote/nome" (conteúdo tipo 7).
 */

data class Figurinha(val id: String, val nome: String)

data class PacoteFigurinhas(val id: String, val nome: String, val figurinhas: List<Figurinha>)

val PACOTES_FIGURINHAS: List<PacoteFigurinhas> = listOf(
    PacoteFigurinhas(
        "basico",
        "Básico",
        listOf(
            Figurinha("basico/coracao", "Coração"),
            Figurinha("basico/sorriso", "Sorriso"),
            Figurinha("basico/risada", "Risada"),
            Figurinha("basico/estrela", "Estrela"),
            Figurinha("basico/feito", "Feito"),
            Figurinha("basico/festa", "Festa"),
            Figurinha("basico/sono", "Sono"),
            Figurinha("basico/pontinhos", "Pontinhos"),
        ),
    ),
    PacoteFigurinhas(
        "rostos",
        "Rostos",
        listOf(
            Figurinha("rostos/piscadinha", "Piscadinha"),
            Figurinha("rostos/apaixonado", "Apaixonado"),
            Figurinha("rostos/beijo", "Beijo"),
            Figurinha("rostos/legal", "Legal"),
            Figurinha("rostos/surpreso", "Surpreso"),
            Figurinha("rostos/triste", "Triste"),
            Figurinha("rostos/bravo", "Bravo"),
        ),
    ),
    PacoteFigurinhas(
        "coisas",
        "Coisas",
        listOf(
            Figurinha("coisas/joinha", "Joinha"),
            Figurinha("coisas/fogo", "Fogo"),
            Figurinha("coisas/coracao-partido", "Coração partido"),
            Figurinha("coisas/presente", "Presente"),
            Figurinha("coisas/bolo", "Bolo"),
            Figurinha("coisas/balao", "Balão"),
            Figurinha("coisas/cafe", "Café"),
            Figurinha("coisas/sol", "Sol"),
            Figurinha("coisas/chuva", "Chuva"),
        ),
    ),
)

/** O nome da figurinha (para o TalkBack e a dica); desconhecida → nulo (a tela diz "Figurinha"). */
fun nomeFigurinha(
    id: String,
): String? = PACOTES_FIGURINHAS.firstNotNullOfOrNull { pacote -> pacote.figurinhas.firstOrNull { it.id == id }?.nome }

/**
 * Caminho nos assets. Só para identificadores do catálogo: o conteúdo vem de outra pessoa e
 * não pode apontar para fora da pasta (`../`).
 */
fun caminhoFigurinha(id: String): String? = if (nomeFigurinha(id) != null) "figurinhas/$id.json" else null
