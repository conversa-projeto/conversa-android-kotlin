# 2026-10-17 · 04 · Enviar localização ou trajeto

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.12 (a linha "Enviar"); FC-528; ENV-30
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **No web** (`f351190`, `LocalizacaoModal.vue`), "+" › "Localização" escolhe um local ou um trajeto de quatro formas:
  - pela posição do aparelho (tenta ao abrir);
  - pela busca de endereço (Nominatim);
  - por coordenadas digitadas;
  - clicando no mapa.
- **O trajeto** calcula a rota de carro no OSRM público; sem resposta, vai a distância em linha reta. "Enviar" manda na hora.
- **No app,** o bloco anterior (`eaa4f42`) só mostrava a localização recebida.

## O que foi feito
- **Serviços de mapa:**
  - `core:network` `mapas/ServicosDeMapa.kt`: os endereços do web (busca, nome do lugar, rota) e a leitura das respostas. O nome é cortado em 300 caracteres; distância e tempo da rota saem inteiros;
  - `core:data` `mapas/MapasRepositorio.kt` chama sem prender a thread, e cancelar a coroutine cancela o pedido. A busca falha com "A busca de endereço não respondeu."; o nome e a rota ficam nulos quando o serviço falha;
  - **o token do servidor não vai** para esses hosts (só a API recebe, pelo `AutenticacaoInterceptor`);
  - **as requisições de mapa ficam fora do log do debug** (`ehServicoDeMapa`): mostrariam o endereço buscado e a região vista.
- **Envio:**
  - `EnvioMensagens.enviar(localizacao = …)` manda o conteúdo tipo 9 depois do texto, como a figurinha;
  - `ChatViewModel.enviarLocalizacao` manda na hora, sozinha (vale também com uma resposta, postagem ou confirmação pendente);
  - `montarLocalizacao` (`core:model`) monta o que vai, como o `resultado()` do web.
- **Janela "Enviar localização"** (`EnviarLocalizacao.kt`, em tela cheia):
  - Local ou Trajeto;
  - **um campo por ponto:** •, A ou B nas cores das chamadas, com a lupa e "Usar minha posição". Os resultados da busca aparecem numa lista embaixo do campo. Só o campo ativo (o que recebe o toque no mapa) fica com a borda colorida;
  - **o mapa interativo do bloco anterior:** tocar marca o campo ativo e fecha o teclado. Marcar a origem passa o toque para o destino;
  - **a linha embaixo do mapa,** com os textos do web ("clique" virou "toque"): o aviso em vermelho, "Buscando…", "Calculando a rota…", "1,7 km · 4 min de carro", "Sem rota pelas ruas: … em linha reta" ou a dica;
  - "Cancelar" e "Enviar".
- **`EnviarLocalizacaoViewModel`:**
  - o estado da janela, que sobrevive ao girar a tela, e o texto de cada campo num `TextFieldState`;
  - o nome do lugar é pedido ao marcar pela posição, pelo toque ou por coordenadas, e o pedido antigo do mesmo campo é cancelado;
  - a rota sai 300 ms depois do último ponto (o mesmo debounce do web), e a linha antiga some enquanto calcula;
  - o mapa centraliza (zoom 16) ou enquadra tudo, como o `MapaLeaflet`.
- **Posição do aparelho** (`PosicaoDoAparelho.kt`, `LocationManager`, sem Google Play Services):
  - vale uma posição de até 60 s; senão, pede a todos os provedores ligados e fica com a primeira, em até 15 s;
  - **o pedido é feito na hora do toque** em "Usar minha posição", com a aproximada ou a precisa. Ao abrir, só tenta se a permissão já foi dada;
  - o manifesto do `feature:chat` declara as permissões de localização, e o GPS é opcional (`uses-feature required=false`).
- **"+" › "Localização"** aparece só com a permissão de uso `localizacao`, entre Código e Votação, como no web.
- **Testes:**
  - `ServicosDeMapaTest`: endereços, hosts fora do log e as três respostas;
  - `MapasRepositorioTest`: MockWebServer, sem `Authorization`, e as falhas;
  - `EnviarLocalizacaoViewModelTest` (6): toque e nome, trajeto com debounce, linha reta, busca com coordenadas, resultados e falhas, minha posição calada e com aviso, abrir e girar;
  - `MensagensTest`: o tipo 9 sozinho;
  - `LocalizacaoTest`: o `montarLocalizacao`.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`.
- **No emulador (A, na direta com o C), contra o servidor de dev:**
  - **busca:** "Praca da Se Sao Paulo" trouxe seis resultados. Escolher um centralizou no zoom 16 e ligou o "Enviar";
  - **trajeto:** com a origem já marcada, tocar no mapa marcou o destino. O nome veio do Nominatim, e o OSRM deu "1,7 km · 4 min de carro" com o mapa enquadrando A, B e a rota. Enviado, chegou com ✓✓;
  - **minha posição:** o pedido de permissão apareceu e foi concedido "só desta vez". Com o GPS simulado no MASP (`adb emu geo fix`), o ponto e o nome "Museu de Arte de São Paulo, 1578, …" vieram; enviado;
  - **pela API, como o C:** as mensagens 1197 e 1198 têm o JSON do web (`pontos`, `rota`, `distancia` e `duracao` inteiras, sem `null`).
- **Na primeira versão,** "Usar minha posição" esperava 15 s e falhava:
  - o provedor combinado, sozinho, pede precisão "equilibrada", que usa a rede (desligada no emulador) e não liga o GPS;
  - daí o pedido a todos os provedores ao mesmo tempo (o web pede alta precisão).
- **Na primeira versão,** o campo mostrava o fim do endereço longo e o teclado ficava aberto depois de escolher um resultado: agora o cursor vai para o começo e o teclado fecha.
- **Não deu para conferir no web:** o web de dev estava fora do ar (nginx 502). Fica pendente a linha **Teste** do 4.12 (local e trajeto nos dois sentidos com o web).

## Decisões
- **Pedir a permissão de localização só no toque** em "Usar minha posição" (o web tenta ao abrir, e o navegador pergunta na hora). Ao abrir, o app só tenta com a permissão já dada, para não pedir antes de a pessoa mostrar que quer a própria posição. A dica explica as outras formas.
- **`LocationManager` em vez do provedor do Google Play Services:** o app precisa funcionar sem o Google (ADR 0002, FCM opcional).
- **Marcadores não se arrastam** (no web, sim): tocar no mapa já remarca o campo ativo.
- **Sem "Inserir na mensagem":** depende do campo rico (FC-416).

## Pendências
- A linha **Teste** do 4.12, com o web no ar.
- A tela Permissões (8.5) ainda não lista a localização (fica para quando mexerem nela).
