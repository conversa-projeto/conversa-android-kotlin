# 2026-10-08 · 10 · Etapa 6 — chamada no sistema: toque, notificação, tela cheia, serviço e Core-Telecom

- **Fluxo:** Chamadas (etapa 6); também Notificações e push (etapa 5: tela cheia e canal de chamadas)
- **Tipo:** código / manifesto / dependência
- **Itens:**
  - `TODO.md` 6.3, 6.5, partes da 6.2, 6.4, 6.8 e 6.12; e os dois itens da etapa 5 que tinham ficado para a 6;
  - FC-701, FC-706, FC-707, FC-713, FC-717; CHA-03, CHA-04, CHA-13; AND-03, AND-04, AND-06;
  - problemas #3 e #7 do legado.
- **Branch:** `reescrita`
- **Commits:** `5837aec`

## Contexto
Depois do bloco da mídia (2026-10-08 · 09), a chamada só abria sozinha com o app na frente. Faltavam:
- o toque;
- a notificação de chamada recebida, com tela cheia;
- o serviço que mantém microfone e câmera em segundo plano;
- a integração com o sistema de chamadas do Android.

**Core-Telecom.** O usuário perguntou se isso faria ligações pela rede de telefonia. Não faz:
- o Core-Telecom só registra no sistema a chamada **do próprio app**, que continua inteira pela internet (WebRTC);
- não usa chip, operadora, minutos nem número de telefone;
- não pede permissão de ligar.

Ganha-se:
- atender e desligar pelo fone Bluetooth ou pelo carro;
- a chamada do app entrar em espera quando chega uma ligação de celular;
- o modo de áudio de chamada (cancelamento de eco, fone do aparelho).

O usuário concordou em manter.

## O que foi feito
- **`IntegracaoChamada`** (`feature/chamada/SistemaChamada.kt`): observa o `GerenciadorChamadas` e cuida de tudo que é do sistema. O gerenciador continua sem saber de toque, notificação ou Telecom.
  - **`TomDeChamada`:**
    - toque próprio em loop (`Ringtone` do toque padrão) e vibração, conforme o modo da campainha (normal, vibrar, silencioso);
    - tom de chamando para quem liga;
    - tudo para quando a fase sai de "tocando" (#7).
  - **`NotificacoesChamada`:**
    - a chamada recebida em CallStyle (`forIncomingCall`), com tela cheia e só com o app fora da frente;
    - "Atender" abre a `ChamadaActivity` direto, nunca por broadcast → activity (#3); "Recusar" vai por receiver;
    - a chamada em andamento em CallStyle (`forOngoingCall`), com cronômetro nativo e "Desligar".
  - **`ServicoChamada`:**
    - serviço em primeiro plano (`phoneCall` + `microphone`, e `camera` com a câmera ligada), só durante a chamada;
    - mantém microfone e câmera com o app em segundo plano;
    - acaba sozinho quando a chamada termina.
  - **`TelecomChamadas`** (Core-Telecom 1.0.1):
    - registro do app;
    - a chamada recebida e a efetuada via `addCall`, com atender, desligar, ativar e espera vindos do sistema;
    - rotas de áudio expostas para a 6.8.
  - **Em espera:**
    - posta em espera pelo sistema, a chamada do app desliga o microfone e a tela mostra "Chamada em espera" com "Retomar";
    - **achado no teste:** o Telecom não retoma sozinho a chamada do app quando a de celular acaba.
  - **Ao entrar em chamada:** para o áudio de mensagem (`PlayerAudio.parar()`).
- **Canal de chamadas:**
  - `chamadas_recebidas_v2`, sem som e sem vibração, porque quem toca é o app;
  - o `v1` (com o som de notificação) é apagado.
- **Tela cheia (Android 14+):** se a permissão estiver negada, a tela principal explica uma vez e leva às configurações; a pessoa decide lá. Fica guardado em `PreferenciasStore.pediuTelaCheia`.
- **Manifesto (`feature:chamada`):**
  - `USE_FULL_SCREEN_INTENT`, `VIBRATE`, `FOREGROUND_SERVICE` e os tipos `PHONE_CALL`, `MICROPHONE` e `CAMERA`;
  - o serviço e o receiver;
  - o `MANAGE_OWN_CALLS` vem do Core-Telecom.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **274 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, contra o web (Chrome de teste com câmera falsa):**
  - **App em segundo plano:**
    - a notificação CallStyle "Teste Android B · Somente Áudio" apareceu, com Recusar e Atender;
    - o Telecom registrou a chamada (`RINGING`, áudio em `MODE_RINGTONE`);
    - o tocador de toque do app apareceu.
  - **Ninguém atendeu em 30 s:** a notificação sumiu, a chamada saiu do Telecom e o áudio voltou ao `MODE_NORMAL`.
  - **"Answer" na notificação:**
    - a tela abriu já atendendo e o web ficou "ativa";
    - o serviço ficou em primeiro plano (`phoneCall|microphone`) com a notificação "em chamada" (cronômetro e "Hang Up").
  - **Telecom em duas chamadas seguidas** (`RINGING` → `ACTIVE`, áudio em `MODE_IN_COMMUNICATION`, removida ao desligar):
    - a primeira versão falhava na segunda chamada, porque os coletores das rotas seguravam o escopo da anterior;
    - foi corrigido, e duas seguidas passaram.
  - **Efetuada:** `DIALING` → (web atende) `ACTIVE` → (web sai) removida, com a bolha "Chamada de áudio · 00:09" na conversa.
  - **Tela apagada:** o web ligou e a tela acendeu na "Chamada recebida" (tela cheia).
  - **Ligação de celular simulada pelo emulador, atendida pelo discador:**
    - a do app ficou `ON_HOLD` e apareceu "Chamada em espera";
    - "Retomar" voltou a `ACTIVE` e a faixa sumiu.
  - Nada disso usou rede de telefonia; a ligação de celular foi só simulada pelo console do emulador.

## Decisões
- **Toque próprio com canal silencioso,** em vez do som do canal com `FLAG_INSISTENT`: o app controla o loop e para exatamente quando a fase muda (#7).
- **Notificação de chamada recebida só com o app fora da frente.** Na frente, a `ChamadaActivity` já abre sozinha (`ApresentadorChamada`).
- **Isenção de bateria:** fica para decidir junto com o FCM. Com push de alta prioridade não deve ser preciso, e sem push não adianta.

## Pendências / próximos passos
- **6.8:** seletor de rota de áudio (alto-falante, fone, Bluetooth) usando `TelecomChamadas.rotas`, e o sensor de proximidade.
- **Testar num aparelho:** atender pelo fone Bluetooth; e a chamada com o app fechado, que depende do FCM (5.1) e do S1 no servidor.
- **6.12:** banner "Toque para voltar à chamada" e PiP.
