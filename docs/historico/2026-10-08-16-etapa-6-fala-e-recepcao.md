# 2026-10-08 · 16 · Etapa 6 — indicador de fala e somente recepção

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 6.13 (indicador de fala, somente recepção); FC-720, FC-721
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
Mais dois recursos de chamada do web:
- o anel verde em quem está falando;
- os botões "Ativar microfone"/"Ativar câmera" para quem entrou só recebendo (sem microfone nem câmera).

## O que foi feito
- **Indicador de fala:**
  - **`DetectorDeFala`** (`core/webrtc`): a mesma regra do `useFalaChamada` do web — volume acima de 0,02 e a pessoa continua marcada 400 ms depois de parar.
  - **`MidiaWebRtc`:**
    - a cada 150 ms lê o `audioLevel` das estatísticas do WebRTC: o recebido de cada participante (`inbound-rtp`) e o meu microfone (`media-source`);
    - só republica as trilhas quando alguém começa ou para de falar;
    - novos campos `TrilhaRemota.falando` e `TrilhasChamada.falandoLocal`.
  - **Tela:** contorno verde (`chamadaEmAndamento`, cor do FMX) no participante que fala e na miniatura "Você".
- **Somente recepção:**
  - **`GerenciadorChamadas.ativarTransmissao(video)`:** abre o microfone (e a câmera, se pedida) e publica. Sem microfone, avisa "Não foi possível acessar o microfone" e continua só recebendo.
  - **Tela:** com a chamada ativa e sem mídia, "Ativar microfone" e, em vídeo, "Ativar câmera" (textos do web), pedindo a permissão na hora.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **292 testes, 0 falhas**;
  - novos: `DetectorDeFalaTest` (2) e 3 de somente recepção no `GerenciadorChamadasTest`;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, numa chamada de áudio com o B no web** (lendo a cor da borda do participante em capturas seguidas):
  - com o microfone do B ligado (o som falso do Chrome é contínuo), a borda ficou verde (`#85ff85`);
  - com ele desligado no web, ficou `#e0e0e0` (sem anel);
  - religado, voltou o verde.
- **Somente recepção:** coberto pelos testes. No emulador as permissões de microfone e câmera já estão concedidas, e não as revogo pela linha de comando (não mexer em configuração do aparelho).

## Pendências / próximos passos
- **Resto da 6.13:** tela e ponteiro remotos (WS 57 `{acao:"tela"}` / `{acao:"ponteiro"}`).
- **Testar "Ativar microfone/câmera"** num aparelho, entrando na chamada sem conceder as permissões.
