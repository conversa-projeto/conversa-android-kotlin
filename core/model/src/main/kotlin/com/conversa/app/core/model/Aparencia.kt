package com.conversa.app.core.model

/**
 * Tema do app (8.5, CFG-02): seguir o sistema, sempre claro ou sempre escuro. O esquema
 * escuro ainda é a **proposta** do `docs/design/cores.md` §6.2 (o FMX não tem; pergunta 9):
 * por isso o padrão é [CLARO], e não o sistema como no web, até a paleta ser aprovada.
 */
enum class PreferenciaTema(val chave: String) {
    SISTEMA("sistema"),
    CLARO("claro"),
    ESCURO("escuro"),
    ;

    /** Escuro de fato, dado o modo do sistema agora. */
    fun escuro(sistemaEscuro: Boolean): Boolean = when (this) {
        SISTEMA -> sistemaEscuro
        CLARO -> false
        ESCURO -> true
    }

    companion object {
        /** Sem escolha (ou chave desconhecida). Vira [SISTEMA] quando o escuro for aprovado. */
        val PADRAO = CLARO

        /** Chave guardada → tema; desconhecida ou ausente = [PADRAO]. */
        fun de(chave: String?): PreferenciaTema = entries.firstOrNull { it.chave == chave } ?: PADRAO
    }
}
