# 2026-10-15 · 20 · Zoom no ponto da pinça

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.4 (linha 🆕 `e33ecba` do zoom no ponto da pinça); FC-420; ANX-04
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O web** (`e33ecba`): o visualizador amplia no ponto do cursor.
- **O app:** a pinça e o duplo toque ampliavam em torno do centro da imagem. Quem pinçava num canto via o canto fugir da tela.

## O que foi feito
- **`ZoomImagem`** (`Anexos.kt`), a escala e o deslocamento da imagem. O `aplicar(foco, mudanca, mover, tamanho)`:
  - amplia em torno do foco: o ponto da imagem sob o foco continua sob ele;
  - soma o arrasto;
  - não deixa a imagem ampliada sair da área (em 2x, no máximo meia tela de cada lado);
  - em 1x volta ao centro; vai até 5x, como antes.
- **`ImagemComZoom`:**
  - a pinça usa o `rememberTransformableState` com o centro da pinça (Compose 1.12);
  - o duplo toque amplia 2,5x no ponto tocado (antes, no centro) e o segundo volta a 1x;
  - sem zoom, o arrasto continua com o pager.
- **Testes** (`ZoomImagemTest`):
  - a pinça no centro não desloca;
  - fora do centro, o ponto fica sob os dedos, também numa segunda pinça já ampliada;
  - o arrasto é limitado;
  - 1x volta ao centro e o zoom para em 5x.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (515 testes), `:app:lintDebug` (só os 3 avisos de sempre) e `ktlintCheck`. Passou.
- **No emulador (A):** Grupo de teste 2 › Ver anexos › a foto da casinha.
  - Um duplo toque na porta, à direita e abaixo do centro, ampliou 2,5x com a porta no mesmo lugar da tela. Em torno do centro, ela sairia pela direita.
  - Outro duplo toque voltou a 1x.
- **A pinça de dois dedos** não dá para simular pelo `adb`, que só toca com um dedo. A conta é a mesma do duplo toque e está coberta pelos testes.

## Decisões
- **O arrasto fica limitado à área da imagem.** Antes não havia limite, e a imagem ampliada podia sumir da tela.
