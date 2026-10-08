# 2026-10-08 · 02 · Etapa 4 — baixar para Downloads e compartilhar

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes / manifesto (`POST_NOTIFICATIONS` declarada)
- **Itens:** `TODO.md` 4.4 (ações do visualizador), 4.7; ANX-09
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
Os anexos só tinham "Abrir" (com outro app). O web tem "Download" no arquivo e "Baixar video" no vídeo. O TODO pede:
- compartilhar e baixar no visualizador;
- salvar em `MediaStore.Downloads`;
- notificação de download concluído.

## O que foi feito
- **`DownloadsRepositorio`** (`core/data/anexos`):
  - **Como salva:**
    - baixa para o cache com `ArquivosLocais.baixar`, que já renova a URL vencida e usa `.part`;
    - depois copia para **Downloads/Conversa**;
    - o tipo do arquivo vem da extensão, se não for informado.
  - **Onde roda:** no escopo do app, então sair da conversa não interrompe o download.
  - **No fim:** publica em `resultados` (`Salvo` com o URI, ou `Falhou`) e chama o `AvisoDownload`.
- **`PastaDownloadsAndroid`:**
  - **Android 10+:** grava no MediaStore em `Download/Conversa`, sem permissão.
    - O arquivo fica pendente (`IS_PENDING`) até terminar e é apagado se der erro.
    - O MediaStore renomeia sozinho quando o nome já existe.
  - **Android 9:** usa o URI do "Salvar como" (`CreateDocument`), sem pedir permissão de armazenamento.
- **`AvisoDownloadNotificacao`:** notificação "Download concluído" (canal "Downloads"); o toque abre o arquivo.
  - Só publica se as notificações estiverem permitidas. A permissão `POST_NOTIFICATIONS` foi declarada no manifesto, porque o lint exige, e será pedida na etapa 5.
- **Tela:**
  - `LinhaArquivo` ganhou o botão "Baixar";
  - `VideoNaBolha` ganhou "Baixar vídeo" embaixo (como o web);
  - o visualizador tem "Abrir com…", "Compartilhar" (baixa para o cache e abre o compartilhar do Android, via `FileProvider`) e "Baixar".
  - **Avisos:** "Baixando …"; quando termina, "… salvo em Downloads/Conversa" com o botão "Abrir"; se falhar, "Não foi possível baixar …".

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **196 testes, 0 falhas**;
  - 0 apontamentos de lint. Lint apontou 2 coisas, já corrigidas:
    - o `check()` não conta como guarda de versão para o lint; virou um `if`;
    - faltava a permissão de notificação no manifesto.
  - ktlint OK.
- **`DownloadsRepositorioTest`** (pasta e aviso falsos):
  - salva com o tipo certo pela extensão, conclui e avisa;
  - se não conseguir baixar, não cria nada;
  - erro ao gravar descarta o arquivo pela metade.
- **No emulador (Android 16):**
  - "Baixar" no PDF:
    - o arquivo ficou em `/sdcard/Download/Conversa/teste-conversa.pdf`, com 596 bytes;
    - o aviso "salvo em Downloads/Conversa" apareceu, e "Abrir" abriu o PDF no leitor do sistema.
  - No visualizador:
    - "Compartilhar" abriu o compartilhar do Android ("Sharing image");
    - "Baixar" salvou a foto com 63.998 bytes, igual ao original.

## Pendências
- **Android 9:** o caminho do "Salvar como" não foi testado (não há emulador Android 9 configurado).
- **Notificação de download concluído:** testar quando a permissão for pedida (etapa 5).
- **PDF:** visualizador interno (4.10); hoje "Abrir" usa outro app.
