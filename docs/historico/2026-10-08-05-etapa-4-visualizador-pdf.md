# 2026-10-08 · 05 · Etapa 4 — visualizador de PDF

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.10 (PDF); FC-411
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
No web, "Abrir" num PDF abre o `VisualizadorPdf.vue` (pdf.js): zoom, Baixar e Fechar. No Android o PDF abria em outro app.

## O que foi feito
- **`VisualizadorPdf`** (`feature/chat/VisualizadorPdf.kt`):
  - **`LeitorPdf`** desenha as páginas com o `PdfRenderer` do Android:
    - uma página por vez, com trava (exigência do `PdfRenderer`);
    - sob demanda, conforme a rolagem;
    - largura no máximo 1600 px;
    - cache LRU limitado a 48 MB;
    - o espaço de cada página fica reservado pela proporção antes de desenhar.
  - **Zoom:**
    - pinça até 4×;
    - duplo toque alterna entre 1× e 2×;
    - com zoom, arrastar move para os lados e a rolagem vertical continua funcionando.
  - **Barra:** nome, "Página X de Y", Baixar (Downloads/Conversa), Abrir com… e Fechar.
    - A barra tem duas camadas do fundo do visualizador de mídia do FMX, para ficar quase opaca usando só cores de `cores.md`. Isso porque o `chamadaFundo` no tema claro é cinza-claro.
  - **PDF protegido ou inválido:** "Não foi possível abrir o PDF. Tente baixar o arquivo." (texto do web) e o botão Abrir com….
- **`ChatViewModel.abrirPdf`** baixa o PDF para o cache e envia o evento `AbrirPdf`; a tela abre o visualizador. "Abrir" em outros tipos de arquivo continua usando outro app.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **205 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador**, com um PDF de 5 páginas gerado por script e enviado pelo B:
  - abriu com "Página 1 de 5";
  - rolando, chegou a "Página 4 de 5";
  - o duplo toque deu zoom de 2×.
- **Teste automatizado:** não há. O `PdfRenderer` precisa de Android de verdade, e o `connectedDebugAndroidTest` desinstala o app.

## Pendências
- Os outros itens da 4.10: colar imagem, HTML e economia de dados.
