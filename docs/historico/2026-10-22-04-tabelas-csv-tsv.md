# 2026-10-22 · 04 · Tabelas CSV e TSV nos blocos de código

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.9 e 7.11 (as linhas 🆕 `3689155`); FC-529; MSG-26, ENV-09, ENV-10
- **Branch:** `reescrita`
- **Commits:** `9db5551`

## Contexto
No web (`3689155`):
- **blocos ```csv e ```tsv** aparecem como tabela, com "Visualizar" (padrão), "Código" e "Copiar" dos dados originais;
- **tabela colada** do Excel, do Sheets ou em CSV vira bloco `tsv`/`csv`.

No app, esses blocos apareciam como código cru, e colar uma tabela de poucas linhas a deixava como texto comum.

## O que foi feito
- **`core:model` `TabelaCsv.kt`,** porte do `tabelaCsv.ts`:
  - `lerTabela`: aspas, `""`, quebra de linha dentro de aspas, `\n`/`\r`/`\r\n`, linhas vazias fora;
  - `separadorCsv`: `;` ou `,` pelo número de colunas;
  - `montarTabela`: cabeçalho, linhas curtas completadas, colunas numéricas à direita;
  - `pareceTabela` e `ehLinguagemTabela`.
  - **Diferença do web:** o app desenha a grade direto, sem passar por Markdown, então não precisa do escape de `*`, `|` e `<br>`.
  - **O número** (`ehNumeroDeTabela`) aceita o espaço não separável do Excel, escrito como ` `/` ` no padrão. O `\s` do JS, da JVM e do ICU não é igual.
- **`core:model` `PareceCodigo.kt`:** o `pareceCodigo` do web (desenho ASCII, comandos de terminal, linhas com cara de código e um sinal forte), com os padrões sem `\w` nem flags embutidas.
- **`BlocoCodigo`:**
  - a tabela é o terceiro visual com "Visualizar"/"Código", como Markdown e Mermaid, recolhida em 240 dp com "Expandir código";
  - um bloco sem nenhuma célula continua como código.
- **`TabelaNaBolha`** (grade em Compose):
  - cabeçalho em negrito com o fundo do cabeçalho do código (`campoEntrada`);
  - divisórias `divisorLista`, sem cor nova;
  - cada coluna com a largura do maior texto, até 240 dp;
  - rolagem horizontal.
- **Colar:**
  - texto que é tabela abre o "Inserir código" já com `tsv`/`csv`, antes do teste de texto longo;
  - "Cancelar" cola o texto como estava;
  - no web a peça vai direto ao campo rico, que o app não tem (FC-416).
- **`LINGUAGENS_CODIGO`:** `csv` e `tsv` no fim, como no web.
- **Testes:**
  - `TabelaCsvTest` (9), com os casos do `tabelaCsv.test.ts` e do `pareceCodigo` do web;
  - o `RegexNoAndroidTest` ganhou o número com espaço não separável, o `pareceCodigo` e o `pareceTabela` (ICU).

## Como foi verificado
- `:core:model:test`, `:feature:chat:testDebugUnitTest`, `assembleDebug` e a compilação dos testes instrumentados.
- **No emulador (A, na direta com o C):**
  - o C mandou pela API um ```csv com `;`, acentos, "R$" e uma célula entre aspas com `;`. A bolha mostrou a tabela com o cabeçalho, `qtd` e `preço` à direita e "Bolo; fatia" numa célula só;
  - o C mandou um texto com tabs. Copiado pelo toque longo e colado no campo, abriu o "Inserir código" com `tsv`. Enviado, a minha bolha mostrou a tabela de duas colunas.

## Pendências
- O `RegexNoAndroidTest` roda no fim do lote, no emulador (ele desinstala o app).
