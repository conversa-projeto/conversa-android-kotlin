# 2026-10-07 · 11 · Etapa 4 — player de áudio (Media3) e `:core:media`

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes / build (módulo e dependência novos)
- **Itens:** `TODO.md` 1.2 (`:core:media`), 4.3 (download com URL vencida), 4.5; ANX-10, ANX-14
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
Áudios (tipo 4, arquivo, e tipo 5, gravação) apareciam como arquivo, só com "Abrir". O web toca o áudio na bolha, um por vez, e marca o áudio de outra pessoa como ouvido no primeiro play (doc 03, ANX-10).

## O que foi feito

### Módulo `:core:media` (novo)
- **Media3 1.11.1** (`media3-exoplayer`), adicionado ao `libs.versions.toml`.
- **`PlayerAudio`** (interface) e **`PlayerMedia3`**: um player único para o app inteiro.
  - Tocar um áudio para o anterior.
  - Pede o foco de áudio e pausa quando o fone é desconectado.
  - A posição é lida a cada 200 ms enquanto toca.
  - No fim, volta ao início parado (como o web).
  - Se der erro, avisa em `falhas` com a chave do áudio e volta ao estado inicial.
  - É criado na thread principal. `setLooper` é uma API instável do Media3 e o lint recusa; não é necessário.
- **`MidiaModulo`** (Hilt) liga `PlayerAudio` a `PlayerMedia3`.

### Dados
- **`ArquivosLocais.baixar`**: se a URL estiver vencida (403), pede outra e tenta mais uma vez.
  - Antes, só esquecia a URL e falhava com "Falha ao baixar (403)".
  - Isso vale para "Abrir" e para o áudio. Foi achado no emulador com o `proxy-dev` em `URL_VENCIDA=1`.
- **`MensagensRepositorio.marcarReproduzida`** no primeiro play de um áudio de outra pessoa:
  - marca no Room na hora (`MensagemDao.marcarReproduzidaPorMim`);
  - chama `POST /mensagem/reproduzir` uma vez só, e de novo se falhar;
  - não marca a mensagem como lida (o servidor também não marca).

### Tela
- **`ChatViewModel`**, áudio:
  - **`alternarAudio`**:
    - no primeiro toque, baixa o arquivo para o cache e toca;
    - nos toques seguintes, pausa e continua;
    - durante o envio, toca o arquivo local;
    - se outro áudio for pedido enquanto um ainda baixa, só toca o último.
  - **`buscarAudio`** só age no áudio que está no player.
  - **`audio`** (`AudioNaConversa`): ignora o que toca em outra conversa e guarda a duração de cada áudio que já tocou.
  - **`onCleared`**: ao sair da conversa, o áudio dela para.
  - A chave do áudio é `conversa:mensagem:ordem`, porque a mesma gravação pode estar em duas mensagens.
- **`PlayerNaBolha`** (`Audio.kt`):
  - play/pause; enquanto baixa, um indicador de progresso;
  - barra fina própria, em que tocar ou arrastar pula para a posição; leitores de tela veem um controle de valor. O `Slider` do Material 3 de 2026 é alto demais para a bolha;
  - tempo `mm:ss`: a posição enquanto toca ou parado no meio, e a duração nos outros casos; "--:--" até saber;
  - tipo 4 mostra o nome do arquivo;
  - botão verde para áudio de outra pessoa não ouvido.
- **Cor do "não ouvido":** o FMX não tem uma. Foi usada a `waveform` (#00D26A), que está em `cores.md`.
- **`LocalAudio`** leva o `StateFlow` do áudio às bolhas. Só as bolhas de áudio leem, então a posição, que muda várias vezes por segundo, não recompõe a lista.
- **Textos:** "Ouvir", "Pausar" e "Não foi possível tocar este áudio.".
- **Limpeza:** `CorpoPadrao` tinha um `if (progresso != null)` quebrado em três linhas pelo ktlint; virou `progresso?.let`.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **185 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- Testes novos:
  - **`PlayerMedia3Test`** (Robolectric com o Media3 de verdade):
    - parar sem ter tocado;
    - arquivo inexistente, que avisa a falha com a chave e volta ao estado inicial.
  - **`ArquivosLocaisTest`**:
    - 403 pede outra URL e baixa;
    - a segunda vez usa o cache;
    - 404 não tenta de novo.
  - **`MensagensTest`:** reproduzida é marcada no Room e no servidor uma vez só, e não marca lida.
  - **`ChatViewModelTest`**, com um player falso:
    - baixa uma vez, toca, marca como ouvido só o áudio de outra pessoa;
    - pausa e continua;
    - pular só no áudio do player;
    - guarda a duração;
    - para ao sair da conversa;
    - áudio de outra conversa não aparece nem é parado.
- **No emulador** (proxy em `URL_VENCIDA=1`; o B mandou WAVs gerados de 7 s, tipo 4 com nome, e 4 s, tipo 5):
  - O download passou pelo 403 e tocou.
  - Botões: "Pausar" e `00:02` durante o play; no fim, "Ouvir" e `00:04`.
  - O botão verde virou neutro depois de ouvir.
  - No banco, `mensagem_status.reproduzida` ficou gravado só no áudio tocado.
  - Pular para 75% mostrou `00:05`.
  - Tocar o segundo áudio parou o primeiro.
  - Ao voltar da conversa, 0 players ativos (`dumpsys audio`).

## Pendências
- **Chamada:** ao começar uma chamada, chamar `PlayerAudio.parar()` (etapa 6).
- **Gravação de áudio:** 4.6.
- **Vídeo:** no visualizador e na bolha, com o Media3 (4.4).
