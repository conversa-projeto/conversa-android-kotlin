package com.conversa.app.core.ui.tema

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Cores do app, tiradas do cliente desktop conversa-windows-fmx.
// Referência e regras: docs/design/cores.md. Toda cor nova vem de lá;
// não usar as cores do web nem dynamicColor.

internal val AzulConversa = Color(0xFF007DFF)
private val Branco = Color(0xFFFFFFFF)
private val FundoApp = Color(0xFFF5F5F5)
private val CinzaTopo = Color(0xFFF0F0F0)
private val CinzaCampo = Color(0xFFE0E0E0)
private val CinzaRail = Color(0xFFC8C8C8)
private val Divisor = Color(0xFFDBDBDB)
private val TextoPrimario = Color(0xFF141414)
private val TextoSecundario = Color(0xFF646464)
private val VermelhoErro = Color(0xFFE53935)

internal val EsquemaClaro = lightColorScheme(
    primary = AzulConversa,
    onPrimary = Branco,
    primaryContainer = Color(0xFFCFE7FF),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF9CCAFF),
    secondary = TextoSecundario,
    onSecondary = Branco,
    secondaryContainer = Color(0xFFE3F1FF),
    onSecondaryContainer = TextoPrimario,
    tertiary = Color(0xFF43A047),
    onTertiary = Branco,
    background = FundoApp,
    onBackground = TextoPrimario,
    surface = Branco,
    onSurface = TextoPrimario,
    surfaceVariant = CinzaTopo,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = Branco,
    surfaceContainerLow = FundoApp,
    surfaceContainer = CinzaTopo,
    surfaceContainerHigh = CinzaCampo,
    surfaceContainerHighest = CinzaRail,
    outline = CinzaRail,
    outlineVariant = Divisor,
    error = VermelhoErro,
    onError = Branco,
    errorContainer = Color(0xFFF08080),
    onErrorContainer = Color(0xFF410002),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF333333),
    inverseOnSurface = FundoApp,
)

/** PROPOSTA: o FMX não tem modo escuro ativo (base: bin/tema/escuro.pss). Só ligar com aprovação. */
internal val EsquemaEscuro = darkColorScheme(
    primary = AzulConversa,
    onPrimary = Branco,
    primaryContainer = Color(0xFF16406B),
    onPrimaryContainer = Color(0xFFE3F1FF),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF2B3A4A),
    onSecondaryContainer = Color(0xFFF0F0F0),
    tertiary = Color(0xFF66BB6A),
    background = Color(0xFF323232),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF3C3C3C),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainerHigh = Color(0xFF464646),
    surfaceContainerHighest = Color(0xFF282828),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF464646),
    error = Color(0xFFEF5350),
    onError = Branco,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Cores próprias do app, sem papel no Material 3 (bolhas, badges, chamadas…). */
@Immutable
data class ConversaCores(
    val bolhaPropria: Color,
    val bolhaOutro: Color,
    val textoBolhaPropria: Color,
    val textoBolhaOutro: Color,
    val horaBolha: Color,
    val statusEntregue: Color,
    val statusLida: Color,
    val link: Color,
    val badgeNaoLidas: Color,
    val textoBadge: Color,
    val itemSelecionado: Color,
    val itemPressionado: Color,
    val divisorLista: Color,
    val separadorNaoLidas: Color,
    val separadorData: Color,
    val campoEntrada: Color,
    val textoCampo: Color,
    val iconeAcao: Color,
    val iconeDiscreto: Color,
    val avatarFundo: Color,
    val avatarLetra: Color,
    val textoTerciario: Color,
    val gravandoAudio: Color,
    val chamadaFundo: Color,
    val chamadaBarraInferior: Color,
    val chamadaBotao: Color,
    val chamadaIconeBotao: Color,
    val chamadaEncerrar: Color,
    val chamadaAtender: Color,
    val chamadaEmAndamento: Color,
    val waveform: Color,
    val chamadaRealizada: Color,
    val chamadaRecebida: Color,
    val chamadaPerdida: Color,
    val botaoRecusar: Color,
    val botaoAtender: Color,
    val avisoConexao: Color,
    val overlayModal: Color,
    val fundoVisualizadorMidia: Color,
    /** PROPOSTA provisória: o FMX não tem indicador online (docs/design/cores.md §9). */
    val online: Color,
)

