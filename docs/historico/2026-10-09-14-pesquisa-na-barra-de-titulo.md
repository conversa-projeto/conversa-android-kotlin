# 2026-10-09 · 14 · Pesquisa só na barra de título da lista de conversas

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código
- **Itens:** `TODO.md` 2.7 (nova linha depois de "Campo de busca"); CON-02
- **Branch:** `reescrita`
- **Commits:** `489b2f0`

## Contexto
O pedido foi que a pesquisa ficasse **só na barra de título** e fosse **dinâmica**. Antes de alterar, um mockup interativo mostrou o que tinha sido entendido, e a pessoa aprovou:
- a lupa se estica até virar o campo;
- a lista filtra enquanto se digita;
- "←" fecha a pesquisa.

Antes, o campo "Pesquisar…" ocupava uma linha própria abaixo do título.

## O que foi feito
- **`feature/conversas/.../lista/BarraConversas.kt` (novo):** a `TopAppBar` da lista com a pesquisa dentro.
  - **Fechada:** o título "Conversas" e a lupa à direita.
  - **Ao tocar na lupa:** ela se estica até ocupar a barra, em 250 ms. O fundo vem de `campoEntrada`, o título some e o campo abre focado, com o teclado.
  - **Aberta:**
    - "←" fecha e limpa o filtro;
    - "×" aparece com texto e apaga o termo;
    - o ícone de "Pesquisar em todos os chats" (8.2) continua lá, com o termo.
  - **Voltar do sistema:** fecha a pesquisa antes de sair da tela (`BackHandler`).
  - **Com um termo guardado** (ao voltar de outra tela), a barra já abre aberta e com o texto.
  - O texto fica em estado local, como antes, para o cursor não pular.
- **`ConversasTela.kt`:** usa a `BarraConversas`. A função `CampoBusca` e a linha do campo saíram, e a lista começa logo abaixo do título.
- **Textos novos** (só do Android): "Pesquisar" (lupa) e "Fechar pesquisa" ("←"), este com o mesmo texto do chat.
- **`feature/conversas/build.gradle.kts`:** Robolectric e `ui-test-junit4` nos testes.
- **`BarraConversasTest` (novo, 6 testes):**
  - fechada, mostra só o título e a lupa;
  - a lupa abre o campo focado, que filtra e pesquisa em todos os chats;
  - o "×" apaga e o campo continua aberto;
  - a seta fecha e limpa;
  - o voltar do sistema fecha sem encerrar a tela;
  - com um termo guardado, a barra abre já aberta.
- **`core/ui/.../ManterNoTopo.kt` (do bloco 13):** ganhou `@Suppress("FrequentlyChangingValue")`, com a justificativa no KDoc. O lint avisava da leitura da posição na composição, mas ela é feita sem observação e só quando o primeiro item muda.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Tudo passou, e o lint só mostra avisos que já existiam.
- **No emulador (conta A):**
  - a lupa abriu o campo focado;
  - "lista" filtrou e o "×" apareceu;
  - a seta voltou ao título com a lupa;
  - no voltar do sistema, o 1º fechou o teclado e o 2º fechou a pesquisa e limpou o filtro, sem sair do app;
  - no tema escuro do app, o campo ficou visível. O tema voltou para Claro depois.

## Decisões
- **A pesquisa nas mensagens de todos os chats fica dentro da barra aberta**, como no mockup, e não ao lado da lupa fechada.
- **Fechar limpa o filtro:** com a barra fechada não há onde ver o termo, e uma lista filtrada sem o campo à vista confundiria.

## Pendências
- Nenhuma.
