package com.conversa.app.core.webrtc

private const val RTPMAP = "a=rtpmap:"

/**
 * Áudio "Música" (8.5): pede Opus estéreo na oferta, como o `sdpComOpusEstereo` do web
 * (`stereo=1;sprop-stereo=1` no `a=fmtp` do Opus). Sem Opus, ou já estéreo, fica igual.
 * Só com operações de texto (o regex do Android é o do ICU).
 */
internal fun comOpusEstereo(sdp: String): String {
    val quebra = if ("\r\n" in sdp) "\r\n" else "\n"
    val linhas = sdp.split(quebra)
    // "a=rtpmap:111 opus/48000/2" → "111"
    val tipo = linhas.firstNotNullOfOrNull { linha ->
        if (!linha.startsWith(RTPMAP)) return@firstNotNullOfOrNull null
        val resto = linha.substring(RTPMAP.length)
        val espaco = resto.indexOf(' ')
        if (espaco > 0 && resto.substring(espaco + 1).startsWith("opus/48000", ignoreCase = true)) resto.substring(0, espaco) else null
    } ?: return sdp
    val prefixo = "a=fmtp:$tipo "
    return linhas.joinToString(quebra) { linha ->
        if (linha.startsWith(prefixo) && "stereo=1" !in linha) "$linha;stereo=1;sprop-stereo=1" else linha
    }
}
