# 2026-10-08 · 27 · Etapa 7 — blocos de código (cabeçalho, copiar, destaque e recolher)

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade / documentação (cores)
- **Itens:** `TODO.md` 7.9 (menos o Markdown renderizado e o mermaid); FC-510; MSG-15
- **Branch:** `reescrita`
- **Commits:** `74ce225`

## Contexto
No web, o bloco de código (`MessageContent.vue`, `useCodeHighlight.ts`) tem:
- um cabeçalho com a linguagem e "Copiar"/"Copiado!";
- destaque do highlight.js;
- recolhimento acima de 240 px, com degradê e "Expandir código"/"Recolher código".

No Android, a bolha mostrava a linguagem e o código monoespaçado, sem cor nem ações. O parser (`codeBlocks.ts`) já tinha sido portado na etapa 3.

## O que foi feito
- **`core:model` — `DestaqueCodigo.kt`:** tokenizador próprio para as linguagens que o web registra: js/ts, python, sql, json, xml/html, css, bash/sh, csharp/cs e pascal/delphi.
  - Marca palavra-chave, texto entre aspas, comentário, número, tag e atributo.
  - Não usa biblioteca nem regex (o regex do Android é o do ICU).
  - A biblioteca leve avaliada (`dev.snipme:highlights`) não cobre SQL, JSON, XML, CSS nem Pascal.
- **`feature:chat` — `BlocoCodigo.kt`**, no lugar do bloco antigo do `Bolhas.kt`:
  - cabeçalho com a linguagem ("code" sem ela) e "Copiar" → "Copiado!" por 2 s;
  - o código colorido, com rolagem horizontal;
  - acima de 240 dp, recolhido com degradê e "Expandir código"/"Recolher código".
- **`docs/design/cores.md`:**
  - nova §6.4: o destaque reaproveita tokens que já existem (azul, `chamadaAtender`, `textoTerciario`, `chamadaEncerrar`, `link`), sem hexadecimal novo;
  - pergunta 12 na §9, para confirmar.
- **Testes:** `DestaqueCodigoTest` (7 casos) e o caso "cerca maior permite crases triplas dentro" no `TextoTest`, que existia no web e faltava aqui.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador:** o B mandou um JS de 24 linhas, um Python curto e um HTML.
  - O JS veio recolhido, com degradê e "Expandir código"; expandido, "Recolher código".
  - Cores certas nos três (palavra-chave, texto, número, comentário, tag e atributo).
  - "Copiar" no Python mostrou "Copiado!" em verde, e colar no campo deu o código.

## Decisões
- **Tokenizador próprio em vez de biblioteca:** cobre todas as linguagens do web, sem dependência nova e sem regex.
- **Cores:** sem cor nova; os tokens existentes ganharam papéis novos (pergunta 12 do `cores.md`).

## Pendências
- **7.9:** Markdown renderizado (` ```md `) com "Visualizar"/"Código", e o mermaid (P2).
- **Disco:** durante este bloco o disco C: chegou a ~170 MB livres (Docker 27 GB, Gradle 12 GB, emulador 8 GB). Ver o fim da noite no resumo da sessão.
