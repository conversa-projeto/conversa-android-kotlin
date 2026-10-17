# 2026-10-17 · 03 · Localização e trajeto na conversa

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.12 (as quatro primeiras linhas); FC-319; MSG-25 (e o rótulo em CHA-18, NOT-02, ATV-01)
- **Branch:** `reescrita`
- **Commits:** `eaa4f42`

## Contexto
- **O servidor** (`ac710eb`, contrato §10.16) aceita o conteúdo **tipo 9**: um JSON dentro do texto do `conteudo`, com 1 ponto (local) ou 2 (trajeto), `rota` (polyline do OSRM), `distancia` (metros) e `duracao` (segundos).
- **O web** (`f351190`) desenha um mapa Leaflet na bolha (tiles do OSM), abre um visualizador com ruas ou satélite (Esri) e tem o link "Abrir no Google Maps".
- **No app,** a localização aparecia como conteúdo desconhecido ("•").

## O que foi feito
- **`core:model`:**
  - `Localizacao.kt`: `PontoMapa`, `Localizacao` e as fórmulas do web (`formatarDistancia`, `formatarTempoTrajeto`, `formatarCoordenadas`, `distanciaReta`, `nomePonto`, `resumoLocalizacao`, `linkMapa`, `lerCoordenadas`, `decodificarRota`). A linha do trajeto virou `DescricaoTrajeto`: o texto com palavras fica no `strings.xml`;
  - `ProjecaoMapa.kt`: Mercator dos tiles, `enquadrar` (como o `fitBounds` do Leaflet: um ponto no zoom 16; vários até o 17, com folga e espaço para o alfinete), as camadas com os endereços do web e o `enderecoDoTile`;
  - `TipoConteudo.LOCALIZACAO` (9), e o tipo nos resumos (citação, notificação, atividades, chat da ligação).
- **`core:network`:**
  - `LocalizacaoJson.kt`: `lerLocalizacao` e `localizacaoParaJson`. A ordem das chaves é a do web, com distância e duração inteiras e sem `null`, porque o servidor recusa `null`;
  - **User-Agent do app** em todas as requisições ("ConversaAndroid/2.0 …"): os tiles do OSM, o Nominatim e o OSRM públicos recusam o agente padrão das bibliotecas.
- **Mapa sem biblioteca** (`Mapa.kt`):
  - os tiles via Coil, a rota e os marcadores num `Canvas`: alfinete na cor principal no local; A em verde e B em vermelho no trajeto (as cores das chamadas, do FMX); reta tracejada sem rota;
  - os tiles são ampliados pela metade da densidade da tela: legíveis e ainda nítidos;
  - `EstadoMapa` com arrastar, pinça, toque duplo e toque (o ponto tocado, para a janela de enviar);
  - a atribuição "© OpenStreetMap" ou "Imagens © Esri, Maxar, Earthstar Geographics" no canto.
- **Bolha** (`LocalizacaoNaConversa.kt`):
  - o mapa de 264 × 160 dp (tocar amplia);
  - o nome do local, ou A e B com "Trajeto · 2,6 km em linha reta" / "… de carro";
  - "Abrir no Google Maps";
  - JSON que não se lê: só "Localização".
- **Visualizador:** em tela cheia, com o título (os nomes, "A → B"), o subtítulo (coordenadas ou a linha do trajeto), "Abrir no Google Maps", "Satélite"/"Mapa", o mapa interativo e, no trajeto, A e B com o nome inteiro.
- **Testes:**
  - `LocalizacaoTest`: fórmulas, coordenadas digitadas e polyline, com os casos do web;
  - `ProjecaoMapaTest`: ida e volta da projeção, enquadramento e endereços;
  - `LocalizacaoJsonTest`: leitura, escrita sem `null` e ida e volta;
  - o `lerCoordenadas` entrou no `RegexNoAndroidTest` (ICU).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck`, `textos-repetidos.mjs` e a compilação dos testes instrumentados. Passou.
- **No emulador (A), contra o servidor de dev:**
  - a conta de teste C mandou pela API um local (Praça da Sé) e um trajeto sem rota (Praça da Sé → MASP);
  - a lista mostrou a prévia "localização";
  - as bolhas mostraram o mapa com o alfinete, o nome e "Abrir no Google Maps"; no trajeto, A e B, a reta tracejada e "Trajeto · 2,6 km em linha reta";
  - o visualizador abriu, arrastou e trocou para o satélite, com a atribuição da Esri.
- **Na primeira versão,** o alfinete A saía pela borda de cima e as letras dos tiles ficavam miúdas: daí a folga do alfinete no enquadramento e a ampliação dos tiles.
- **Não deu para conferir no emulador:** a pinça (o adb não simula dois dedos) e o toque duplo (os toques pelo adb saem espaçados demais). Ficam para o aparelho.

## Decisões
- **Mapa próprio em Compose, sem biblioteca** (osmdroid ou MapLibre): só tiles, rota e marcadores. Assim não entra dependência nova nem tamanho, e o mesmo mapa serve à bolha, ao visualizador e à janela de enviar.
- **Bolha padrão com o mapa dentro,** em vez de um tipo de exibição novo: fica igual à do web na prática.
- **Sem o mapa escuro do web** (o filtro de inversão): os tiles ficam como são.

## Pendências
- Enviar localização ou trajeto (FC-528, TODO 4.12).
