package com.conversa.app.core.model

// Qualidade das chamadas (8.5, FC-807, CFG-06/CHA-20), como o `useConfigChamada.ts` do web:
// o que este aparelho envia. Os padrões são os do web no celular.

enum class QualidadeAudio(val chave: String, val bitrate: Int, val estereo: Boolean) {
    NORMAL("normal", 32_000, false),
    ALTA("alta", 64_000, false),
    MUSICA("musica", 128_000, true),
}

enum class ResolucaoVideo(val chave: String, val largura: Int, val altura: Int) {
    P360("360", 640, 360),
    P720("720", 1280, 720),
    P1080("1080", 1920, 1080),
}

/** Teto de bitrate do vídeo; nulo = o WebRTC ajusta sozinho. */
enum class BandaVideo(val chave: String, val bitrate: Int?) {
    AUTOMATICA("auto", null),
    ECONOMICA("economico", 500_000),
    ALTA("alto", 3_000_000),
}

/** Quadros por segundo que dá para escolher. */
val QUADROS_POR_SEGUNDO = listOf(15, 24, 30)

data class ConfigChamada(
    val reducaoRuido: Boolean = true,
    val cancelamentoEco: Boolean = true,
    val ganhoAutomatico: Boolean = true,
    val qualidadeAudio: QualidadeAudio = QualidadeAudio.NORMAL,
    val resolucao: ResolucaoVideo = ResolucaoVideo.P360,
    val quadros: Int = 15,
    val banda: BandaVideo = BandaVideo.AUTOMATICA,
) {
    /** Para guardar nas preferências: `ruido=1;eco=1;…`. */
    fun paraTexto(): String = listOf(
        RUIDO to bit(reducaoRuido),
        ECO to bit(cancelamentoEco),
        GANHO to bit(ganhoAutomatico),
        AUDIO to qualidadeAudio.chave,
        RESOLUCAO to resolucao.chave,
        QUADROS to quadros.toString(),
        BANDA to banda.chave,
    ).joinToString(";") { (chave, valor) -> "$chave=$valor" }

    companion object {
        val PADRAO = ConfigChamada()

        private const val RUIDO = "ruido"
        private const val ECO = "eco"
        private const val GANHO = "ganho"
        private const val AUDIO = "audio"
        private const val RESOLUCAO = "resolucao"
        private const val QUADROS = "fps"
        private const val BANDA = "banda"

        private fun bit(ligado: Boolean) = if (ligado) "1" else "0"

        /**
         * O texto guardado de volta. O que faltar ou não for reconhecido fica no padrão
         * (como o `{ ...padrao(), ...salvo }` do web): uma versão nova não perde o resto.
         */
        fun deTexto(texto: String?): ConfigChamada {
            val valores = texto.orEmpty().split(';').mapNotNull { par ->
                val i = par.indexOf('=')
                if (i <= 0) null else par.substring(0, i).trim() to par.substring(i + 1).trim()
            }.toMap()

            fun ligado(chave: String, padrao: Boolean) = when (valores[chave]) {
                "1" -> true
                "0" -> false
                else -> padrao
            }
            return ConfigChamada(
                reducaoRuido = ligado(RUIDO, PADRAO.reducaoRuido),
                cancelamentoEco = ligado(ECO, PADRAO.cancelamentoEco),
                ganhoAutomatico = ligado(GANHO, PADRAO.ganhoAutomatico),
                qualidadeAudio = QualidadeAudio.entries.firstOrNull { it.chave == valores[AUDIO] } ?: PADRAO.qualidadeAudio,
                resolucao = ResolucaoVideo.entries.firstOrNull { it.chave == valores[RESOLUCAO] } ?: PADRAO.resolucao,
                quadros = valores[QUADROS]?.toIntOrNull()?.takeIf { it in QUADROS_POR_SEGUNDO } ?: PADRAO.quadros,
                banda = BandaVideo.entries.firstOrNull { it.chave == valores[BANDA] } ?: PADRAO.banda,
            )
        }
    }
}

/** De onde a mídia da chamada lê a configuração na hora de abrir (as preferências do aparelho). */
interface FonteConfigChamada {
    suspend fun configChamada(): ConfigChamada
}
