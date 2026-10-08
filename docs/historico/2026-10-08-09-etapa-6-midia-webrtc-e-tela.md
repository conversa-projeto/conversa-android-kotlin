# 2026-10-08 · 09 · Etapa 6 — mídia WebRTC (WHIP/WHEP), tela da chamada e ligar pela conversa

- **Fluxo:** Chamadas (etapa 6); também Repositório e build (dependência WebRTC, proxy de dev) e Anexos e mídia (lint do `feature:chat`)
- **Tipo:** código / testes / build / documentação
- **Itens:**
  - `TODO.md` 6.1 (inteira) e partes de 6.4, 6.5, 6.7, 6.9 e 6.11;
  - FC-703, FC-704, FC-705, FC-706, FC-712, FC-714, FC-716; CHA-01, CHA-03, CHA-04, CHA-10, CHA-11, CHA-12;
  - problemas #7, #10, #18, #37 e #39 do legado.
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
Com a máquina de estados pronta (2026-10-08 · 08), faltava a mídia de verdade e um jeito de ligar e atender. Isso é o mínimo para o teste da 6.1: o arquivo gravado aparecer no MediaMTX.

O outro lado dos testes é o cliente web, como usuário B, num Chrome à parte com câmera e microfone falsos.

## O que foi feito
- **`:core:webrtc`:**
  - **WebRTC:** `io.github.webrtc-sdk:android` 144.7559.15, o fork mantido do libwebrtc.
    - A linha 150 só tem a primeira versão. O lint de "versão mais nova" foi silenciado só para essa biblioteca (`app/lint.xml`).
  - **`ClienteWhipWhep`:** WHIP e WHEP com o OkHttp do app.
    - O token não vai junto: só vai para `/api`.
    - Não há `TrustManager` próprio; o legado aceitava qualquer certificado.
    - Lê o `Location` e faz `DELETE` ao encerrar. O MediaMTX responde o caminho a partir da raiz dele; atrás do nginx a raiz vira `<base>/webrtc/`.
    - 404 vira `StreamAusente`.
  - **`FabricaWebRtc`:** `PeerConnectionFactory` e `EglBase` únicos por processo, com AEC/NS por hardware.
  - **`MidiaWebRtc`** implementa a `MidiaChamada`:
    - **Publicação:** só de envio; Opus a 32 kbps; câmera frontal a 360p e 15 fps; codecs em ordem H264 → VP9 → resto.
    - **Assinaturas:** uma por participante, só de recepção, registrada antes da resposta (#10). WHEP 404 tenta de novo (vídeo 40×1 s, áudio 12×0,8 s).
    - **Reconexão:**
      - a conexão que falha ou fica desconectada por 2 s é refeita sozinha;
      - o vídeo esperado que não chega em 5 s refaz a assinatura;
      - a publicação que cai é refeita.
    - **Concorrência:** objetos do WebRTC só numa vez única, e a rede não segura a vez (#18).
    - **Encerrar:** troca a sessão e desmonta conexões, trilhas, fontes e câmera (#37).
    - **Câmera desligada:**
      - a câmera fecha e a trilha recebe quadros pretos (320×180, 2 por segundo);
      - **achado no teste:** sem pacote nenhum ao publicar, o MediaMTX nem registra a trilha de vídeo;
      - sem isso, "Apenas assistir" publicava só o áudio e depois não dava para ligar a câmera.
    - **Trilhas para a tela:** vídeo local e de cada participante, com "conectado" e "falhou". Quem publica só áudio fica com o avatar (a resposta do MediaMTX diz se vem vídeo, `sdpEnviaVideo`).
- **`:feature:chamada`:**
  - **`ChamadaActivity`:** abre por cima da tela bloqueada e acende a tela. Quem a abre é o `ApresentadorChamada`, quando a chamada começa e o app está na frente.
    - **Recebida:** textos do web ("Chamada recebida", "… está ligando…", "Vídeo + Áudio"/"Somente Áudio"), Recusar e Atender grandes (#7) e "Atender só assistindo".
    - **Em chamada:**
      - cabeçalho com status, duração, tipo e nº de pessoas;
      - grade de participantes e miniatura local "Você";
      - faixa vermelha "Reconectando…";
      - controles Microfone, Câmera, Trocar câmera, Ativar vídeo e Sair da chamada (vermelhos quando desligados; `stateDescription` para o TalkBack);
      - modal "<nome> ativou o vídeo".
    - **Renderers:** `key(trilha)` + `onRelease` com `removeSink` protegido (#39).
    - **Permissões:** pedidas na hora de atender, ligar a câmera ou transmitir.
  - **`LigarViewModel` + `rememberLigar`:** os botões de voz e vídeo da conversa e o "ligar" da bolha de chamada.
    - Pedem o microfone (e a câmera, no vídeo).
    - Montam os participantes: direta = [eu, outro]; grupo = `GET /conversa/usuarios`.
    - Mostram os avisos do gerenciador.
  - **Integração:**
    - `GerenciadorChamadas` e `ApresentadorChamada` iniciados no `ConversaApplication`;
    - `ChamadasRepositorio` ligado no Hilt;
    - o aviso "chamadas na etapa 6" saiu da conversa.
- **`ferramentas/proxy-dev.mjs`:** `/webrtc/` vai para o MediaMTX (`127.0.0.1:8889`), como o nginx. A mídia passa pelo TURN em TCP 3478, por isso também é preciso `adb reverse tcp:3478 tcp:3478`.
- **`docs/desenvolvimento/emulador.md`:** a seção "Chamadas", com o web num Chrome de teste com câmera falsa, as gravações e a limitação da câmera falsa.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **274 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **Testes novos:**
  - `WhipWhepTest`: POST `application/sdp` no caminho do stream, `Location` em todos os formatos, 404 e 406, `DELETE`, ordem dos codecs, `sdpEnviaVideo`;
  - `TelaChamadaTest`: duração e textos dos avisos.
- **No emulador (usuário A) contra o web (usuário B, Chrome com câmera e microfone falsos):**
  - **App liga em vídeo:** a permissão de câmera apareceu na hora; o web mostrou "Teste Android A está ligando..." e atendeu. No app ficou "Em chamada", com o cronômetro, o vídeo do web e a miniatura da câmera do emulador. O web recebeu o vídeo do app.
  - **Gravação (teste da 6.1):** `call-1-u-1` (app, **VP9 + Opus**; o emulador não tem H264 por hardware) e `call-1-u-2` (web), crescendo durante a chamada.
  - **Sair no app:** o web encerrou, o stream do app sumiu do MediaMTX (404) e a bolha "Chamada de vídeo · 00:52" apareceu na conversa.
  - **Web liga em áudio:** o app abriu sozinho a tela "Chamada recebida", atendeu, e o web conectou ao áudio do app.
  - **Upgrade pelo web (56):**
    - o modal apareceu, e "Apenas assistir" mostrou o vídeo do web;
    - antes da correção dos quadros pretos, o app republicava só com áudio;
    - depois dela: 2 trilhas (Opus, VP9), e ao ligar a câmera o web passou a ver o vídeo.
  - **Trocar câmera:** frontal (1) → traseira (10).
  - **Sair no web:** o app encerrou sozinho (55 → dados → ninguém → sair).
  - **Recusar no app:** o web parou (53).
  - **Cancelar no web enquanto tocava:** a tela do app fechou (52).
  - **Upgrade pelo app ("Ativar vídeo"):** o web recebeu o 56 ("Teste Android A ativou o vídeo") e o vídeo do app.
    - O vídeo do web não voltou porque a câmera falsa do Chrome não abre de novo na mesma sessão; é limitação do teste, documentada.
    - Isso mostrou que um participante sem vídeo aparecia como quadro preto. Agora aparece o avatar.

## Decisões
- **Quadros pretos com a câmera desligada,** em vez de manter a câmera aberta (que deixaria o indicador de câmera do Android aceso). O web manda quadros pretos com a trilha desabilitada; o efeito para quem assiste é o mesmo.
- **`ChamadaActivity` separada,** por cima de tudo. É ela que a notificação de tela cheia vai abrir (6.5) e que vira PiP (6.12).
- **`feature:chat` depende de `feature:chamada`** só para os botões de ligar. O `ChatViewModel` não mudou.

## Pendências / próximos passos
- **6.3 e 6.5:** Core-Telecom, notificação CallStyle com tela cheia, toque e som de chamando, serviço em primeiro plano. Hoje a chamada só abre sozinha com o app na frente.
- **6.8:** rota de áudio (alto-falante, fone, Bluetooth), foco de áudio e parar o áudio de mensagem.
- **6.12:** banner "voltar à chamada" e PiP.
- **Não testado no emulador:** chamada em grupo, queda de rede (refazer a conexão), TalkBack.
- **64/128 kbps de áudio e as outras qualidades:** chegam com a configuração de chamadas (etapa 8).