internal val ConversaCoresClaro = ConversaCores(
    bolhaPropria = Color(0xFFCFE7FF),
    bolhaOutro = Color(0xFFEDEDED),
    textoBolhaPropria = Color(0xFF000000),
    textoBolhaOutro = Color(0xFF000000),
    horaBolha = Color(0x80000000),
    statusEntregue = Color(0xFF808080),
    statusLida = AzulConversa,
    link = Color(0xFF0000EE),
    badgeNaoLidas = AzulConversa,
    textoBadge = Branco,
    itemSelecionado = Color(0xFFE3F1FF),
    itemPressionado = Color(0xFFE6E6E6),
    divisorLista = Color(0x32000000),
    separadorNaoLidas = Color(0xFFB0C4DE),
    separadorData = CinzaCampo,
    campoEntrada = CinzaCampo,
    textoCampo = Color(0xFF545454),
    iconeAcao = AzulConversa,
    iconeDiscreto = Color(0xFF808080),
    avatarFundo = FundoApp,
    avatarLetra = AzulConversa,
    textoTerciario = Color(0xFF9E9E9E),
    gravandoAudio = Color(0xFFFF0000),
    chamadaFundo = FundoApp,
    chamadaBarraInferior = CinzaCampo,
    chamadaBotao = Branco,
    chamadaIconeBotao = Color(0xFF000000),
    chamadaEncerrar = Color(0xFFD44242),
    chamadaAtender = Color(0xFF008000),
    chamadaEmAndamento = Color(0xFF85FF85),
    waveform = Color(0xFF00D26A),
    chamadaRealizada = AzulConversa,
    chamadaRecebida = Color(0xFF00C853),
    chamadaPerdida = Color(0xFFFF5252),
    botaoRecusar = VermelhoErro,
    botaoAtender = Color(0xFF43A047),
    avisoConexao = Color(0xFFF08080),
    overlayModal = Color(0x64000000),
    fundoVisualizadorMidia = Color(0xC8000000),
    online = Color(0xFF43A047),
)

/** PROPOSTA (o FMX não tem modo escuro). */
internal val ConversaCoresEscuro = ConversaCoresClaro.copy(
    bolhaPropria = Color(0xFF16406B),
    bolhaOutro = Color(0xFF3C3C3C),
    textoBolhaPropria = Color(0xFFF0F0F0),
    textoBolhaOutro = Color(0xFFF0F0F0),
    horaBolha = Color(0x99FFFFFF),
    statusEntregue = Color(0xFF9E9E9E),
    statusLida = Color(0xFF4DA3FF),
    link = Color(0xFF8AB4F8),
    itemSelecionado = Color(0xFF2B3A4A),
    itemPressionado = Color(0xFF464646),
    divisorLista = Color(0x33FFFFFF),
    separadorNaoLidas = Color(0xFF3A4A5E),
    separadorData = Color(0xFF464646),
    campoEntrada = Color(0xFF464646),
    textoCampo = Color(0xFFDCDCDC),
    iconeDiscreto = Color(0xFF9E9E9E),
    avatarFundo = Color(0xFF464646),
    avatarLetra = Color(0xFF4DA3FF),
    textoTerciario = Color(0xFF8C8C8C),
    gravandoAudio = Color(0xFFFF5252),
    chamadaFundo = Color(0xFF282828),
    chamadaBarraInferior = Color(0xFF3C3C3C),
    chamadaBotao = Color(0xFF464646),
    chamadaIconeBotao = Color(0xFFF0F0F0),
    chamadaAtender = Color(0xFF43A047),
    chamadaEmAndamento = Color(0xFF2E7D32),
    chamadaRealizada = Color(0xFF4DA3FF),
    avisoConexao = Color(0xFF8C1D18),
)

val LocalConversaCores = staticCompositionLocalOf { ConversaCoresClaro }
