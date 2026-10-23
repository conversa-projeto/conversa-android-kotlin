# 2026-10-22 · 05 · Câmera com foto ou vídeo, prévia e legenda

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.2 (a linha 🆕 `77c7821`); FC-426; ANX-20
- **Branch:** `reescrita`
- **Commits:** `0128e8a`

## Contexto
No web (`77c7821`), "+" › "Câmera":
- no celular, abre a câmera do sistema, para foto ou vídeo;
- a prévia tem "Adicione uma legenda...", "Refazer" e "Enviar", que manda na hora, o arquivo antes da legenda.

No app, a "Câmera" só tirava foto, que ia para a fila de anexos e saía com o Enviar do campo, com o texto antes do arquivo.

## O que foi feito
- **`CameraDoCampo.kt` (`rememberCameraDoCampo`):**
  - com as permissões de uso `imagem` e `video`, a pessoa escolhe "Tirar foto" ou "Gravar vídeo"; com só uma delas, vai direto;
  - foto pelo `TakePicture`, vídeo pelo `CaptureVideo`, os dois no cache do app pelo FileProvider;
  - o arquivo, o tipo e a prévia sobrevivem à recriação da Activity enquanto a câmera está aberta.
- **A permissão de câmera do aparelho é pedida no toque:**
  - o app declara `CAMERA` (as chamadas), e nesse caso o Android exige a permissão dada para abrir a câmera do sistema;
  - sem ela, o `TakePicture` lançava `SecurityException`, que o código antigo não tratava;
  - negada: "Não foi possível abrir a câmera. Confira a permissão de câmera do app.".
- **Prévia** em tela cheia, no fundo do visualizador de mídia:
  - "Enviar" no topo, a foto (Coil) ou o vídeo (o `ReprodutorVideo` do Media3, com os controles);
  - "Adicione uma legenda…" (o Enter do teclado envia), "Refazer" (apaga e abre a câmera de novo) e "Enviar";
  - o × apaga o arquivo.
- **Envio:**
  - `ChatViewModel.enviarDaCamera` confere a permissão de uso do tipo e o tamanho e manda na hora (vale também com uma resposta, postagem ou confirmação pendente);
  - `EnvioMensagens.enviar(legendaDepois = true)` põe os anexos antes do texto.
- **O item "Câmera"** aparece com a permissão `imagem` ou `video` (antes, só `imagem`).
- **Teste:** `MensagensTest` — da câmera o arquivo vai antes da legenda; sem `legendaDepois`, o texto continua antes.

## Como foi verificado
- `:core:data:testDebugUnitTest`, `:feature:chat:testDebugUnitTest` e `assembleDebug`.
- **No emulador (A, na direta com o C), com a câmera do emulador:**
  - **foto:** "+" › "Câmera" mostrou "Tirar foto" e "Gravar vídeo". A foto abriu a prévia; com a legenda "Foto pela camera", "Enviar" mandou a mensagem 1204 com `[imagem, texto]`, nessa ordem (conferido pela API, como o C);
  - **vídeo:** gravado por 4 s, a prévia tocou o vídeo. Enviado sem legenda, chegou como a mensagem 1205, tipo 3 (vídeo com extensão, como o web), e a bolha mostrou o player e "Baixar vídeo".

## Decisões
- **A foto da câmera não vai mais para a fila de anexos:** segue o web, com a prévia e o envio na hora. A galeria e os documentos continuam pela fila.
- **Escolher foto ou vídeo antes,** porque não há um intent do sistema para os dois.
- **O fundo da prévia é o do visualizador de mídia** (`fundoVisualizadorMidia`, com texto branco, como no visualizador de imagens), sem cor nova.

## Pendências
- Nenhuma.
