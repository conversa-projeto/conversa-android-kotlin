# 2026-10-08 · 08 · Etapa 6 — módulos de chamada e a máquina de estados (`GerenciadorChamadas`)

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes / build (módulos novos)
- **Itens:**
  - `TODO.md`: criação dos módulos; 6.2; regras de 6.4, 6.5, 6.6, 6.7 e 6.11 que são só estado;
  - FC-700, FC-708, FC-709, FC-710, FC-711, FC-712; CHA-03, CHA-04, CHA-10; ATV-03;
  - problemas #7 e #18 do legado.
- **Branch:** `reescrita`
- **Commits:** `ceeec72`

## Contexto
A etapa 6 começa pela máquina de estados da chamada: todo o resto depende dela (mídia, Telecom, toque, notificação, telas), e as regras do projeto exigem teste unitário dela.

O comportamento de referência é o do web (`conversa-web/src/stores/call.ts`), com as regras do contrato §9 e o desenho do plano §3.4.

## O que foi feito
- **Módulos novos:** `:core:webrtc` e `:feature:chamada`, em `settings.gradle.kts` e no `CLAUDE.md`.
- **`MidiaChamada`** (`core/webrtc`): a interface da mídia. O gerenciador decide **quando** e a mídia só executa:
  - `abrirLocal(video)`: vídeo → só áudio → nada (só recebe);
  - `publicar`, `sincronizar(participantes)`, `reassinar`, `desconectar`;
  - `ativarVideo(transmitir)`, `microfone`, `camera`;
  - `encerrar()`, idempotente; aborta o que estiver em andamento.

  A implementação WebRTC vem na 6.1.
- **`ChamadasRemotas` / `ChamadasRepositorio`** (`core/data/chamadas`): as ações do §9.2 (iniciar, dados, entrar, recusar, sair, cancelar, anunciar vídeo, ICE).
  - É uma interface para o gerenciador ser testado com um falso.
