# 2026-10-07 · 12 · Etapa 4 — gravação de áudio no campo

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes / manifesto (permissão nova)
- **Itens:** `TODO.md` 4.6 e uma linha nova na 4.7 (`nomeSeguro`); ANX-11, ENV-16; problemas #32 e #48 do legado
- **Branch:** `reescrita`
- **Commits:** `399bae5`

## Contexto
O campo não gravava áudio. O web grava pelo microfone (`useAudioRecording.ts`, `BarraGravacao.vue`, `MessageInput.vue`) e o app legado tinha o `AudioRecorderHelper`. Esse helper só pegava `IOException` em `start()` (#48) e pedia a permissão no lugar errado (#32).

## O que foi feito

### `:core:media`
- **`GravadorAudio`** / **`GravadorMediaRecorder`**: grava trechos em AAC/M4A, mono, 64 kbps.
  - **Por que AAC:** toca em todos os clientes (navegadores, Windows e Android). O web grava WebM/Opus, que nem todo cliente toca.
  - **Erros:** qualquer `Exception` ao abrir o microfone vira `false`, e o app nunca fecha (#48).
  - Se `stop()` for chamado logo depois de `start()`, o trecho fica sem dados e é jogado fora.
- **`juntarTrechos`** (`MediaMuxer` + `MediaExtractor`): copia as amostras de cada trecho, sem recodificar, deslocando o tempo.
  - **Decisão:** cada pausa fecha um trecho, e "continuar" abre outro.
  - Assim existe o "Ouvir gravação" com a gravação pausada, como no web. O `MediaRecorder` não deixa ouvir um MP4 que ainda está sendo gravado.

### Dados
- **`ArquivosLocais`:**
  - `novaPastaGravacao()` cria `cache/gravacoes/<n>/`;
  - `uriCompartilhado()` gera o URI do `FileProvider` (`caminhos_arquivos.xml` ganhou `gravacoes/`);
  - o logout limpa a pasta.
- **`FontesArquivoAndroid.liberar`:** depois de enviar, apaga a pasta da gravação inteira, incluindo os trechos que sobraram.
- **`nomeSeguro`** agora nunca devolve `.` nem `..`.
  - Antes, um nome `..` passava. Com isso, o arquivo baixado ou apagado podia sair da pasta (+ teste).
- **`MensagensRepositorio.avisarGravando`** chama `POST /conversa/gravando`.

### Tela
- **`ControleGravacao`** (`Gravacao.kt`): máquina de estados, testável sem Android. Estados: `Parada`, `Gravando` (segurando ou travada) e `Pausada`.
  - Começar a gravar para o áudio que estiver tocando.
  - Enquanto a gravação está aberta, gravando ou pausada, envia "gravando" a cada 2,5 s, como o web.
  - **Enviar:**
    - junta os trechos em `audio-<hora>.m4a`, tipo 5, `audio/mp4`;
    - vai pela mesma fila de envio;
    - menos de 1 s é descartado com o aviso "Áudio muito curto…". O 1 s vem do legado; o web não tem mínimo.
  - **Descartar**, arrastar para o lado ou sair da conversa apagam tudo.
- **`CampoMensagem.kt`**: o campo saiu do `ChatTela.kt`, que estava com mais de 600 linhas.
  - **Botões:** com o campo vazio aparece o microfone; com texto ou anexo, o Enviar (como o web).
  - **Gestos do microfone:**
    - apertar já começa a gravar, pedindo a permissão na hora com um launcher só para isso (#32);
    - segurar 300 ms ou mais e soltar envia (`HOLD_THRESHOLD_MS` do web);
    - toque curto ou arrastar para cima trava a gravação;
    - arrastar para o lado descarta;
    - se o sistema cancelar o gesto, a gravação fica travada e nada se perde.
  - **Acessibilidade:** um toque pelo leitor de tela começa a gravação já travada.
  - **Segurando:** o texto dá lugar ao tempo e a "‹ Deslize para cancelar". O botão do microfone fica no mesmo lugar, para o gesto não se perder.
  - **`BarraGravacao`**, com os textos do web:
    - Descartar;
    - ponto vermelho piscando + `m:ss` + barrinhas com o nível do microfone;
    - Pausar e Continuar gravação;
    - pausada: Ouvir gravação / Parar preview, com barra de progresso;
    - Enviar áudio.
  - **Segundo plano:** se o app vai para segundo plano com a gravação aberta, ela pausa, porque o Android corta o microfone de app em segundo plano.
- **Cabeçalho:** "… está gravando áudio…" aparece em vermelho (`gravandoAudio`), no texto e nos pontos. A lista de conversas não mostra, igual ao web.
- **Manifesto:** `RECORD_AUDIO`.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **193 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **`GravacaoTest`** (gravador e player falsos, relógio controlado):
  - segurar e soltar envia tipo 5 em `.m4a` e o "gravando" para;
  - menos de 1 s não envia e apaga os arquivos;
  - pausar, ouvir, continuar e enviar juntam os trechos em ordem;
  - um trecho sem dados não conta tempo;
  - descartar apaga tudo;
  - microfone que não abre avisa;
  - começar a gravar para o áudio que estava tocando.
- **`ArquivosLocaisTest`:** `nomeSeguro`.
- **No emulador** (a permissão do microfone foi concedida pelo diálogo do próprio app, "Durante o uso do app"):
  - **Toque curto:** a barra apareceu com o tempo correndo e o indicador de microfone do Android aceso.
  - **Pausa e preview:** pausar, depois Ouvir gravação, tocou o preview.
  - **Continuar e enviar:** foram criados 2 trechos, e chegou ao servidor uma mensagem tipo 5, `audio-….m4a` com 351 KB.
    - O arquivo baixado tem 42,42 s contínuos (1827 quadros AAC, cerca de 66 kbps).
    - A pasta `cache/gravacoes` ficou vazia depois do envio.
  - **Segurar e soltar:** segurar 3,5 s e soltar enviou o áudio na hora. O B recebeu o WS 5 duas vezes, com 2,5 s de intervalo, e depois o WS 2.
  - **Arrastar para a esquerda:** nada foi enviado e não sobrou arquivo.
  - **Arrastar para cima:** a barra travou, e Descartar voltou ao microfone sem sobrar arquivo.
  - **Segurar 0,5 s:** apareceu "Áudio muito curto. Segure o microfone para gravar.".
  - **B gravando:** quando o B avisou "gravando", o cabeçalho mostrou em vermelho "Teste Android B está gravando áudio…".

## Pendências
- **Teste instrumentado de `juntarTrechos`:** precisa de áudio real. O `connectedDebugAndroidTest` desinstala o app; por enquanto a verificação é no emulador, lendo a duração do MP4.
- **Gravação com texto escrito:** entra como peça no campo (ENV-21). Fica para a 4.11, junto com a decisão do campo rico (FC-416). Hoje o microfone só aparece com o campo vazio.
