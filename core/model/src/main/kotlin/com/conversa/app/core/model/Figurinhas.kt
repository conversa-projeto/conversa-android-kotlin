package com.conversa.app.core.model

/*
 * Figurinhas animadas (ENV-04, TODO 7.8), como o `utils/figurinhas.ts` do web. As animações
 * Lottie ficam nos assets do app (`figurinhas/<pacote>/<nome>.json`, as mesmas do web); a
 * mensagem leva só o identificador "pacote/nome" (conteúdo tipo 7).
 *
 * Desde o web `d202c06` (2026-10-11) o seletor tem só o pacote "Noto" (TODO 8.7, FC-417). Os
 * pacotes antigos ficam só para desenhar as mensagens que já os usaram (o web mostra o texto
 * "Figurinha"; aqui a animação continua).
 */

data class Figurinha(val id: String, val nome: String)

/** Pacote de terceiros: o crédito que a licença pede, mostrado no seletor. */
data class CreditoFigurinhas(val texto: String, val link: String)

data class PacoteFigurinhas(val id: String, val nome: String, val figurinhas: List<Figurinha>, val credito: CreditoFigurinhas? = null)

/** "Noto" (55), gerado do `utils/figurinhasNoto.ts` do web — Animated Noto Emoji, © Google, CC BY 4.0. */
val PACOTE_NOTO = PacoteFigurinhas(
    "noto",
    "Noto",
    listOf(
        Figurinha("noto/chorando-de-rir", "Chorando de rir"),
        Figurinha("noto/rolando-de-rir", "Rolando de rir"),
        Figurinha("noto/sorriso", "Sorriso"),
        Figurinha("noto/ufa", "Ufa"),
        Figurinha("noto/piscadinha", "Piscadinha"),
        Figurinha("noto/apaixonado", "Apaixonado"),
        Figurinha("noto/cheio-de-amor", "Cheio de amor"),
        Figurinha("noto/beijinho", "Beijinho"),
        Figurinha("noto/legal", "Legal"),
        Figurinha("noto/deslumbrado", "Deslumbrado"),
        Figurinha("noto/festejando", "Festejando"),
        Figurinha("noto/abraco", "Abraço"),
        Figurinha("noto/ops", "Ops"),
        Figurinha("noto/pensando", "Pensando"),
        Figurinha("noto/desconfiado", "Desconfiado"),
        Figurinha("noto/revirando-os-olhos", "Revirando os olhos"),
        Figurinha("noto/de-cabeca-para-baixo", "De cabeça para baixo"),
        Figurinha("noto/envergonhado", "Envergonhado"),
        Figurinha("noto/por-favorzinho", "Por favorzinho"),
        Figurinha("noto/triste", "Triste"),
        Figurinha("noto/chorando", "Chorando"),
        Figurinha("noto/bravo", "Bravo"),
        Figurinha("noto/assustado", "Assustado"),
        Figurinha("noto/cabeca-explodindo", "Cabeça explodindo"),
        Figurinha("noto/bocejando", "Bocejando"),
        Figurinha("noto/dormindo", "Dormindo"),
        Figurinha("noto/doente", "Doente"),
        Figurinha("noto/morri", "Morri"),
        Figurinha("noto/palhaco", "Palhaço"),
        Figurinha("noto/fantasma", "Fantasma"),
        Figurinha("noto/joinha", "Joinha"),
        Figurinha("noto/negativo", "Negativo"),
        Figurinha("noto/palmas", "Palmas"),
        Figurinha("noto/aleluia", "Aleluia"),
        Figurinha("noto/por-favor", "Por favor"),
        Figurinha("noto/tchau", "Tchau"),
        Figurinha("noto/forca", "Força"),
        Figurinha("noto/combinado", "Combinado"),
        Figurinha("noto/paz", "Paz"),
        Figurinha("noto/de-olho", "De olho"),
        Figurinha("noto/coracao", "Coração"),
        Figurinha("noto/coracao-partido", "Coração partido"),
        Figurinha("noto/fogo", "Fogo"),
        Figurinha("noto/cem", "Cem"),
        Figurinha("noto/brilhos", "Brilhos"),
        Figurinha("noto/festa", "Festa"),
        Figurinha("noto/confete", "Confete"),
        Figurinha("noto/presente", "Presente"),
        Figurinha("noto/bolo", "Bolo"),
        Figurinha("noto/foguete", "Foguete"),
        Figurinha("noto/ideia", "Ideia"),
        Figurinha("noto/raio", "Raio"),
        Figurinha("noto/arco-iris", "Arco-íris"),
        Figurinha("noto/cafe", "Café"),
        Figurinha("noto/brinde", "Brinde"),
    ),
    credito = CreditoFigurinhas("Animated Noto Emoji © Google, CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
)

/** Os pacotes do seletor (o web só oferece o Noto). */
val PACOTES_FIGURINHAS: List<PacoteFigurinhas> = listOf(PACOTE_NOTO)

/** Pacotes que o web apagou em `d202c06`: só para desenhar as mensagens antigas. */
val PACOTES_ANTIGOS: List<PacoteFigurinhas> = listOf(
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
fun nomeFigurinha(id: String): String? =
    (PACOTES_FIGURINHAS + PACOTES_ANTIGOS).firstNotNullOfOrNull { pacote -> pacote.figurinhas.firstOrNull { it.id == id }?.nome }

/**
 * Caminho nos assets. Só para identificadores do catálogo: o conteúdo vem de outra pessoa e
 * não pode apontar para fora da pasta (`../`).
 */
fun caminhoFigurinha(id: String): String? = if (nomeFigurinha(id) != null) "figurinhas/$id.json" else null
