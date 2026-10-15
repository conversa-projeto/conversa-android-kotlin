# 2026-10-15 · 09 · Notificação de resposta a postagem

- **Fluxo:** Notificações e push (etapa 5)
- **Tipo:** código
- **Itens:** `TODO.md` 5.4 (linha 🆕 `d202c06`) e 7.13 (**Teste**); FC-610; NOT-02, MSG-22
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O que o web faz** (`tratarPostagemAtualizada`): no WS 64 de outra pessoa, mostra "<nome> respondeu a uma postagem", menos com a conversa aberta, arquivada ou com a resposta minha.
- **O que faltava no Android:** a resposta de postagem fica fora de `/mensagens` e não entra no Room. Por isso ela não passava pelo `novasDeOutros`, e não havia notificação nenhuma. Era o que faltava para fechar o **Teste** do 7.13.

## O que foi feito
- **`SyncManager`:** o WS 64 de outra pessoa, depois de reler a postagem e a lista, sai em `respostasDePostagem` (`RespostaDePostagem`: conversa, postagem e quem respondeu). A minha resposta, de outro aparelho, não sai.
- **`NotificadorMensagens`:**
  - **Regra:** a mesma das mensagens (`decidirAviso`): arquivada, nada; na frente com a conversa na tela, nada; na frente em outra tela, só o som; em segundo plano, a notificação.
  - **Linha:** "<nome> respondeu a uma postagem", com o nome completo do contato, como o web ("Usuário <id>" sem ele).
  - **Junto das mensagens:** a linha fica guardada e entra na notificação da conversa ao lado das não lidas do Room, por hora, até a notificação sumir (aberta, lida ou arquivada). A montagem passou para `montar`, com uma `Linha` (quem, texto, quando e a postagem).
- **"Marcar como lida" e "Responder" na notificação** (`AcoesNotificacaoReceiver`): marcam também as respostas das postagens avisadas.
  - Sem isso, o contador ficava preso: o `POST /mensagem/visualizar` marca uma mensagem só, e as respostas não estão no Room.
  - `MensagensRepositorio.marcarPostagensVistas`: `POST /postagem/visualizar` de cada postagem; o contador desce o que o servidor marcou (`vistas`); a postagem que está no aparelho é relida.
- **Textos:** `notificacao_respondeu_postagem` ("respondeu a uma postagem") e `notificacao_usuario` ("Usuário %d").
- **Testes:**
  - o WS 64 de outra pessoa relê e avisa, e o meu não avisa;
  - "Marcar como lida" marca as postagens avisadas uma vez cada, desconta o contador e relê só a que está no aparelho.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (474 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), com o app em segundo plano** (dentro dos 10 s em que o WebSocket ainda fica ligado; sem FCM, ver ADR 0002):
  - **A notificação:** o B respondeu à postagem 1182, do A, pela API. Chegou "Grupo criado pelo B · Teste Android B · respondeu a uma postagem", com "Responder" e "Marcar como lida".
  - **O toque:** abriu o grupo com a resposta nova embaixo da postagem, e a notificação sumiu.
  - **"Marcar como lida":** depois de outra resposta do B, a notificação sumiu, e no servidor o A ficou com 0 não lidas no grupo.
- **Um problema no teste de unidade:** o coletor do teste ficava parado. O `advanceUntilIdle` não roda as tarefas do `backgroundScope` quando só elas sobram; o teste chama `runCurrent()` depois, como o do `novasDeOutros`.

## Decisões
- **O aviso de resposta fica em memória**, não no Room. Ele só existe para a notificação, e a resposta continua fora do fluxo.
- **O "Responder" da notificação manda uma mensagem comum na conversa**, não uma resposta à postagem. A notificação é da conversa, e o "Responder à postagem" fica no cartão.

## Pendências
- Sem o push (5.1, decisão do ADR 0002), a notificação só chega com o WebSocket ligado.
