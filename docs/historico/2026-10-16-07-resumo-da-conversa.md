# 2026-10-16 · 07 · Resumo da conversa por IA

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.8 (duas linhas do resumo); FC-811; CON-14
- **Branch:** `reescrita`
- **Commits:** `110f742`

## Contexto
- **O servidor** (`b8ea0c2`, contrato §13.5) resume a conversa por IA em segundo plano. `POST /conversa/resumo {conversa_id, periodo}` devolve um id, e `GET /conversa/resumo?id=` consulta até `concluido` ou `erro`. O pedido vale 30 min, só na memória da API.
- **O web** (`769bd7d`, `05507a0`) tem o diálogo "Resumo da conversa": períodos em pílulas, consulta a cada 2 s, um cartão por assunto com pendências e "Ver na conversa", e o último resumo de cada conversa no IndexedDB.
- **No app** não existia.

## O que foi feito
- **`core:model` (`Resumo.kt`):** `PeriodoResumo` (24h, 7d, 30d, recentes), `StatusResumo`, `AssuntoResumo`, `ResumoConversa` e `ResumoSalvo` (com a hora do pedido).
- **Rede:** `pedirResumo` e `consultarResumo` no `ConversaApi`; `ResumoDto` e `AssuntoResumoDto` (`IaDtos.kt`), com o mapeamento nos dois sentidos.
- **Room v11:** a tabela `resumo` (usuário + conversa → o JSON do servidor e a hora do pedido), como o IndexedDB do web; `ResumoDao`.
- **`ResumosRepositorio`** (`core:data/ia`):
  - `observar` preso ao usuário logado;
  - `pedir` guarda com a hora;
  - `consultar` atualiza mantendo a hora. Sem rede, fica como estava e a próxima consulta tenta de novo. Outra falha (o pedido se perdeu porque a API reiniciou, 404) vira erro com o motivo, como o web.
- **`ResumoViewModel` e `FolhaResumo`** (`feature:chat`):
  - **disponibilidade:** `disponivel` só com `recursos.ia`;
  - **ao reabrir:** volta com o período do último resumo;
  - **consulta:** a cada 2 s, só com a folha aberta;
  - **pedir de novo:** "Resumir" vira "Resumir de novo" quando já há resumo;
  - **estados com os textos do web:** escolha o período, "Resumindo N mensagens…" com "Pode levar alguns minutos…", erro numa caixa, "Nenhuma mensagem nesse período.";
  - **resultado:** a linha "Resumo de dd/MM às HH:MM · período · N mensagens", um cartão por assunto (pendências com a bolinha âmbar, a cor proposta da presença "ausente", e "Ver na conversa (N mensagens)", que fecha e vai à primeira mensagem do assunto) e o rodapé "Gerado por IA a partir de N mensagens. Confira na conversa antes de agir.".
- **Chat:** "Resumir conversa" no "⋮", depois de "Pesquisar na conversa", só com a IA ligada.
- **Testes:** `ResumosRepositorioTest` (pedir, consultar, por usuário, sem rede, 404), `ResumoViewModelTest` (período ao reabrir, consulta só aberta e até ficar pronto, motivo do erro) e `MigracoesTest` (versão 11).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev, sem IA configurada:** o "⋮" do chat mostrou só "Pesquisar na conversa".
- **Não testado de ponta a ponta:** precisa de um servidor de IA no Sistema do servidor de dev, e os parâmetros de IA só mudam com autorização (TODO 8.8, linha do teste).

## Decisões
- **Folha (bottom sheet) no lugar do diálogo do web:** o conteúdo pode ser longo, e no celular a folha rola melhor.
- **A consulta para ao fechar a folha** e volta ao reabrir, como o web. O resultado continua guardado no Room.

## Pendências
- Conferir com um servidor de IA (pedir antes de mexer nos parâmetros do servidor de dev).
