# 2026-10-08 · 14 · Etapa 6 — minimizar a chamada: picture-in-picture e a faixa "voltar à chamada"

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / manifesto
- **Itens:** `TODO.md` 6.12 (PiP, faixa) e 6.9 (voltar = minimizar); FC-717; CHA-13; AND-06
- **Branch:** `reescrita`
- **Commits:** `add5196`

## Contexto
Antes, "voltar" fechava a tela da chamada e não havia como voltar a ela, a não ser pela notificação. Numa chamada de vídeo, sair do app também perdia a imagem.

## O que foi feito
- **Picture-in-picture** (`ChamadaActivity`):
  - **Quando entra:** com a chamada de vídeo em andamento, sair do app (início, outro app) vira PiP sozinho no Android 12+ (`setAutoEnterEnabled`); antes do 12, pelo `onUserLeaveHint`. "Voltar" também minimiza para o PiP.
  - **O que mostra:** só quem está em destaque (ou o primeiro com vídeo), sem cabeçalho nem controles. A animação sai da área do vídeo (`setSourceRectHint`).
  - **Tarefa própria** (`taskAffinity`, `singleTask`): o PiP leva só a chamada, e o app continua usável embaixo.
  - **Fim:** quando a chamada acaba, a tela fecha e o PiP some.
- **Faixa "Toque para voltar à chamada"** (`ComBannerDaChamada`, em volta de todas as telas do app):
  - durante a chamada, faixa verde (`chamadaAtender`, cor do FMX) no topo, com a duração;
  - o toque reabre a tela da chamada;
  - a faixa ocupa a barra de status, e as telas embaixo não a descontam de novo (`consumeWindowInsets`).
- **Voltar = minimizar:** em vídeo, vira PiP; em áudio, a tela fecha e a chamada segue, com a faixa no app.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **283 testes, 0 falhas**;
  - 0 apontamentos de lint (o aviso de PiP pedia `setAutoEnterEnabled(true)` e `setSourceRectHint`; os dois ficaram explícitos);
  - ktlint OK.
- **No emulador, contra o web:**
  - **Chamada de áudio, "voltar":** apareceu a lista de conversas com a faixa "Toque para voltar à chamada · 00:10", sem margem dobrada embaixo; o toque voltou à chamada.
  - **Chamada de vídeo, "início":**
    - virou janela flutuante (`pinned`, tarefa `com.conversa.app.chamada`) com o vídeo do B, e a chamada seguiu "ativa" no web;
    - ao encerrar pelo web, o PiP fechou e a tela sumiu.
  - **Chamada de vídeo, "voltar":** também virou PiP, sem erro.

## Pendências / próximos passos
- **6.13 (extras):** adicionar participante, chat da chamada, indicador de fala, somente recepção, tela e ponteiro remotos.
- **6.9:** o teste só com o TalkBack.
