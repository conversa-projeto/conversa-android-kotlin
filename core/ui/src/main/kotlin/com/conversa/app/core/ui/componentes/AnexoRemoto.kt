package com.conversa.app.core.ui.componentes

/**
 * Modelo do Coil para um anexo do servidor, pelo identificador (SHA-256). A URL assinada
 * é obtida, e renovada se vencer, pelo `FetcherAnexo` do `:app` (ANX-14). O cache de
 * memória e de disco usa o identificador, que não muda, e não a URL, que muda a cada 10 min.
 */
data class AnexoRemoto(val identificador: String)
