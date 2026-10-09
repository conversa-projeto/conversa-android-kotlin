# 2026-10-08 · 35 · Etapa 7 — Markdown formatado nos blocos ```md

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade / dependência nova
- **Itens:** `TODO.md` 7.9 (Markdown); FC-510; MSG-15
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
No web (`MessageContent.vue`, `useMarkdown.ts`), um bloco ```` ```md ```` ou ```` ```markdown ```` aparece formatado (`marked`), com "Visualizar" e "Código" no cabeçalho. Os blocos de código do Android ([27](2026-10-08-27-etapa-7-blocos-de-codigo.md)) mostravam o Markdown como texto.

## O que foi feito
- **Dependência:** `com.mikepenz:multiplatform-markdown-renderer-m3` **0.45.0** (Apache 2.0) no `:feature:chat`, pelo `libs.versions.toml`.
  - É a do TODO ("compose-markdown"): Compose puro, com tabelas e listas do GFM.
  - Puxa só o parser `org.jetbrains:markdown` 0.7.9 e usa o Compose do próprio app.
- **`core:model`:** `ehLinguagemMarkdown` ("md"/"markdown", sem diferenciar maiúsculas), com teste no `DestaqueCodigoTest`.
- **`feature:chat` — `BlocoCodigo.kt`:**
  - o bloco ```md mostra o Markdown por padrão;
  - "Visualizar" | "Código" no cabeçalho, com "Código" mostrando o texto cru com destaque;
  - acima de 240 dp, recolhe com degradê e "Expandir código"/"Recolher código", como o resto, pelo modificador `recolhivel`, que mede o conteúdo inteiro e corta;
  - tamanhos de bolha, como o `CLASSES_MARKDOWN` do web (h1 em `titleLarge`, texto em `bodyMedium`);
  - cores só de tokens, inclusive os alertas do GitHub (`cores.md` §6.4).
- **`docs/design/cores.md` §6.4:** a tabela de cores do Markdown.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou. O lint só aponta versões novas de dependências.
- **Emulador:** o B mandou pela API "Resumo:" + um ```md com título, negrito, itálico, `código`, lista aninhada, lista numerada, tabela, citação e link.
  - Veio formatado e recolhido. Na primeira versão o título saiu enorme, porque a biblioteca usa `displayLarge`; foi corrigido para tamanhos de bolha.
  - "Expandir código" mostrou tudo, com a tabela e o link sublinhado na cor primária, e "Recolher código" apareceu embaixo.
  - "Código" mostrou o texto cru.

## Decisões
- **`multiplatform-markdown-renderer` em vez do Markwon:** Compose nativo (o Markwon é de View, precisaria de `AndroidView`), mantido e sem WebView.
- **Mermaid (P2) fica para depois:** precisa embutir o mermaid.js (uns 3 MB) num WebView offline. Até lá, ```mermaid aparece como bloco de código, e diagramas dentro do Markdown aparecem como código.

## Pendências
- Mermaid (P2), à espera da decisão sobre embutir o mermaid.js.
