# 2026-10-08 · 15 · Etapa 6 — adicionar à chamada e chat da chamada

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 6.13 (adicionar participante, chat da chamada, chat completo) e 6.9 (controles); FC-718, FC-719
- **Branch:** `reescrita`
- **Commits:** `0cf178b`

## Contexto
Dois recursos do web que faltavam na chamada:
- **adicionar alguém** a uma chamada em andamento;
- **o chat da chamada:** um grupo criado na primeira mensagem, com os participantes.

## O que foi feito
- **Adicionar à chamada:**
  - `ChamadasRemotas.adicionar` → `PUT /chamada/usuario`;
  - `GerenciadorChamadas.adicionar(usuarios)`: um por vez, em sequência como o web, e depois os dados da chamada;
  - **na tela:** botão "Adicionar usuário" e diálogo "Adicionar à chamada" (textos do web), com os contatos fora da chamada e caixas de seleção.
    - "Nenhum contato disponível para adicionar." quando não há ninguém.
- **Chat da chamada:**
  - `ChamadasRemotas.chat` → `PUT /chamada/chat`;
  - `GerenciadorChamadas.garantirChat()`: devolve a conversa que já existe ou cria na primeira vez;
  - **na tela:** botão "Chat da chamada".
    - **Sem chat ainda:** abre o painel só com o campo "Mensagem" (como o web). A primeira mensagem cria o chat, entra na fila de envio e abre a conversa.
    - **Com chat:** abre a conversa completa direto.
  - **Abrir a conversa:**
    - é a própria tela de chat do app (link `conversa://chat/{id}`), não um painel por cima;
    - numa chamada de vídeo, a chamada vai para o picture-in-picture;
    - a faixa "Toque para voltar à chamada" traz de volta.
- **Barra de controles:** com até 7 botões (vídeo, chamada ativa), rola na horizontal quando não cabe e fica centralizada quando cabe.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **287 testes, 0 falhas**;
  - 4 novos no `GerenciadorChamadasTest`: adicionar manda um `PUT` por pessoa e atualiza os dados; adicionar só com a chamada ativa; o chat é criado uma vez; sem chamada, nulo;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, contra o web:**
  - **Adicionar:**
    - numa chamada do B com o app, o diálogo listou só o C;
    - "Adicionar" → o C recebeu a chamada e atendeu;
    - o app passou a "3 pessoas", com B e C.
  - **Chat:**
    - numa chamada de vídeo, "Chat da chamada" → o painel → "Oi do app na chamada" → foi criado o grupo "Chamada: Teste Android A, Teste Android B";
    - o web recebeu o mesmo chat (WS 57);
    - a conversa abriu com a mensagem, e a chamada ficou em PiP com a faixa no topo.
  - **Voltar:** a faixa trouxe a chamada de volta em tela cheia. O segundo toque em "Chat da chamada" abriu a conversa direto.

## Decisões
- **Chat completo = a tela de chat do app, não um painel sobre a chamada.**
  - No celular não há espaço para os dois juntos.
  - Com o PiP e a faixa, a pessoa conversa e volta à chamada com um toque.
  - O painel simples (só o campo) continua para antes de existir o chat, como no web, para não criar grupo sem mensagem.

## Pendências / próximos passos
- **Resto da 6.13:** indicador de fala, somente recepção ("Ativar microfone/câmera") e tela/ponteiro remotos.
