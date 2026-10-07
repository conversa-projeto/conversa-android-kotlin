# 2026-10-07 · 10 · Etapa 4 — seletor de anexos, visualizador e teste no emulador

- **Fluxo:** Anexos e mídia (etapa 4); também Mensagens (etapa 3, lista acompanha a mensagem nova)
- **Tipo:** código / testes / ferramenta de desenvolvimento
- **Itens:** `TODO.md` 4.1 (teste de 200 MB), 4.2, 4.3, 4.4, 4.7, e uma linha nova na 3; ANX-01, ANX-03, ANX-04, ANX-05, ANX-09, ANX-14
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
Continuação do bloco 09. A tela de anexos estava pronta, mas não estava ligada ao campo e ainda não tinha sido testada no emulador. Ao testar, apareceram dois defeitos, ambos corrigidos aqui:
- a renovação de URL vencida não funcionava;
- uma mensagem nova ficava escondida embaixo da lista.

## O que foi feito

### Campo e fila (ANX-01, ANX-03)
- **`BotaoAnexar`** (`ChatTela.kt`) abre um menu com três opções:
  - **Galeria:** seletor de fotos do sistema, vários itens, imagens e vídeos, sem pedir permissão.
  - **Câmera:** `TakePicture` grava em `cache/camera` pelo `FileProvider`. O app não declara a permissão `CAMERA`, então não precisa pedi-la. Atenção: quando as chamadas (etapa 6) declararem `CAMERA`, será preciso pedir a permissão antes de abrir a câmera.
  - **Documento:** `OpenMultipleDocuments`.
- **Acesso aos arquivos:**
  - os URIs escolhidos guardam a permissão (`takePersistableUriPermission`), porque o envio pode ser feito depois, pelo WorkManager, com o app fechado;
  - se não houver nenhum app de câmera, aparece o aviso "Nenhum app de câmera instalado.".
- **Campo:** `FilaAnexos` fica acima do campo, e o Enviar fica habilitado quando há anexos mesmo sem texto.
  - A fila foi redesenhada: o "×" agora fica no fim da linha. Antes, era um círculo sobre o nome do arquivo.
- **Devolver o acesso** (`FontesArquivo.liberar`, `EnvioMensagens.desistirDoArquivo`):
  - quando a mensagem é enviada ou descartada, ou o arquivo é tirado da fila do campo, o app devolve a permissão guardada, porque o Android limita quantas um app pode ter;
  - a foto da câmera é apagada do cache;
  - um arquivo que outra mensagem da fila ainda vai enviar não é liberado.

### Imagens (ANX-04, ANX-05, ANX-14)
- **Imagem com URL vencida.** A primeira versão renovava a URL na tela: ao falhar, trocava o modelo do `AsyncImage` pela URL nova, mas o Coil nunca refazia o pedido.
  - O teste no emulador mostrou isso: o app pegava a URL nova (via `GET /anexo`), mas nenhum `/storage` saía e a bolha ficava em branco.
  - **Solução:** o modelo agora é `AnexoRemoto(identificador)` (`core/ui`), carregado pelo **`FetcherAnexo`** (`app/imagens`), registrado no `ImageLoader`. O fetcher:
    1. se a imagem já está no cache de disco (chave = identificador), usa o cache e nem pede URL ao servidor;
    2. senão, pede a URL ao `AnexosRepositorio` (que tem cache) e repassa ao fetcher de rede do Coil, que grava no disco com a mesma chave;
    3. se receber 403, esquece a URL e tenta uma vez com uma URL nova.
  - O `Keyer` usa o identificador como chave da memória.
  - O `ImagemAnexo` ficou simples. A tela e o ViewModel não lidam mais com URL: `urlDoAnexo` e `esquecerUrl` saíram de `AcoesBolha` e do `ChatViewModel`.
- **Visualizador:**
  - o toque na imagem (bolha só de imagem ou imagem dentro da bolha padrão) abre o `VisualizadorImagens`;
  - a lista de imagens vem de `imagensDaConversa`: todas as imagens da conversa, em ordem, menos as ocultas (+ teste);
  - a imagem aberta é lembrada pelo id da mensagem e pelo identificador; se ela sumir, o visualizador fecha;
  - o "×" ganhou um fundo escuro, porque sumia sobre imagens claras.
- `AcoesBolha` virou `data class` (a tela usa `copy` para pôr o `aoAbrirImagem`).
- `LinhaMensagem` recebe o `progresso` do envio, que aparece como uma barra na bolha.

### Lista de mensagens (etapa 3)
- **Mensagem nova escondida.** Com a lista no fim, uma mensagem nova ficava fora da tela, sem nenhum aviso: o `LazyColumn` mantém visível o item que já estava na tela, pela chave.
  - **Correção:** perto do fim, a lista rola até a mensagem nova. Longe do fim, continua o "Há novas mensagens" (para mensagem de outra pessoa).

### Ferramenta
- **`ferramentas/proxy-dev.mjs`:** com `URL_VENCIDA=1`, o primeiro GET de cada arquivo em `/storage` recebe 403, como o MinIO com URL expirada. Os GETs seguintes passam normalmente.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **177 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- Testes novos:
  - `AnexosTest`: imagens do visualizador, em ordem, sem as ocultas;
  - `MensagensTest`:
    - liberar o acesso só depois de enviar;
    - um arquivo usado por duas mensagens só é liberado quando a última sai;
    - tirar da fila do campo libera, menos o que está na fila de envio.
- **No emulador**, com `proxy-dev` (servidor `http://localhost:8081`):
  - **Do app (A) para o servidor:**
    - Galeria: 2 imagens com texto. O banco gravou o texto como conteúdo 1 e as imagens como 2 e 3, com o tamanho exato.
    - Documento: um PDF.
    - Câmera: uma foto. O arquivo saiu de `cache/camera` ao enviar e também ao remover da fila.
    - Os 3 anexos e a foto ficaram registrados como persistidos em `dumpsys activity permissions`.
  - **Do B (script) para A:** imagem e PDF na mesma mensagem.
    - Chegaram pelo WebSocket em menos de 0,5 s, e a lista desceu até eles depois da correção.
  - **Abrir:** o PDF baixou pelo proxy e abriu no leitor de PDF do sistema.
  - **Visualizador:** abriu, deslizou para a imagem anterior, deu zoom com duplo toque e mostrou a legenda e o "Abrir com…".
  - **URL vencida** (`URL_VENCIDA=1`, cache do Coil apagado):
    - para cada imagem: 403, nova `GET /anexo`, 200, e a imagem apareceu;
    - ao reabrir o app, nenhum pedido de anexo, tudo veio do disco.
  - **200 MB:**
    - o arquivo subiu inteiro e a barra apareceu na bolha;
    - a memória do app ficou estável, com PSS entre 116 e 125 MB durante o envio;
    - enviado de novo, só foram feitos `PUT /anexo` (o arquivo já existia) e `PUT /mensagem`.

## Pendências
- **Visualizador:** falta a tira de miniaturas; vídeos entram com o Media3 (4.4).
- **Áudio:** player (4.5) e gravação (4.6).
- **Arquivos:** baixar para `Downloads` e notificação de download concluído (4.7).
- **Etapa 4:** 4.8 a 4.11.
