# 2026-10-09 · 01 · Etapa 8 — pesquisa na conversa e em todos os chats

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade / módulo novo / correção de recursos
- **Itens:** `TODO.md` 8.2 e parte da linha dos módulos; FC-801; PES-01, PES-02
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O web tem duas pesquisas de mensagens:
- **Na conversa** (`ChatHeader.vue`): a lupa abre "Pesquisar nesta conversa", e os resultados levam à mensagem.
- **Em todos os chats** (`PesquisaAvancada.vue`): o botão ao lado do "Pesquisar…" da lista. Os resultados vêm agrupados por conversa, com o termo marcado.

A rota `GET /pesquisar` já existia na `ConversaApi`.

## O que foi feito
- **`core:model` — `Pesquisa.kt`:**
  - `ocorrencias` (sem diferenciar maiúsculas, sem regex);
  - `trechoComTermo` ("…antes termo depois…", para o termo aparecer numa mensagem longa);
  - `textoParaPesquisa` e `tipoParaPesquisa` ("Imagem" / "Áudio" / "Arquivo" sem texto, como o `resumoMensagem` do web);
  - `agruparPorConversa`.
- **`core:data` — `MensagensRepositorio.pesquisar`:**
  - as mais recentes primeiro;
  - filtra pela conversa, como o web;
  - **não grava no Room**: uma mensagem antiga solta abriria um buraco na lista do chat, e o "ir para a mensagem" traz o caminho todo.
- **`core:ui` — `LinhaResultadoPesquisa`:** quem mandou, a data `dd/MM/aa HH:mm` e o trecho com o termo em negrito sobre a primária clara. O FMX não tem o amarelo de marca-texto do web.
- **`feature:chat` — na conversa:**
  - "⋮" → "Pesquisar na conversa" (o cabeçalho já tem voz, vídeo e membros);
  - o cabeçalho vira o campo "Pesquisar nesta conversa", com foco; o "Pesquisar" do teclado ou a lupa busca, e voltar fecha;
  - os resultados aparecem num painel por cima da conversa ("Pesquisando…", "Nenhum resultado encontrado.");
  - o toque fecha a pesquisa e usa o mesmo "ir para a mensagem" da citação (7.5);
  - estado em `ChatViewModel.pesquisa`; uma busca nova cancela a anterior.
- **Módulo novo `:feature:pesquisa` — em todos os chats:**
  - "Pesquisar em todos os chats" (ícone no campo da lista de conversas) abre a tela já com o termo do campo;
  - os resultados vêm agrupados por conversa (nome e quantidade);
  - o toque abre a conversa na mensagem.
  - Rota `RotaPesquisa(termo)`.
- **Correção — textos com o mesmo nome em módulos diferentes:** no emulador, o painel mostrou "Nenhuma conversa ou contato encontrado", texto do módulo de conversas. No APK os nomes de texto são globais, e o `nenhum_resultado` novo do `core:ui` colidia com o de lá.
  - Renomeados: `pesquisa_nenhum_resultado`, `pesquisa_pesquisando`, `atividade_previa_*` (o `previa_votacao` das atividades colidia com o "📊 Votação" do `core:ui`), `acao_pesquisar` e `pesquisa_placeholder`.
  - Novo `ferramentas/textos-repetidos.mjs`: aponta nomes repetidos com textos diferentes e sai com 1 se achar algum. A regra foi registrada no CLAUDE.md, em "Textos de UI".
- **Testes:**
  - `PesquisaTest` (4);
  - `MensagensTest`: as mais recentes primeiro, só da conversa, sem gravar no Room;
  - `PesquisaViewModelTest` (2);
  - `ChatViewModelTest`: pesquisar e abrir o resultado.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (A).** Antes do teste, a sessão tinha vencido: o servidor dá 12 h ao token. Entrei de novo com a conta de teste.
  - **Global:** "Pesquisar em todos os chats" → "Original" trouxe "Grupo criado pelo B (3)", com o termo destacado; o toque abriu o grupo na mensagem.
  - **Na conversa:** "⋮" → "Pesquisar na conversa" → "esp" listou 4 mensagens, das mais novas para as mais antigas, com as ocorrências destacadas. O toque em "Lembra desta? (resposta a uma antiga)" fechou a pesquisa e levou à mensagem.

## Pendências
- **Módulos:** o `:feature:config` entra com o 8.5.
