# 2026-10-16 · 05 · Conversa com bot de IA

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2); toca também as mensagens (confirmação) e as chamadas
- **Tipo:** código
- **Itens:** `TODO.md` 2.9 (linha 🆕 `ef13c22`/`9c6e8a6`); FC-221; CON-16; S27 (itens 2 e 3)
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`ef13c22`) tem bots de IA: usuários comuns que conversam por mensagens comuns. Só `GET /usuario/contatos` diz quem é bot (`bot: true`); as outras rotas não, e o cliente cruza pelo id (contrato §13.7).
- **O bot nunca atende nem confirma** (S27): sem `confirmacao_usuarios`, o servidor pede a confirmação a todos os outros membros, inclusive aos bots, e o "N de T" não fecha; dá para ligar para um bot, mas a chamada só toca.
- **O web** (`9c6e8a6`) mostra a pílula "Bot" (`title` "Responde com a IA") ao lado do nome, só na direta com o bot.

## O que foi feito
- **Dados:**
  - `ContatoDto.bot`, `Contato.bot` e a coluna `bot` em `ContatoEntidade`: **Room v9**, AutoMigration 8→9 com padrão 0 (esquema `9.json`);
  - `FichaUsuario.bot`, preenchido pelo `fichaDaConversa`.
- **`core:model`:** `listaDeConfirmacao(escolhidos, outros, bots)`. Com bot no grupo, o "todos" vira a lista dos outros sem os bots; a escolhida perde os bots; sem bot, continua sem lista.
- **Chat:**
  - `ChatUiState.comBot` (direta com um bot) e o conjunto de bots lido dos contatos;
  - **cabeçalho:** a pílula "Bot" ao lado do nome (o leitor de tela diz "Bot: responde com a IA") e sem os botões de voz e vídeo;
  - **"+":** sem "Confirmar leitura" na direta com bot; o menu da mensagem sem "Pedir confirmação de leitura";
  - **grupos:** o "Quem precisa confirmar" não lista bots, e o envio e o "Pedir confirmação" depois mandam a lista sem eles.
- **Folha do perfil:** sem "Ligar" para bot (na lista de conversas e no chat).
- **Chamada:** bot fora do "Adicionar à chamada".
- **Testes:** `ConfirmacaoTest` (a lista), `PerfilTest` (a ficha), `ChatViewModelTest` (a pílula) e `MigracoesTest` (versão 9; contato antigo fica com `bot = 0`).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev:**
  - criar bot pela API exige a IA configurada, e os parâmetros de IA do servidor de dev só mudam com autorização. Por isso a conta C foi marcada como bot direto na base (uma linha em `bot`, `ativo = false`, para nunca responder);
  - reaberto o app, a folha do perfil do C ficou sem "Ligar";
  - no chat com o C, a pílula "Bot" ao lado do nome e nenhum botão de ligar; o "+" sem "Confirmar leitura";
  - apagada a linha, o app releu os contatos e o chat do C voltou a ter os botões de ligar, sem a pílula.
  - O caso do grupo não foi visto no emulador: o C não está em grupo com o A, e pôr o C num grupo não teria volta (o servidor não deixa tirar outra pessoa, S15). Fica com os testes unitários.

## Decisões
- **Além do web:** não oferecer ligação para bot nem pedir confirmação a ele. O web ainda oferece, e o resultado não serve (S27, itens 2 e 3).
- **O bot continua nos grupos, nas menções e na "Nova conversa":** é assim que se conversa com ele.
- **Tiques de entrega** em grupo com bot (S27, item 1) seguem como o servidor manda: o app não tem o detalhe por pessoa para corrigir.

## Pendências
- S27 itens 1, 4 e 5 (servidor).
