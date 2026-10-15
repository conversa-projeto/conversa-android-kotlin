# 2026-10-15 · 05 · Respostas de postagem não prendem mais o contador

- **Fluxo:** Mensagens (etapa 3)
- **Tipo:** correção
- **Itens:** `TODO.md` 8.7 (1ª linha) e 7.13 (1ª linha, DTO e rotas); FC-316; MSG-22; contrato §10.14 e §10.15
- **Branch:** `reescrita`
- **Commits:** `4ba2643`

## Contexto
O servidor `12cf0cf` tirou as respostas de postagem do fluxo: elas não vêm em `/mensagens`. Mesmo assim, elas:
- contam no `mensagens_sem_visualizar`;
- viram a prévia da lista;
- geram o WS 2 e o WS 3.

O app nunca as marcava como vistas. Em 2026-10-13, no emulador, depois de abrir o grupo ficou "1 não lida" para sempre.

## O que foi feito
- **Dados, já pensando na confirmação de leitura (FC-521), que vem a seguir:**
  - **DTOs** (`MensagemDtos.kt`): `postagem` (`PostagemDto`: título, `respostas`, `nao_lidas` e as `ultimas`, que são `MensagemDto`), `postagem_id` e `confirmacao` (`ConfirmacaoDto` com `pode_confirmar`, `destinatarios` e `usuarios`). Também `VisualizarPostagemRequisicao` e `RespostasVistasDto`.
  - **Modelo** (`Mensagem.kt`): `Postagem`, `ConfirmacaoLeitura`, `DestinatarioConfirmacao` e `ConfirmacaoUsuario`, mais os campos `postagem` e `confirmacao` em `Mensagem`.
  - **Room v4** (migração automática 3→4): `postagemJson` e `confirmacaoJson` na mensagem, gravados e lidos pelos mapeadores. O esquema `4.json` foi exportado.
  - **API:** `POST /postagem/visualizar` e `GET /postagem/respostas`.
  - **WebSocket:** eventos 8 (`ConfirmacaoLeitura`), 64 (`PostagemAtualizada`) e 65 (`ConfirmacaoPedida`).
- **Comportamento:**
  - **`MensagensRepositorio.visualizarPostagem`:** quando a postagem aparece na tela com respostas novas, faz quatro coisas:
    - desconta essas respostas do contador na hora;
    - chama `POST /postagem/visualizar`;
    - relê a postagem;
    - não repete o pedido para a mesma resposta mais recente. Ela é a chave, e não a quantidade: a quantidade se repete de uma resposta para a outra.
  - **`ChatViewModel.marcarLidas`:** as postagens à vista com `nao_lidas > 0` entram junto com as mensagens comuns.
  - **`SyncManager`:**
    - o WS 64 relê a postagem, se ela está no aparelho, e a lista;
    - o WS 8 e o WS 65 releem a mensagem;
    - **os ids do WS 3 que não estão no fluxo** (respostas) são procurados uma vez só e depois ficam numa lista de "fora do fluxo" (até 200), sem disparar outro `GET` a cada status.
- **Testes:**
  - parser dos eventos 8, 64 e 65;
  - desserialização da postagem, com as últimas respostas, e da confirmação, com a lista;
  - `visualizarPostagem`: marca uma vez, desconta, relê, e marca de novo quando chega outra resposta;
  - o WS 3 com uma resposta de postagem busca uma vez só.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` e `ktlintCheck`. Passou.
- **No emulador (A), com o B respondendo pela API:**
  - **A resposta presa desde 2026-10-13:** a versão nova migrou o banco; ao abrir o grupo, `POST /postagem/visualizar` e a releitura da 1170; a "1 não lida" sumiu.
  - **Resposta nova com o A na lista:** "1 não lida"; ao abrir o grupo, zerou. A primeira versão da deduplicação, por (postagem, quantidade), não marcava; corrigida para a resposta mais recente.
  - **Resposta nova com o grupo aberto:** o WS 64 releu, a postagem à vista foi marcada na hora, e a lista ficou sem contador.

## Decisões
- **A postagem continua aparecendo como uma mensagem comum** até o 7.13, que vai trazer a bolha com título, contagens, respostas e "Responder à postagem". Este bloco só tira o contador preso.
- **As colunas da confirmação entraram nesta mesma migração**, para a FC-521 não precisar de outra.

## Pendências
- 7.13: a bolha de postagem (FC-317), responder pelo campo e criar a postagem (FC-522).
- 5.4: a notificação de resposta a postagem (FC-610). O WS 64 já chega.