- **`GerenciadorChamadas`** (`feature/chamada`), singleton:
  - **Estado:** `StateFlow<EstadoChamada>` com a fase (`INATIVO`, `CHAMANDO`, `RECEBENDO`, `CONECTANDO`, `ATIVA`, `ENCERRANDO`), chamada e dados, tipo, quem ligou, mídia local, microfone, câmera, `ativaDesde` (cronômetro), pedido de vídeo e conversa do chat.
  - **Avisos de uma vez:** "já em chamada", sem microfone, falha do servidor.
  - **Comandos:** `ligar`, `atender(soAssistir)`, `recusar`, `desligar` (cancela, recusa ou sai, conforme a fase), microfone, câmera, `ligarVideo`, `responderVideo`.
  - **Entradas:**
    - é o único consumidor dos eventos 51–57 do WebSocket;
    - lê as `chamadasPendentes` do `SyncManager`;
    - observa a sessão: o logout encerra a chamada.
  - **WebSocket em segundo plano:** liga `ConexaoTempoReal.chamadaAtiva` enquanto houver chamada, para o socket não cair.
  - **Concorrência como no JavaScript do web:** roda num despachante de uma coisa por vez (`limitedParallelism(1)`), então o estado só muda entre as suspensões.
    - Quem espera a rede guarda a "geração" e confere na volta se a chamada ainda é a mesma.
    - Não há trava durante a rede (#18).
  - **Regras (iguais às do web, com reforços):**
    - **51:**
      - de outra chamada, ocupado ou já tocando → `recusar {nao_atendeu}` sem tocar;
      - da minha própria chamada, parado → ignora;
      - duplicado → ignora;
      - depois do 52 da mesma chamada (fora de ordem) → ignora;
      - com os dados já finais ou eu já atendi → não toca.
    - **Toque:** 30 s tocando → `recusar {nao_atendeu}`; "Recusar" manda **um** `POST`.
    - **Atendi ou recusei em outro aparelho:** 53/54 com o meu id enquanto toca → para, sem chamar o servidor.
    - **Atender:** vídeo+áudio → só áudio → só recepção → `entrar` → publicar → `ATIVA` → assinar com 3 tentativas.
    - **Ligar:**
      - mídia → `iniciar` (comigo na lista) → publicar;
      - desligar enquanto o servidor cria → cancela a chamada que nasceu;
      - 45 s sem ninguém → cancelar.
    - **54 de outro:** `CHAMANDO` → `ATIVA` com cronômetro.
    - **53 de outro:** recalcula pelos dados; ninguém mais tocando nem dentro → encerra (1:1 recusou; ou todos do grupo recusaram).
    - **55:**
      - solta quem saiu e confere os dados;
      - ninguém mais com "Entrou" → `sair`, porque o servidor não manda o 52 nesse caso;
      - **52** → encerra.
    - **Monitor de 4 s (chamando ou ativa):**
      - sincroniza a mídia;
      - pega um 54 ou um 55 perdidos;
      - percebe quando todos recusaram.
    - **Pendentes:** com mais de 25 s → perdida; senão toca. Uma pendente que chega depois de recusar não toca de novo.
    - **56:** em vídeo → reassina quem republicou; em áudio → pedido "Apenas assistir / Transmitir também", com "Apenas assistir" em 15 s.
      - Ligar o vídeo é protegido contra toque duplo.
    - **57 `{acao:"chat"}`:** guarda a conversa do chat.
  - **Encerrar:**
    - corta a mídia antes (DELETE WHIP/WHEP + `dispose`, na `MidiaChamada`);
    - em `ENCERRANDO`, espera o servidor por até 3 s;
    - depois volta a `INATIVO`.

    Toque, notificações, Telecom e serviço vão **observar** o estado (6.3 e 6.5).
- **Lint (de blocos anteriores, no `feature:chat`):** "Carregando PDF…" e "Transcrevendo…" com o caractere de reticências, e `createBitmap` da KTX no `VisualizadorPdf`. Eram 3 avisos que tinham passado.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`: **265 testes, 0 falhas** (51 novos); 0 apontamentos de lint; ktlint OK.
- **`GerenciadorChamadasTest`** cobre todas as transições, inclusive eventos duplicados e fora de ordem: 51 duplicado, 51 depois do 52, 52 duplicado, o meu próprio 54 durante o atender, 52 durante o `entrar`, desligar durante o `iniciar`, 54 e 55 perdidos (monitor), pendente repetida e pendente depois de recusar.
  - Usa o servidor e a mídia falsos, com "portões" para segurar uma chamada de rede no meio.
- **Teste de mutação:** tirar duas regras (51 depois do 52; 53 com o meu id) fez 3 testes falharem. Os testes pegam regressões.

## Decisões
- **Gerenciador reativo para o sistema:**
  - o gerenciador não chama toque, notificação nem Telecom;
  - quem cuida disso observa o `estado`;
  - assim a máquina continua pura e testável.

  A ordem do `encerrar()` do TODO continua valendo: a mídia é cortada antes de o estado mudar.
- **`CONECTANDO` → `ATIVA`** só depois do `entrar` e da tentativa de publicar (o diagrama do plano diz "WHIP ok → Ativa"). O web marca "ativa" logo depois do `entrar`.
- **Reforços em relação ao web:**
  - monitor também em `CHAMANDO`;
  - regra "todos do grupo recusaram";
  - 45 s sem resposta;
  - lista de chamadas encerradas para ignorar 51 e pendentes atrasados.

  O web não tem nada disso. Nenhum muda o que o usuário vê numa chamada normal; só cobre eventos perdidos.
- **O status 4 da chamada não é usado para encerrar no 55.** O servidor marca 4 numa chamada de áudio em grupo quando a primeira pessoa sai (o `tipo` tem sentido duplo, contrato §9.1). Vale a regra do web e do TODO: sai só quando ninguém mais está com "Entrou".

## Pendências / próximos passos
- **6.1:** a implementação WebRTC da `MidiaChamada` (WHIP/WHEP com o OkHttp do app, ICE por `/api/ice` só relay, H264). Depois, ligar o `GerenciadorChamadas.iniciar()` no `Application`.
- **6.3 e 6.5:** toque, notificação CallStyle, tela cheia e Telecom observando o estado.
- **6.4 e 6.9:** botões e telas.
