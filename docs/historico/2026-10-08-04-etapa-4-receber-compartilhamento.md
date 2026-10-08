# 2026-10-08 · 04 · Etapa 4 — receber compartilhamento de outros apps ("Enviar para…")

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código / testes / manifesto (intent-filters novos) / segurança
- **Itens:** `TODO.md` 4.9; AND-10 (só Android; o web não tem)
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O Conversa não aparecia no "Compartilhar" do Android. Pelo TODO 4.9:
- texto, imagens, vídeos e arquivos de outros apps devem chegar numa tela "Enviar para…";
- a conversa escolhida abre com os itens na fila.

## O que foi feito
- **Manifesto:** `MainActivity` aceita `ACTION_SEND` e `ACTION_SEND_MULTIPLE` com qualquer tipo (`*/*`).
- **`lerCompartilhamento`** (`app/Compartilhar.kt`): lê `EXTRA_TEXT` e `EXTRA_STREAM` (um ou vários) e devolve `null` se o intent não for um compartilhamento ou vier vazio.
- **`Compartilhamentos`** (`core/data/anexos`):
  - **Cópia:** os arquivos vão para `cache/compartilhados/<n>/` na hora, porque a permissão de leitura do compartilhar é temporária e o envio pode acontecer depois, pelo WorkManager.
  - **O que é aceito:**
    - só `content://` de outro app. `file://` e URIs do próprio app são recusados: outro app poderia fazer o Conversa mandar os próprios arquivos privados (a sessão, por exemplo) para uma conversa;
    - arquivos acima de 1 GiB ficam de fora;
    - os recusados são contados em `ignorados`.
  - **Entrega:** os itens ficam guardados até a pessoa escolher a conversa e são entregues uma vez só (`retirar`).
    - Voltar descarta a cópia.
    - Um compartilhamento novo substitui o pendente. Arquivos já entregues a um chat são da fila dele; saem pelo `FontesArquivo.liberar`, que agora também conhece `compartilhados/`.
  - **`FileProvider`:** `caminhos_arquivos.xml` ganhou `compartilhados/`. O logout limpa a pasta.
- **`MainViewModel.receberCompartilhamento`** copia os itens e leva ao "Enviar para…". Sem sessão, guarda o compartilhamento e abre depois do login (como o link).
- **`EnviarParaRotaTela`** (`feature/conversas/enviarpara`):
  - **Lista:** conversas na ordem da lista e contatos sem conversa direta; escolher um contato cria a conversa.
  - **Busca.**
  - **Resumo na barra:** "1 arquivo", o texto, quantos não puderam ser enviados.
  - **Voltar** descarta o compartilhamento.
- **Navegação:**
  - `RotaEnviarPara`;
  - `RotaChat(comCompartilhamento = true)`: o `ChatViewModel` retira os itens, os arquivos entram na fila e o texto vai para o campo. O campo usa o texto uma vez (`textoParaCampo` / `textoUsado`), para a pessoa revisar antes de enviar.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **205 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **`CompartilharTest`:**
  - texto, um arquivo, vários arquivos;
  - outro intent ou vazio não conta.
- **`CompartilhamentosTest`:**
  - copia o arquivo de outro app e entrega uma vez só;
  - recusa `file://` (o caminho da sessão), o URI do próprio app e o que passa de 1 GiB;
  - sem nada aceito, não há o que enviar;
  - desistir apaga a cópia;
  - um novo compartilhamento substitui o pendente.
- **No emulador:**
  - **Texto** (enviado com `am start … SEND`): abriu o "Enviar para…" com o texto no subtítulo; escolhida a conversa, o texto estava no campo.
  - **PDF pelo app Arquivos do Android:**
    - o Conversa apareceu no compartilhar;
    - abriu "Enviar para… · 1 arquivo" e a cópia ficou em `cache/compartilhados`;
    - escolhido o contato B, abriu a conversa direta com o PDF na fila;
    - ao enviar, chegou ao servidor (mensagem 1074, tipo 3) e a cópia saiu do cache.
  - **Não deu para testar com `am start` e `EXTRA_STREAM`:** o shell do `adb` não pode conceder leitura de um URI do MediaStore. O teste com o app Arquivos é o caminho real.

## Pendências
- **Destinos:** a lista não mostra as conversas arquivadas separadas. Hoje entram todas na ordem da lista.
- **Atalhos de compartilhamento direto (Sharing Shortcuts):** fica para depois.
