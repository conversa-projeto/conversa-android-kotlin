# 2026-10-06 · 01 · Auditoria inicial do Android contra o servidor e o web

- **Fluxo:** Planejamento e documentação
- **Tipo:** documentação (nenhum código alterado)
- **Commits:** `87ba4b5` (registrado retroativamente)
- **Referências:** `docs/auditoria-2026-10/00` a `08`

## Contexto
O app Android parou em 2026-04-26 (servidor Delphi). Desde então:
- o servidor foi reescrito para Node (13/09) e depois para Bun + Elysia (26/09), com 22 commits;
- o web recebeu 25 commits.

Foi pedida uma varredura completa, com fila de correções e a possibilidade de recomeçar o projeto.

## O que foi feito
- Leitura integral do servidor (`conversa/src`, migrações 000–034, infra) → `01-contrato-servidor-atual.md` (64 rotas, WS, FCM, anexos, chamadas, 46 pegadinhas).
- Histórico do servidor desde abril (`56e7cc0` → `7f670c3`) → `02-mudancas-servidor-desde-abril.md` (quebras confirmadas).
- Inventário do web (`bfb79d8`) → `03-inventario-web.md` (132 funcionalidades com IDs).
- Auditoria interna do Android → `04-auditoria-android.md` (57 problemas; build `assembleDebug` OK com o JDK 21 do Android Studio).
- Consolidação:
  - `00-LEIAME.md` (decisão);
  - `05-matriz-paridade.md`;
  - `06-fila-de-correcoes.md` (FC-xxx);
  - `07-plano-nova-base.md`;
  - `08-pendencias-servidor.md` (S1–S14).

## Decisões
- **Recomeçar o app (nova base)** em Compose/Hilt/Room/Core-Telecom/FCM, transplantando:
  - o `WhipWhepClient` e o núcleo do WebRTC;
  - o gravador de áudio;
  - o ringtone;
  - os padrões de notificação;
  - o visual das telas de chamada.
- O app atual não deve ir para o Play; os hotfixes do app antigo são opcionais.

## Achados principais
- **Q1–Q12 (quebras contra o servidor):**
  - chamadas sem mídia (sem TURN);
  - upload e download de anexos quebrados;
  - mensagem oculta exibida;
  - `visualizar` dá 404;
  - push inoperante;
  - porta de dev 4430 → 443;
  - VP8 não é gravado.
- **Críticos internos:**
  - callbacks do socket disputados (eventos de chamada perdidos);
  - "atender" pela notificação quebrado no Android 12+;
  - senha e JWT em log;
  - senha salva em texto puro;
  - toque calado pelo full-screen intent.
- **Bloqueante no servidor:** não há push de chamada (S1).

## Pendências geradas
- Fila completa em `docs/auditoria-2026-10/06-fila-de-correcoes.md` e passo a passo em `TODO.md`.
