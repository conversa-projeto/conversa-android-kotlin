package com.conversa.app.core.ui.componentes

/**
 * Modelo do Coil para o primeiro quadro de um vídeo (ANX-04): [conteudo] é o
 * identificador do anexo ou `local:<uri>` (ainda enviando). Carregado pelo
 * `FetcherQuadroVideo` do `:app`, que lê só o necessário do vídeo (não baixa tudo).
 */
data class QuadroVideo(val conteudo: String)
