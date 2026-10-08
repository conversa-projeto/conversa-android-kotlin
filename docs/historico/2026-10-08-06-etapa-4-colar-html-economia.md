# 2026-10-08 · 06 · Etapa 4 — colar imagem, HTML e economia de dados

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 4.10 (colar, HTML, economia); FC-412, FC-413, FC-414
- **Branch:** `reescrita`
- **Commits:** `d9d03f1`

## Contexto
Os últimos itens da 4.10:
- colar imagem do teclado ou da área de transferência;
- abrir HTML anexado;
- "Toque para carregar". No web, esse aviso aparece com conexão lenta (`useConexao.ts`: `effectiveType` 2g ou slow-2g).

## O que foi feito
- **Colar imagem (FC-412):**
  - **Campo novo:** o campo de mensagem passou a usar `TextFieldState` (`rememberTextFieldState`), porque o `Modifier.contentReceiver` só funciona com ele. O estado continua local e síncrono (a regra do `CLAUDE.md` vale), e o "digitando" sai por `snapshotFlow`.
  - **Imagem colada:** ao colar uma imagem (`MediaType.Image`), o `receptorDeImagens` manda os URIs para `ChatViewModel.colarAnexos`. O resto (texto) segue para o campo.
  - **Cópia:** `Compartilhamentos.copiarColados` copia para o cache com as mesmas regras do compartilhar (só `content://` de outro app, até 1 GiB), porque a permissão do teclado ou da área de transferência é temporária. Depois a imagem entra na fila.
- **HTML (FC-413):** "Abrir" já abria com outro app pelo `FileProvider`. O Conversa não tem WebView, então não há risco de acesso a arquivos ou cookies. Testado no emulador.
- **Economia de dados (FC-414):**
  - **`EconomiaDados`** (`core/data/rede`): acompanha a rede padrão (`registerDefaultNetworkCallback`) só enquanto uma conversa está aberta.
  - **Regra (`deveEconomizar`):**
    - economiza com a conexão lenta: abaixo de 150 kbps, o equivalente ao 2g em que o web bloqueia;
    - ou com a Economia de dados do Android ligada numa rede medida;
    - com a velocidade desconhecida (0), não bloqueia, como o web quando não tem a API de conexão.
  - **`CarregarSobToque`:** nas bolhas de imagem e de vídeo mostra "Toque para carregar" (texto do web). O toque libera aquela mídia. O visualizador, aberto de propósito, não bloqueia.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **211 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **Testes novos:**
  - `EconomiaDadosTest`: conexão lenta, economia só em rede medida, velocidade desconhecida;
  - `ChatViewModelTest`:
    - quem vem do "Enviar para…" recebe a fila e o texto uma vez;
    - uma conversa aberta normalmente não pega o compartilhamento;
    - imagem colada entra na fila sem repetir.
- **No emulador:**
  - **Campo novo:** digitar, apagar 5 letras e redigitar, e enviar funcionaram, e o campo limpou depois de enviar. O B recebeu o "digitando" (WS 4) no máximo a cada 2,5 s e depois a mensagem.
  - **HTML do B:** o Android ofereceu Chrome e HTML Viewer, e a página abriu.
  - **Não testado no emulador:**
    - colar de verdade: o teclado do emulador não tem imagens sem internet, e copiar imagem pelo Chrome pediria aceitar os termos dele;
    - "Toque para carregar": exigiria mudar a rede ou a Economia de dados do aparelho.

## Pendências
- **Testes de ponta a ponta** de colar e da economia de dados: num aparelho de verdade.
- **4.11 (campo rico):** depende da decisão FC-416, que é do usuário.
