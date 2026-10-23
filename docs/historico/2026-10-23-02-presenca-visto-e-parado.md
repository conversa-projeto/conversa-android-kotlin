# 2026-10-23 · 02 · Presença: visto por último no ausente e parado em 30 s

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código
- **Itens:** `TODO.md` 2.11 (as duas linhas 🆕 `17c49a4`) e 8.5 (o texto do Status); FC-226; PRE-02, PRE-03 e CFG-09
- **Branch:** `reescrita`
- **Commits:** `7726b40`

## Contexto
Na sincronização de hoje (2026-10-23-01), o web `17c49a4` e o servidor `331083b` mudaram a presença:
- a aba fica parada depois de 30 s sem uso e continua "nesta conversa";
- o cabeçalho mostra o "visto por último" junto do "ausente" e do "nesta conversa";
- o texto do Status na Privacidade passou a "30 segundos".

## O que foi feito
- **Subtítulo da direta** (`core/model/.../Presenca.kt`):
  - `NestaConversa` e `Ausente` passaram a levar o "visto por último" (`VistoPorUltimo`, o tipo comum de `VistoHoje`, `VistoOntem` e `VistoEm`);
  - no "nesta conversa", só com a pessoa ausente (ativa continua só "nesta conversa"), como o `ChatHeader.vue`.
- **`ChatTela`:** "ausente · visto por último …" e "nesta conversa · visto por último …" (`presenca_com_visto`, "%1$s · %2$s"); o "nesta conversa" continua inteiro na cor primária, como no web.
- **Parado em 30 s** (`core/data/.../presenca/UsoDoApp.kt`):
  - um toque nas telas (`onUserInteraction` da `MainActivity` e da `ChamadaActivity`) ou uma mudança do texto no chat (`ChatViewModel.aoDigitar`) conta como uso. O teclado não passa pelas telas do app, por isso o chat avisa;
  - 30 s sem nada: o `PresencaRepositorio` manda `{ativo:false, conversa_id:<aberta>}`, ausente e ainda na conversa; o próximo uso volta a `ativo:true`;
  - em segundo plano, a contagem para (`pausar`) e a volta começa em uso; o `conversa_id` continua indo `null` em segundo plano.
- **Privacidade:** `privacidade_status_texto` com "parado há 30 segundos", o texto do web.
- **Testes:**
  - `PresencaTest`: o ausente e o "nesta conversa" com o visto, e o "nesta conversa" de quem está ativa sem ele;
  - `PresencaRepositorioTest`: parado em 30 s vai como `(false, 42)`, o uso volta a `(true, 42)`, e o segundo plano não passa por parado na volta;
  - `ChatViewModelTest`: cada mudança do texto marca uso.

## Como foi verificado
- `:core:model:test`, `:core:data:testDebugUnitTest`, `:feature:chat:testDebugUnitTest` e `assembleDebug`.
- **No emulador (A, na direta com o C), com o C conectado por um script de WebSocket:**
  - o C parado com a conversa aberta: o cabeçalho do A mostrou **"nesta conversa · visto por último hoje às 04:21"**, em azul, com a bolinha amarela;
  - o C fora da conversa: **"ausente · visto por último hoje às 04:21"**;
  - o A sem tocar na tela: 30 s depois do último toque, o C recebeu o WS 60 do A com **"ausente"**, sem nenhum WS 63 `aberta:false` (continuou na conversa);
  - uma rolagem na lista: o C recebeu o A **"ativo"** de novo.

## Decisões
- **O parado entrou no app,** embora fosse opcional, para os dois clientes mostrarem a mesma coisa. No celular, a tela costuma apagar perto dos 30 s, e aí o app vai para segundo plano como antes.
- **Só o campo do chat avisa o uso pelo teclado:** nos outros campos, o toque para abrir o teclado já conta, e ficar mais de 30 s digitando neles sem tocar na tela é raro.
- **A tela da chamada também conta o parado,** como uma aba do web numa chamada.

## Pendências
- Nenhuma.
