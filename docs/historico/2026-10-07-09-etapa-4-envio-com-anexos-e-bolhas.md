# 2026-10-07 · 09 · Etapa 4 — envio com anexos, bolhas de imagem e arquivo (parcial, pausa)

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 4.1, 4.2, 4.3, 4.4, 4.7 (parte); ANX-01, ANX-03, ANX-04, ANX-05, ANX-09
- **Branch:** `reescrita`
- **Commits:** `c5cefbb`

## Contexto
Segundo bloco da etapa 4. O usuário pediu uma pausa no meio. Este commit deixa tudo compilando e testado na parte de dados. A parte de tela que já existe ainda **não foi testada no emulador**: no TODO ela está com 🔄, nunca ✅.

## O que foi feito

### Dados
- **`EnvioMensagens`** agora envia anexos:
  - a mensagem otimista mostra o arquivo local (`conteudo = "local:<uri>"`);
  - a fila (`PacoteEnvio`) guarda o corpo e a lista de anexos;
  - o `EnvioWorker` sobe cada anexo antes da mensagem e grava o identificador assim que sobe, então uma nova tentativa não sobe de novo;
  - ordem dos conteúdos: texto e depois os arquivos;
  - arquivo inacessível, grande demais ou recusado faz a mensagem inteira "falhar"; sem rede, espera;
  - `progresso` por mensagem, para a barra na bolha.
- **`FontesArquivo`**: lê nome, tamanho e MIME pelo `ContentResolver` e abre o arquivo em fluxo. `nomeSeguro` tira caminho e caracteres de controle.
- **`ArquivosLocais`**: baixa o anexo para `cache/anexos/<identificador>/` uma vez só (`.part` e depois renomeia), mais a pasta da câmera. São limpos no logout.
- **`EnvioPendenteDao.atualizarPayload`.**
- **Regras puras:** `tipoPorMime`, `ehVideo`, `formatarTamanho`, `Conteudo.local`.

### Tela (código pronto, falta testar no emulador)
- **`ImagemAnexo`**:
  - chave de cache do Coil = identificador;
  - URL assinada pelo ViewModel;
  - se falhar, esquece a URL e tenta mais uma vez;
  - mostra o arquivo local enquanto envia.
- **`BolhaImagem`** (hora e status por cima, barra de envio) e imagens dentro da bolha padrão.
- **`LinhaArquivo`** ("Abrir" baixa e abre com outro app pelo `FileProvider`; `res/xml/caminhos_arquivos.xml` e o provider no manifest).
- **`VisualizadorImagens`** (pager, pinça, duplo toque, legenda) e **`FilaAnexos`**: compostos prontos, ainda não ligados ao toque na bolha nem ao campo.
- **`ChatViewModel`**: fila de anexos, `adicionarAnexos` (acima de 1 GiB avisa e não entra), `removerAnexo`, envio com a fila, URL do anexo e abrir arquivo.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
  - **174 testes, 0 falhas**, entre eles os novos do `MensagensTest`:
    - anexos sobem antes;
    - nova tentativa não sobe de novo;
    - ordem correta;
    - sem acesso ao arquivo, a mensagem falha.
  - Também novos em `ChatTest`: tipo pelo MIME, vídeo e tamanho.
  - Lint: 0 apontamentos.
    - Corrigido: `getString` via `LocalContext` trocado por `LocalResources`;
    - `Uri.parse` trocado por `toUri`.
- **Não testado no emulador ainda.**

## Pendências (próximo passo, ao voltar)
1. Ligar no campo:
   - botão de anexo (Galeria/Câmera/Documento) com `takePersistableUriPermission`;
   - `FilaAnexos`;
   - Enviar habilitado com anexos.

   O código desse passo foi escrito, mas não entrou, porque o script que o aplicaria falhou ao ser interrompido pela pausa.
2. Ligar o toque na imagem ao `VisualizadorImagens`.
3. Testar no emulador com o `ferramentas/proxy-dev.mjs` (servidor `http://localhost:8081`, `adb reverse tcp:8081 tcp:8081`):
   - imagem e PDF nos dois sentidos;
   - abrir arquivo;
   - URL vencida;
   - vídeo de 200 MB.
4. Depois: áudio (4.5), gravação (4.6) e o restante da etapa 4.
