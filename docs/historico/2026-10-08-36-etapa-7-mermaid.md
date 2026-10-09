# 2026-10-08 · 36 · Etapa 7 — diagramas Mermaid offline

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade / asset novo
- **Itens:** `TODO.md` 7.9 (mermaid, P2); FC-511; MSG-15
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
No web (`useMermaid.ts`), um bloco ```` ```mermaid ```` vira diagrama, com "Visualizar"/"Código". Os blocos ```mermaid que vêm dentro de um ```md também viram diagrama. Diagrama inválido fica como código. O usuário autorizou embutir o mermaid no app (pedido de 2026-10-08).

## O que foi feito
- **Assets (`feature/chat/src/main/assets/mermaid/`):**
  - `mermaid.min.js` **12.0.0** (MIT), a mesma versão do `bun.lock` do web. Veio do registro do npm (`mermaid-12.0.0.tgz`), conferido pelo sha512 do `bun.lock` antes de usar.
  - Ocupa 5,5 MB, ou 1,87 MB compactado no APK.
  - `LICENSE-mermaid.txt` e `diagrama.html`, a página que desenha com `securityLevel: 'strict'` e avisa a altura ou a falha.
- **`feature:chat` — `DiagramaMermaid.kt`:** WebView isolado.
  - Só carrega a página dos assets: sem rede (`blockNetworkLoads`), sem acesso a arquivos ou conteúdo, e sem navegar.
  - O texto do diagrama vai como literal JSON (`JSONObject.quote`).
  - Cores só de tokens, como `themeVariables` (`cores.md` §6.4).
  - A altura volta pela ponte `Conversa` (1 px de CSS = 1 dp).
- **`BlocoCodigo.kt`:**
  - ```mermaid mostra o diagrama com "Visualizar"/"Código", recolhido acima de 240 dp;
  - diagrama inválido fica como código, sem a alternância;
  - dentro do Markdown, o código cercado `mermaid` vira diagrama (`componentesDoMarkdown`), e o inválido fica como código.
- **`core:model`:** `ehLinguagemMermaid`, com teste no `DestaqueCodigoTest`.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou. O lint só aponta versões novas.
- **Emulador:** o B mandou, pela API:
  - **um fluxograma ```mermaid:** apareceu desenhado e recolhido, com "Expandir código";
  - **um ```md com um `sequenceDiagram` dentro:** o título e o texto saíram formatados, com o diagrama de sequência no meio;
  - **um ```mermaid inválido:** ficou como código, sem "Visualizar".

## Decisões
- **WebView em vez de desenhar em Compose:** não há renderizador Mermaid nativo; é a mesma biblioteca do web, sem rede.
- **`addJavascriptInterface` só com dois métodos (altura e falha), numa página local:** o diagrama roda no modo `strict` do mermaid, sem script nem link.

## Pendências
- Nenhuma da etapa 7.
