# 2026-10-07 · 07 · Etapa 3 — tela de chat, testada de ponta a ponta no emulador

- **Fluxo:** Mensagens (etapa 3)
- **Tipo:** código / testes / documentação
- **Itens:** `TODO.md` 3.1–3.11; CON-09, CON-12, MSG-01…16, ENV-01, ENV-15
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
Segundo bloco da etapa 3: a conversa aberta, em cima das regras e da camada de dados do bloco anterior (`2026-10-07-06`). Substitui o chat provisório da etapa 2.

## O que foi feito

### `:feature:chat` (módulo novo)
- **`ChatViewModel`**:
  - recarrega as recentes ao abrir (também quando vem de link/notificação);
  - a primeira não lida é decidida **uma vez**, depois da 1ª carga;
  - página anterior perto do topo;
  - marca como lidas as de outras pessoas que aparecem na tela;
  - envia e só então avisa a tela para limpar o campo;
  - "digitando" no máximo 1 a cada 2,5 s, com o último adiado e não descartado; o limite recomeça ao enviar;
  - menção abre (ou cria) a conversa direta;
  - Reenviar e Apagar.
- **`ChatTela`**:
  - cabeçalho com avatar, online, membros do grupo e digitando/gravando com os textos do web; botões de voz, vídeo (avisam "etapa 6") e membros;
  - lista `reverseLayout` com separador de dia ("Hoje"/"Ontem"/data) e a linha "Últimas";
  - ao abrir, posiciona na primeira não lida;
  - botões "Ir para o final" e "Há novas mensagens";
  - campo com Enviar.
- **`Bolhas`**:
  - texto curto (hora ao lado) e padrão;
  - só emojis;
  - oculta (toque revela);
  - chamada (título, duração ou status, participantes, cores do FMX);
  - código em bloco monoespaçado;
  - citação com o resumo do web;
  - marcadores para imagem, arquivo, áudio e figurinha;
  - votação ("📊 Votação" + "Abra no computador para votar");
  - links e menções clicáveis (`LinkAnnotation`);
  - status: relógio, ✓, ✓✓, ✓✓ azul, ⚠ + Reenviar/Apagar.
- O chat provisório do `:app` saiu.

### Dados
- `SyncManager`: id do WS 3 que não está no cache faz buscar as mensagens que faltam. Sem isso, o resumo de chamada nunca aparecia, porque só gera WS 3 (contrato §9.8).
- `MensagensRepositorio.avisarDigitando`.

### Correções achadas no emulador
- **O app fechava ao abrir uma conversa com mensagem:**
  - causa: o regex `(?U)\s+` (`Texto.kt`), aceito pela JVM dos testes e recusado pelo regex do Android (ICU);
  - correção: os espaços são juntados à mão;
  - `\w` virou `[A-Za-z0-9_]`, porque no ICU `\w` aceita acentos e no web não;
  - **teste instrumentado novo** `RegexNoAndroidTest` (roda no emulador, com o ICU de verdade);
  - regra registrada no `CLAUDE.md`.
- **Faltava a linha "Últimas" ao abrir com não lidas:** o Room já tinha as mensagens pela sincronização, e a tela se posicionava antes de o ViewModel decidir a primeira não lida. Agora espera (`pronto`).

### Documentação
- `CLAUDE.md`: comando do teste no emulador (que desinstala o app), regra do regex (ICU × JVM), regra dos campos de texto e o módulo `chat`.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
  - **164 testes, 0 falhas**, entre eles o `ChatViewModelTest` (4) e o WS 3 com id fora do cache no `SyncManagerTest`.
  - Lint: 0 apontamentos (trocado `screenWidthDp` por `LocalWindowInfo`).
- `gradlew :app:connectedDebugAndroidTest` no emulador → `RegexNoAndroidTest` passou.
- **Emulador contra o servidor de dev:**
  - **Envio:** enviar → chegou ao banco; o link sai clicável e o "." final fica fora dele.
  - **Recebimento e leitura (conta B, Node):**
    - B leu → meu ✓✓ ficou azul sozinho;
    - a mensagem de B apareceu sozinha, com o nome;
    - fiquei como leitor dela no banco.
  - **Sem servidor:** com `docker stop api`, a mensagem ficou "Enviando". Quando a API voltou, saiu sozinha e virou "Enviada", sem duplicar.
  - **1050 mensagens:** 70 gestos até a primeira (≈17 páginas), sem fechar.
  - **"Últimas":** 5 mensagens novas → a linha aparece antes da primeira.

## Decisões
- **O dia do separador segue a data efetiva** (mesma regra da ordenação).
- **Chamadas:** os botões ficam visíveis já, mas só avisam até a etapa 6.
- **Mensagens que faltam no WS 3 são buscadas sempre** (com a conversa aberta ou não): o cache fica certo e o custo é uma chamada.

## Pendências
- Itens abertos da etapa 3:
  - microfone e anexos (etapa 4);
  - ligar de verdade (etapa 6);
  - menu da mensagem, reações e salto para uma mensagem (etapa 7);
  - perfil pelo avatar e pesquisa (etapa 8).
