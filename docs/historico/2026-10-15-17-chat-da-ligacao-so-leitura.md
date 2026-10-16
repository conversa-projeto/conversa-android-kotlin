# 2026-10-15 · 17 · Chat da ligação só para leitura, na bolha e no histórico

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 3.5 (linha 🆕 `964ed72` da bolha) e 6.x (linha 🆕 `964ed72` do histórico); FC-727; CHA-26
- **Branch:** `reescrita`
- **Commits:** `e773a8e`

## Contexto
- **O servidor** (`964ed72`): manda o chat de cada ligação para quem é membro dele: `chat_chamada_id` no conteúdo da mensagem da ligação e `conversa_chat_id` no histórico.
- **O web** (`964ed72`/`e33ecba`): "Ver chat da chamada" / "Esconder o chat da chamada" abre esse chat dentro da bolha e do histórico, só para ler, com os conteúdos como no chat, e com "Abrir o chat completo".

## O que foi feito
- **Dados:**
  - `chatChamadaId` no `ConteudoDto`, na `ConteudoEntidade` e no `Conteudo`;
  - **Room v7:** migração automática 6→7 (coluna nova no conteúdo). O esquema `7.json` foi exportado;
  - `conversaChatId` no `ChamadaHistoricoDto` e no `ChamadaHistorico` (o histórico não fica no Room).
- **`MensagensRepositorio.chatDaChamada`:** as últimas 100 mensagens do chat, sem gravar no Room.
- **`ChatDaChamada`** (`core:ui`, novo):
  - o "Ver chat da chamada" / "Esconder o chat da chamada" com a seta;
  - aberto: "Carregando o chat…", o erro, "Ninguém escreveu no chat da chamada." ou as mensagens (quem, hora e o conteúdo, com a linha à esquerda, até 384 dp com rolagem);
  - e "Abrir o chat completo".
  - Quem usa decide como desenhar cada mensagem.
- **Bolha da ligação** (`Bolhas.kt`): com `chatChamadaId`, o `ChatDaChamada` embaixo, com os conteúdos como no chat (`ConteudoNaBolha`: texto, imagem com o visualizador, áudio, arquivo, figurinha). "Abrir o chat completo" abre a conversa (`ChatViewModel.abrirConversa`).
- **Histórico de chamadas** (`HistoricoChamadas.kt`): com `conversaChatId`, o mesmo `ChatDaChamada` embaixo da linha, com o resumo de cada mensagem: o texto, ou "Imagem", "Áudio", "Figurinha", "Arquivo".
- **Textos:** os do web, mais "Não foi possível carregar o chat".
- **Testes:**
  - o JSON com `chat_chamada_id` e `conversa_chat_id`, e sem eles para quem não é membro;
  - o chat da ligação vem sem gravar no Room;
  - a mensagem da ligação guarda o chat no Room.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (504 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A):**
  - **A bolha:** a direta A–B foi aberta pelo link na ligação 1179, cujo chat é o grupo 15. "Ver chat da chamada" mostrou "Teste 16:36 · Combinado o abacaxi para sexta", e "Abrir o chat completo" abriu o grupo 15.
  - **O histórico:** na aba Chamadas, a ligação de áudio com o B ganhou "Ver chat da chamada", com a mesma mensagem e o "Abrir o chat completo".

## Decisões
- **No histórico, o resumo de cada mensagem em vez dos conteúdos.** O histórico fica no módulo de chamadas, que não usa as bolhas do chat; o chat completo fica a um toque.
