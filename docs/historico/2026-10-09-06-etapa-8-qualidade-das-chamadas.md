# 2026-10-09 · 06 · Etapa 8 — Configurações: qualidade das chamadas

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8). Toca também o fluxo de chamadas (mídia WebRTC).
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 8.5 (Chamadas); FC-807; CFG-06, CHA-20
- **Branch:** `reescrita`
- **Commits:** `705ee2e`

## Contexto
O web tem "Configurações → Chamadas" (`ConfiguracaoChamadas.vue`, `useConfigChamada.ts`), guardado no navegador:
- **Áudio:** redução de ruído, cancelamento de eco e ganho automático (padrão ligados); qualidade Normal 32 kbps / Alta 64 kbps / Música estéreo 128 kbps.
- **Vídeo:** resolução 360p/720p/1080p, quadros 15/24/30 e limite de banda Automático / Econômico 0,5 Mbps / Alto 3 Mbps.
- **Padrões no celular:** 360p e 15 fps.
- **"Restaurar padrão".**

O web pede estéreo no SDP do Opus quando transmite em "Música", e sempre ao assinar, para ouvir quem transmite assim.

No Android, a mídia (`MidiaWebRtc`) tinha tudo fixo: Opus a 32 kbps e câmera 640×360 a 15 fps. O eco e o ruído do aparelho estavam sempre ligados, numa fábrica criada uma vez por processo.

## O que foi feito
- **`core:model` — `ConfigChamada`:** as opções e os padrões do web no celular.
  - Vira texto para guardar (`ruido=1;eco=1;…`). O que faltar ou não for reconhecido fica no padrão, como o `{ ...padrao(), ...salvo }` do web.
  - `FonteConfigChamada` é a interface por onde a mídia lê a configuração.
- **Preferências:**
  - `PreferenciasStore`: chave `config_chamada`;
  - `PreferenciasRepositorio`: `chamada`, `alterarChamada` e a implementação de `FonteConfigChamada`, ligada no Hilt (`DadosModulo`).
- **`core:webrtc`:**
  - **`FabricaWebRtc`:** o `AudioDoAparelho` (eco e ruído do hardware, microfone e saída estéreo) só se escolhe ao criar o `JavaAudioDeviceModule`. `prepararAudio` descarta a fábrica quando o pedido muda, e a próxima sai com ele. A `MidiaWebRtc` só chama isso no começo da chamada, com nada vivo (nem trilha nem conexão).
  - **`MidiaWebRtc`:** lê a configuração no `abrirLocal`, que é o começo de cada chamada. Com ela:
    - o processamento em software do microfone (`googEchoCancellation`, `googNoiseSuppression`/`googHighpassFilter`, `googAutoGainControl`);
    - a câmera em `startCapture(largura, altura, fps)`;
    - os limites de envio: bitrate do Opus, fps e teto de banda do vídeo;
    - o estéreo na oferta em "Música". Ao assinar, a oferta sempre aceita estéreo, como o web.
  - **`comOpusEstereo`:** põe `stereo=1;sprop-stereo=1` no `a=fmtp` do Opus. É só texto, sem regex, por causa do ICU.
- **`:feature:config` — "Chamadas":**
  - as opções e os textos do `ConfiguracaoChamadas.vue`, com "Restaurar padrão" na barra;
  - o aviso "As mudanças valem a partir da próxima chamada.";
  - o `SubTela` ganhou espaço para ações na barra.
  - A "Prioridade" do compartilhamento de tela fica para a etapa 9, junto com o compartilhamento.
- **Testes:**
  - `ConfigChamadaTest`: padrões, ida e volta pelo texto, valores inválidos;
  - `SdpOpusTest`;
  - `QualidadeChamadasViewModelTest`.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (conta A).** Chamadas do próprio app para a conta de teste C, desligadas logo depois.
  - **Eco desligado e "Música":** a fábrica foi refeita, e o microfone abriu em estéreo (`AudioRecord … channels: 2`), sem erro.
  - **"Restaurar padrão" e nova chamada:** a fábrica foi refeita de novo, no mesmo processo e sem queda, e o microfone voltou ao mono (`channels: 1`).
  - **720p a 30 fps em chamada de vídeo:** `CameraCapturer: startCapture: 1280x720@30`.
  - **No fim:** voltei ao padrão.
- **Não deu para conferir no emulador:** se o eco e o ruído do hardware desligam de fato. O emulador não tem esses efeitos, e isso entra no teste em aparelho real.

## Decisões
- **Quando vale:** a mudança vale a partir da próxima chamada. No web, parte dela vale na hora: reabre o microfone e aplica as restrições do vídeo. Aqui, mudar no meio da chamada pediria refazer as fontes e as trilhas. A aba de configurações quase nunca é aberta durante uma chamada, e a tela avisa.

## Pendências
- **Aparelho real:** conferir o eco e o ruído do hardware desligados e ouvir o estéreo do outro lado. Duas linhas novas na matriz de `docs/testes/chamadas.md` ("Áudio e sistema").
- **O resto do 8.5:** Sistema e Acessos.
