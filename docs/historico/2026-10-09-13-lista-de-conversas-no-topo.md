# 2026-10-09 · 13 · Lista de conversas: a conversa que recebe mensagem aparece no topo

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** correção
- **Itens:** `TODO.md` 2.6 (nova linha depois de "Atualizar (sem spinner…)"); CON-01, CON-13
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O pedido foi garantir que, ao chegar mensagem nova, a lista se atualizasse corretamente. Todo o caminho foi conferido no emulador (conta A), com mensagens enviadas pela API como B:
- WebSocket 2 → `mensagens/novas` → Room → lista;
- a conversa aberta.

O caminho dos dados estava certo. A apresentação tinha um defeito: com a lista maior que a tela, a conversa que recebia a mensagem subia para o 1º lugar, mas ficava **escondida acima do topo**. O `LazyColumn` mantém na tela, pela chave, o item que já estava em primeiro. Quem olhava só via uma linha sumir do meio da lista.

## O que foi feito
- **`core/ui/.../componentes/ManterNoTopo.kt` (novo):**
  - **Quando age:** quando muda o primeiro item da lista, se a lista estava no topo, pede `requestScrollToItem(0)`.
  - **Como decide se estava no topo:** quem era o primeiro continua sendo o primeiro visível, sem deslocamento.
  - **Por que esse critério:** vale antes e depois de o `LazyColumn` medir a lista nova. Nos testes, a medida às vezes vem antes da composição, e "o índice era 0?" já chegava tarde.
  - **Quem rolou para baixo:** a lista continua onde está.
- **`ConversasTela.kt`:** a lista ganha um `LazyListState` próprio e chama `ManterNoTopo` com a chave do primeiro item (`c<id>`).
- **`core/ui/build.gradle.kts`:** Robolectric e `ui-test-junit4` nos testes do módulo.
- **`ManterNoTopoTest` (novo, Robolectric, 5 testes):**
  - **Sem o ajuste**, o item que sobe some, tanto lendo o estado direto quanto recebendo a lista pela recomposição, como no app. Esses dois testes provam o problema na versão do Compose do projeto.
  - **Com o ajuste**, o item aparece nos dois casos e numa segunda subida.
  - **Rolada para baixo**, a lista não pula.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Tudo passou.
- **No emulador (A), com mensagens do B pela API:**
  - **Chegada:** em menos de 1 s, a conversa foi para o topo com a prévia, a hora e o contador em 1.
  - **Rajada:** 3 mensagens seguidas levaram o contador a 4.
  - **Ao abrir:** aparece "Últimas" e o contador zera.
  - **Com a conversa aberta:** a mensagem nova aparece embaixo e a tela desce até ela.
  - **Ao voltar:** a prévia está atualizada.
- **Lista maior que a tela:** foram criados 6 grupos "Teste lista N" só com o A.
  - **Versão antiga:** a conversa que recebeu a mensagem sumiu acima do topo.
  - **Versão corrigida:** ela apareceu em primeiro.
  - **Lista rolada:** ela ficou parada.
- **Estado do emulador:** "Grupo de teste 2" foi desafixado para o teste e fixado de novo. Os 6 grupos "Teste lista N" ficaram na conta A, para testar listas que rolam.

## Decisões
- **Um componente no `core:ui`**, porque outras listas com o mais novo no topo podem precisar dele.
- **Atividades e Chamadas ficaram como estão:** elas começam com o cabeçalho do dia ("Hoje"), que segura o topo. Lá o efeito só apareceria no primeiro item de um dia novo.

## Pendências
- Se aparecer o caso do dia novo, usar o `ManterNoTopo` nas Atividades e no histórico de Chamadas.
