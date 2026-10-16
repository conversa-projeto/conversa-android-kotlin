# 2026-10-15 · 27 · Atividades pendentes até concluir

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.1 (linha 🆕 `ef13c22` das pendentes); ATV-01; servidor `ef13c22`, web `9c6e8a6`
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`ef13c22`, migração 044) trocou o "nova" das atividades por **pendente**.
  - **Antes:** era nova o que tinha chegado desde a última visita à aba.
  - **Agora:** cada atividade fica pendente até ser concluída. Isso acontece sozinho (leu a resposta ou a menção, confirmou a leitura, viu a reação, abriu a chamada perdida) ou à mão (`POST /atividade/concluir`).
  - **O contador** de `GET /atividades/novas` passou a ser o de pendentes.
  - **Rotas e tipo novos:** `POST /atividades/reacoes-vistas` (as minhas mensagens com reação que apareceram na tela) e o tipo 5, "pediu que você confirme a leitura".
- **No app, depois do pull:**
  - `GET /atividades` não manda mais `nova`, e o app lia `false` em tudo; a marca de nova sumiu;
  - abrir a aba chamava `POST /atividades/vistas` e zerava o contador, mas o servidor voltava a mandar o número de pendentes no aviso seguinte.

## O que foi feito
- **Modelo e rede:**
  - `Atividade.pendente` no lugar de `nova`;
  - `TipoAtividade.CONFIRMACAO` (5), com o selo "✓";
  - `ConversaApi.concluirAtividade` e `concluirReacoesVistas`;
  - `reacoesDeOutros(mensagem)` (as reações que não são minhas).
- **Room v8:** migração automática 7→8 que renomeia a coluna `nova` para `pendente` (`Migracao7Para8`, `@RenameColumn`), com o esquema `8.json`. O `MigracoesTest` passou a ir até a 8.
- **`AtividadesRepositorio`:**
  - **abrir:** relê a lista e o contador, sem zerar e sem `POST /atividades/vistas`;
  - **`concluir(atividade)`:** tira a marca no Room e desconta o contador; se já está concluída, não faz nada;
  - **o WS 61:** relê o contador e, com a aba aberta ou lista guardada, a primeira página.
- **`MensagensRepositorio.reacoesVistas`:** como o web, avisa uma vez por quantidade de reações e de novo se falhar. Chamado pelo `ChatViewModel.marcarLidas` com as minhas mensagens à vista que têm reação de outra pessoa.
- **Aba Atividades:**
  - a pendente fica com o fundo azul e o ponto ("Pendente"), e o botão "Marcar como concluída" (no pedido de confirmação, "Marcar como concluída (a leitura continua sem confirmar)", como o web);
  - a chamada perdida se conclui ao ser tocada, mesmo sem conversa;
  - o tipo 5 aparece como "pediu que você confirme a leitura", com o selo verde (`chamadaAtender`, cor que já existe);
  - o texto da lista vazia segue o web ("… pedidos de confirmação de leitura e chamadas perdidas aparecem aqui.").
- **Testes:**
  - abrir não zera nem marca como vistas;
  - concluir tira a marca e desconta uma vez;
  - o aviso relê a lista guardada;
  - reações vistas avisam uma vez por quantidade e de novo se falhar;
  - `reacoesDeOutros`;
  - o JSON com `pendente`;
  - a migração até a 8.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (522 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A),** contra o servidor `ef13c22`, com o B pela API:
  - o B reagiu (🎉) a uma mensagem do A e pediu a ele a confirmação de leitura de outra. O contador foi a 2;
  - na aba, as duas apareceram pendentes. O pedido veio com o selo ✓ verde e "pediu que você confirme a leitura", e o contador **continuou em 2** ao abrir a aba;
  - "Marcar como concluída" no pedido: saiu de pendente, e o contador foi a 1 (o servidor também);
  - o toque na reação abriu a mensagem com o 🎉 no grupo. Com ela na tela, o servidor foi a 0, e o contador da aba sumiu.

## Decisões
- **A coluna do Room foi renomeada (v8), em vez de guardar "pendente" na coluna `nova`.** O nome segue o do servidor, e a migração está coberta pelo `MigracoesTest`.
- **O aviso das reações vistas fica no `MensagensRepositorio`,** junto das lidas e das postagens vistas, com o mesmo controle de repetição.
