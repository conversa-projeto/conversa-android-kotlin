# 2026-10-08 · 37 · Etapa 8 — aba Atividades

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade / módulo novo / correção
- **Itens:** `TODO.md` 8.1 e parte da linha dos módulos; FC-800; ATV-01, ATV-02
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A aba Atividades da barra inferior mostrava "em breve"; só o badge (`GET /atividades/novas`, WS 61) já funcionava. A `ConversaApi`, o DTO, o modelo e a tabela `atividade` do Room já existiam.

## O que foi feito
- **`core:model` — `Atividades.kt`:**
  - `agruparAtividades` (Hoje / Ontem / `dd/MM/aaaa`, em ordem);
  - `textoDoSelo` (emoji, ↩, @, ✆);
  - `ondeFoi` (só em grupo);
  - `previa` (texto com menção resumida, ou o tipo do conteúdo; nada na chamada perdida);
  - `ATIVIDADES_POR_PAGINA = 30`.
- **`core:data` — `AtividadesRepositorio`:**
  - o Room como cache: a primeira página troca tudo (`AtividadeDao.trocar`), as seguintes somam;
  - `abrir` relê e marca como vistas; o badge zera;
  - o WS 61 com a aba aberta relê e marca; fechada, só atualiza o contador, como o store do web;
  - mapeadores entidade ↔ modelo.
- **`SyncManager`:** o contador e o WS 61 passam para o repositório; `atividadesNovas` continua igual para a tela principal.
- **Módulo novo `:feature:atividades`:**
  - **`AtividadesViewModel`:** abre ao entrar em primeiro plano, fecha ao sair, pagina perto do fim.
  - **`AtividadesTela`**, como o `AtividadesPage.vue`:
    - cabeçalhos por dia;
    - item com avatar e selo, "**autor** descrição", "em <grupo>", prévia entre aspas, hora e o ponto de "Nova" (fundo azul claro);
    - "Carregando…", vazio e erro;
    - o toque leva à mensagem (`RotaChat(conversa, mensagem)`) ou à conversa.
- **`app`:** a aba usa a tela nova; saem os textos "em breve".
- **Correção — `ConversaApi.marcarAtividadesVistas`:** era `@HTTP(method = "POST", hasBody = false)`, e o OkHttp recusa POST sem corpo, então as atividades nunca ficavam vistas no servidor. Agora manda `{}`. O `RedeTest` cobre o caso; antes da correção, o teste falhava.
- **Testes:**
  - `AtividadesTest` (4);
  - `AtividadesRepositorioTest` (Room em memória: páginas, troca, vistas, aviso com a aba aberta ou fechada);
  - `AtividadesViewModelTest` (2);
  - `RedeTest` (POST de vistas);
  - `SyncManagerTest`, com o repositório de verdade.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A):** o B, pela API, reagiu (🔥) a uma mensagem do A, respondeu e mencionou o A no grupo.
  - A aba mostrou "Hoje" com as três atividades: selos 🔥, ↩ e @, "em Grupo criado pelo B", as prévias e o ponto de "Nova".
  - Em "Ontem", as chamadas perdidas antigas apareceram com ✆.
  - Na primeira versão, o servidor continuava com 4 não vistas depois de abrir a aba: foi assim que achei o defeito do POST. Depois da correção, abrir a aba deixou `GET /atividades/novas` em 0.
  - O toque em "respondeu sua mensagem" abriu o grupo na resposta.

## Pendências
- **Módulos:** `:feature:pesquisa` e `:feature:config` entram com o 8.2 e o 8.5.
