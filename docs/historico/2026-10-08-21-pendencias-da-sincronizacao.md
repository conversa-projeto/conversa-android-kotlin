# 2026-10-08 · 21 · Itens 🆕 da sincronização: limite e "+N" nas reações, chat da chamada e cabeçalho do grupo

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade / correção
- **Itens:** `TODO.md` 2.10, 3.7, 6.13 e 7.2 (linhas 🆕); FC-212, FC-501, FC-719, FC-723; ENV-17, CON-08, CHA-18, CHA-24
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
A sincronização de 2026-10-08 (registro 20) trouxe itens pequenos para seções já fechadas. Pela ordem do TODO, eles vêm antes do 7.3:
- reações (servidor `d4435db` e web `eaa8bac`);
- chat da chamada (web `eaa8bac`);
- cabeçalho do grupo (web `eaa8bac`).

Os outros cabem neste fluxo e são citados aqui:
- chamadas (6.13);
- grupos (2.10);
- campo de mensagem (3.7).

## O que foi feito
- **Reações (7.2):**
  - **Limite de 5:** `core:model` ganhou `LIMITE_REACOES_POR_PESSOA`, `REACOES_A_MOSTRA` e `podeReagir` (tirar sempre pode; pôr um emoji novo, só abaixo de 5).
    - O `ChatViewModel.reagir` confere antes da reação otimista, como o web, e manda `EventoChat.LimiteDeReacoes` ("Você já reagiu com 5 emojis nesta mensagem.").
    - O erro do servidor vira `ReagirFalhou` ("Não foi possível reagir: <motivo>").
  - **Chips:** `ChipsDeReacao` mostra 5 e um "+N", destacado se eu reagi em algum dos escondidos. O TalkBack lê "Mais 1 reação" ou "Mais N reações" (plural).
    - Tocar no "+N" abre `MaisReacoes`, com emoji, quem reagiu e contagem; tocar alterna.
    - `AcoesAbertas.maisReacoes` guarda o que está aberto.
  - **Ordem:** os chips agora seguem a ordem do servidor (`order by min(r.id)`: a primeira reação de cada emoji vem antes), e não a do código do emoji (a chave do Room).
    - Coluna nova `ordem` em `ReacaoEntidade`: banco **versão 2**, com `AutoMigration(1, 2)`, sem apagar nada.
    - Os mapeadores gravam a posição da resposta; a reação otimista entra no fim.
    - O schema `2.json` foi exportado.
- **Chat da chamada (6.13):** sem grupo, a folha `ChatAindaSemGrupo` mostra o aviso do web e um campo-botão "Digite uma mensagem".
  - O toque cria o grupo (`criarChat` → `garantirChat`), mostra "Abrindo o chat…" enquanto isso, e abre a conversa com `conversa://chat/{id}?focar=1`.
  - Se falhar, aparece "Erro ao abrir o chat".
  - O `ChamadaViewModel` não depende mais do `EnvioMensagens`.
- **Campo com foco:** `RotaChat.focar`, lido no link por `rotaDoLink`.
  - `ChatUiState.focarCampo` vale uma vez (`campoFocado`).
  - O `Campo` usa um `FocusRequester` no `TextField`. Também serve para o "Responder" (7.3).
- **Conversa que não está no aparelho:** o `ChatViewModel` relê a lista (`conversas.atualizar()`) antes dos membros. Antes, o grupo recém-criado abria com "?" e sem nome.
- **Cabeçalho do grupo (2.10):** avatar e nome clicáveis só em grupo; abrem a tela "Membros do grupo" (ação "Membros do grupo" no TalkBack).
- **Placeholder do campo (3.7):** "Digite uma mensagem", como o web desde o campo rico.
  - Antes era "Mensagem", com o mesmo nome de recurso que o `feature:chamada` usou com outro texto. Um sobrescrevia o outro no app.

## Como foi verificado
- **Testes novos:**
  - `ReacoesTest` (limite);
  - `MensagensTest`: ordem do servidor e a otimista no fim;
  - `ChatViewModelTest`: conversa ausente é buscada antes dos membros, e o foco vale uma vez;
  - `LinkConversaTest`: `focar=1`.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador (A),** com o web do B pelo DevTools:
  - **Migração:** o app foi instalado por cima do banco v1 e abriu sem erro, já na ordem certa depois da carga.
  - **Reações na 1048:** o A reagiu com 5 emojis e o B com 2. Os chips ficaram 🤩 👍 ❤️ 😂 😮 "+2", igual ao web.
    - O "+2" abriu 🔥 e 👏; tocar no 🔥 mostrou o aviso do limite sem ir ao servidor.
    - Depois de tirar o 😮, tocar no 👏 da lista somou a reação (servidor: 👏 2) e o chip virou "Mais 1 reação".
  - **Cabeçalho:** tocar no nome do grupo abriu "Membros do grupo".
  - **Chat da chamada:** o B ligou e o A atendeu. "Chat da chamada" abriu a folha; o toque no campo criou o grupo e abriu o chat com o nome "Chamada: Teste Android A, Teste Android B" e o teclado. A mensagem enviada chegou ao grupo (conferido na API).

## Decisões
- **O limite fica no ViewModel,** com a regra no `core:model`: é lá que está a mensagem atual, e o servidor confere de novo.
- **Migração automática,** e não destrutiva: o banco guarda os envios pendentes.
- **"+N" com área de toque de 48 dp:** os chips ficam centralizados na linha (`itemVerticalAlignment`).

## Pendências
- Ficaram dois grupos "Chamada: …" de teste no servidor de dev (conversas 6 e 7) e reações de teste no "Grupo criado pelo B".
