# 2026-10-08 · 23 · Etapa 7 — citação completa e "ir para a mensagem"

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade / correção
- **Itens:** `TODO.md` 7.5 e a linha do link com `mensagem=` do 3.11; FC-504, FC-505; MSG-06, MSG-19
- **Branch:** `reescrita`
- **Commits:** `2a4cc59`

## Contexto
O web desenha a citação com o conteúdo completo da original (`BolhaReferencia.vue`, `ReferenciaRecursiva.vue`): título com a hora, citação aninhada, imagens e áudios. Tocar leva até a original. O Android mostrava só um resumo de duas linhas, sem toque.

## O que foi feito
- **`core:model`:**
  - `separarConteudosDaCitacao`: a regra do web para o que vai na citação e o que fica embaixo. Na encaminhada, a citada sem conteúdo usa os próprios, e os próprios iguais (tipo + conteúdo) não se repetem.
  - `MensagemResumida.comoMensagem()`, para desenhar a citada com as mesmas bolhas.
  - `MAXIMO_NIVEIS_CITACAO = 5`.
- **`core:data` — `MensagensRepositorio.trazerAte`:**
  - garante que a mensagem está no aparelho **sem deixar buraco**: volta de 99 em 99 (o servidor devolve no máximo 100) a partir da mais antiga salva, até ela aparecer;
  - no máximo 20 páginas (cerca de 2.000 mensagens);
  - para no começo da conversa e devolve a falha de rede.
- **`feature:chat`:**
  - **`BlocoCitacao`** (no lugar da `Citacao`):
    - título "Remetente · HH:mm" ("Encaminhado de …", ou "Encaminhado"/"Resposta" sem nome, como o web);
    - "Mensagem oculta" quando a citada foi ocultada;
    - a aninhada, até 5 níveis, e os conteúdos com as bolhas de texto, imagem, áudio e arquivo;
    - a borda é desenhada atrás: sem medida intrínseca, porque há imagem e vídeo dentro.
  - **Toque na citação:** o bloco todo é tocável ("Ir para a mensagem" para o TalkBack). Resposta sempre; encaminhada só se participo da conversa dela (`minhasConversas`, `participaDe`).
  - **`ChatViewModel.irParaMensagem`:**
    - nesta conversa: `trazerAte`, depois `irPara` e `destaque` no estado, com a barra de progresso enquanto busca (`buscandoMensagem`);
    - de outra conversa: `AbrirConversa(conversa, mensagem)`, se participo;
    - sem achar: "Não foi possível localizar esta mensagem no contexto da conversa.";
    - `chegouNaMensagem` tira o destaque depois de 1,2 s;
    - o `mensagemId` da rota (o link `?mensagem=`) também usa o salto.
  - **Tela:**
    - rola até a mensagem, perto do meio: longe, salta sem animar (animar centenas de itens demora); perto, anima;
    - destaca a linha (cor primária suave, animada);
    - a imagem de uma citada que não está na lista abre sozinha no visualizador.
- **`app` — navegação:** o link ou a notificação de uma conversa troca o chat do topo.
  - O `launchSingleTop` reaproveitava a entrada do topo, com o ViewModel da conversa antiga, e a tela não mudava. Era um defeito já existente, achado ao testar o link.
  - A rota do chat passa a mensagem adiante (`aoAbrirConversa(conversa, mensagem)`).

## Como foi verificado
- **Testes novos:**
  - `ChatTest`: 4 casos de `separarConteudosDaCitacao`;
  - `MensagensTest`: `trazerAte` volta sem buraco, para no começo e falha sem rede;
  - `ChatViewModelTest`: salto e destaque de 1,2 s, não achou, encaminhada de conversa minha ou não, e o link com a mensagem.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador (A),** com mensagens criadas pelo B na API (resposta à "Mensagem número 100", resposta da resposta, encaminhada do "Grupo de teste 2", resposta à "Mensagem número 3"):
  - a citação mostrou título e hora, a aninhada e a encaminhada sem repetir o conteúdo;
  - tocar na citação da 100 (cerca de 950 mensagens atrás) trouxe as páginas e parou nela; a 3 foi até o começo da conversa;
  - com a mensagem já carregada, o salto centralizou e destacou;
  - a encaminhada abriu o "Grupo de teste 2" na original;
  - com um chat aberto, `conversa://chat/3?mensagem=104` trocou para o outro grupo já na mensagem, e o voltar levou à lista.

## Decisões
- **Trazer até a mensagem em vez de substituir a lista.** O web troca a lista em memória por uma janela de 30/30 em volta da mensagem. No Android a lista é o Room inteiro da conversa: uma janela solta deixaria um buraco que a paginação nunca preencheria. Por isso o Android pagina para trás até a mensagem; até ~2.000 mensagens de distância, depois avisa que não achou.
- **O bloco todo da citação é tocável,** e não só o título como no web: área de toque melhor no celular. Imagens, áudios e links de dentro continuam com o toque deles.

## Pendências
- Ficaram mensagens de teste no "Grupo criado pelo B" (servidor de dev).
