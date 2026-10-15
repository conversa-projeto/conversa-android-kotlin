# 2026-10-15 · 14 · Seletor de emojis como o do web

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.2 (linha 🆕 `d202c06` do seletor); FC-418; ENV-03, ENV-17
- **Branch:** `reescrita`
- **Commits:** `5d17a71`

## Contexto
- **O que o web mudou** (`d202c06`): o seletor de emojis passou a usar um catálogo próprio (`public/emojis/pt.json`), sem tons de pele, bandeiras nem FE0F sobrando. Tem pesquisa em português sem acento, categorias e "Recentes" (até 32).
- **O problema no Android:** o "mais emojis" usava o `EmojiPickerView` do AndroidX, que tem tons de pele e bandeiras. A mesma reação podia se dividir entre o web e o Android (o mesmo emoji com e sem variação).
- **E o grupo vai precisar de um seletor:** o emoji do grupo (FC-217) é o próximo bloco, fora do módulo do chat.

## O que foi feito
- **Catálogo:** `core/ui/src/main/assets/emojis/pt.json` é a cópia do arquivo do web (`d202c06`, 131 KB), gerado do `emojibase-data` (MIT). A origem e a licença ficam em `LEIAME.txt`, ao lado.
- **Regras** (`core/model/.../Emojis.kt`, novo):
  - `palavrasDaBusca`, sem acento, sem maiúsculas e sem regex (o ICU do Android não é o da JVM);
  - `textoDeBuscaDoEmoji`;
  - `pesquisarEmojis`: cada palavra digitada precisa começar uma palavra do nome ou das palavras-chave; até 200;
  - `usarEmojiRecente` (até 32) e os `EMOJIS_POPULARES` do web, para começar os recentes.
- **Seletor** (`core/ui/.../SeletorEmojis.kt`, novo):
  - **`CatalogoEmojis`:** lê o catálogo uma vez, fora da thread principal, e deixa de fora o que a fonte do aparelho não desenha (`Paint.hasGlyph`, como o `suporteEmoji.ts` do web);
  - **`EmojisRecentes`:** guarda os recentes no aparelho (`SharedPreferences`; o web usa o navegador);
  - **`SeletorEmojis`:** "Pesquisar emoji", a barra com "Recentes" e as 8 categorias (o toque leva até a categoria), a grade com os cabeçalhos, "Nenhum emoji encontrado" e "Carregando os emojis…". Cada emoji tem o nome em português para o TalkBack.
- **O "mais emojis" das reações** (`SeletorDeEmoji` no `AcoesMensagem.kt`) passou a usar o seletor novo. A dependência `emoji2-emojipicker` saiu do `feature:chat` e do catálogo de versões.
- **Testes:** as palavras da busca, a pesquisa (início de palavra, várias palavras, sem acento, o limite) e os recentes.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (498 testes), `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Passou.
  - O lint pediu o `edit { }` do core-ktx no `SharedPreferences` (`UseKtx`); trocado, e o lint voltou aos 3 avisos de sempre.
- **No emulador (A), no grupo 3:**
  - toque longo › "mais emojis" abriu o seletor novo, com os recentes populares e as categorias;
  - "abacaxi" na pesquisa achou o 🍍, que entrou como reação na mensagem;
  - reaberto, o 🍍 era o primeiro dos recentes.

## Pendências
- Usar o mesmo seletor no emoji do grupo (FC-217, o próximo bloco).
