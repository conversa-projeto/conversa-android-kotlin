# ADR 0001 — Recomeçar o app Android numa nova base

- **Status:** aceito
- **Data:** 2026-10-06
- **Base:** `docs/auditoria-2026-10/00-LEIAME.md` e `docs/auditoria-2026-10/04-auditoria-android.md` §8

## Contexto
- O app foi escrito contra o servidor Delphi e parou em abril/2026. O servidor foi reescrito (Node → Bun/Elysia) e mudou formatos e fluxos:
  - anexos por URL assinada do MinIO;
  - exclusão lógica ("ocultar");
  - TURN obrigatório para a mídia;
  - push só de dados;
  - codec gravável.
- O web tem 136 funcionalidades; o Android tem 2 íntegras e cerca de 42 parciais ou quebradas.
- Os defeitos internos críticos estão no núcleo:
  - socket com callback único disputado por dois serviços;
  - ciclo de vida das chamadas;
  - sessão com senha salva;
  - FCM inoperante.
- Não há ViewModel, DI, Room nem testes. O `targetSdk 34` não é aceito pelo Play.

## Decisão
Construir uma **nova base**:
- Kotlin, Single-Activity, Jetpack Compose + Material 3;
- ViewModel/StateFlow, Hilt, Room, DataStore + Keystore;
- OkHttp único, kotlinx.serialization, WorkManager, Media3, Coil;
- Core-Telecom, Firebase Messaging, WebRTC (WHIP/WHEP);
- `targetSdk 36`.

Transplantar só as peças boas:
- `WhipWhepClient` e o núcleo do `WebRTCManager`;
- `AudioRecorderHelper`;
- `ChamadaRingtoneManager`;
- os padrões de notificação MessagingStyle/CallStyle;
- o visual das telas Compose de chamada;
- a lógica do histórico de chamadas.

## Alternativas descartadas
- **Atualizar o app atual endpoint por endpoint:** mexeria em quase todas as classes com estado, sem testes nem arquitetura que dê segurança. Também exigiria refazer o XML para edge-to-edge e depois migrar para Compose (trabalho dobrado).
- **Publicar o app atual com hotfixes:** inviável no Play (`targetSdk`, FGS permanente, permissões).

## Consequências
- O app legado fica na branch `legado/abril-2026` / tag `legado-v1-final`. Hotfixes nele são opcionais (seção "Opcional" do `TODO.md`).
- O `applicationId` `com.conversa.conversa` é mantido, para atualizar por cima do app instalado.
- O trabalho segue o `TODO.md`, e cada alteração é registrada em `docs/historico/`.
- Algumas funcionalidades dependem do servidor (S1/S2: push de chamada e prioridade alta).
