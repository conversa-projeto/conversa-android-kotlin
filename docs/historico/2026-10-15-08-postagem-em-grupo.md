# 2026-10-15 · 08 · Postagem em grupo: bolha, responder pelo campo e criar pelo "+"

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.13 (2ª, 3ª e 4ª linhas); FC-317, FC-522; MSG-22, ENV-26
- **Branch:** `reescrita`
- **Commits:** `2033b39`

## Contexto
O bloco 05 deste dia fez só a parte de dados da postagem (servidor `12cf0cf`, web `d202c06`/`e33ecba`): os DTOs, o Room v4 e o contador. A postagem ainda aparecia como uma mensagem comum. Faltavam três coisas do 7.13, iguais ao web (`BolhaPostagem.vue`, `MessageInput.vue`, `AnexoPopup.vue`):
- a bolha;
- responder pelo campo do chat;
- criar a postagem pelo "+".

## O que foi feito
- **Classificação** (`core/model/.../Chat.kt`): `TipoExibicao.POSTAGEM` depois de oculta e chamada, antes de imagem, com qualquer conteúdo.
- **Bolha** (`feature/chat/.../BolhaPostagem.kt`, novo):
  - **Forma:** um cartão da largura do chat, de quem quer que seja.
  - **Em cima:** "Postagem · Você/nome" e o título.
  - **No meio:** os conteúdos, com o `CorpoPadrao` de uma mensagem recebida, como no web.
  - **Contagem:**
    - "Nenhuma resposta ainda" / "1 resposta" / "N respostas";
    - o selo "N novas".
  - **Respostas à mostra:**
    - as 2 últimas, com avatar, "Você", hora e conteúdo; a apagada vira "Mensagem apagada";
    - "Ver todas as N" / "Mostrar menos": `GET /postagem/respostas`, lido de novo a cada resposta nova;
    - as minhas ainda na fila, com o relógio, ou "Reenviar" / "Apagar".
  - **Embaixo:** o botão "Responder à postagem". Fica "Respondendo no campo de mensagem" enquanto a resposta está no campo, e desligado enquanto a postagem não tem id.
  - O `LinhaMensagem` (`Bolhas.kt`) dá a largura toda ao cartão e não repete o nome em cima. O `CorpoPadrao` e o `ConteudoNaBolha` passaram a `internal`, e o `CorpoPadrao` ganhou o `modifier`.
- **Campo** (`CampoMensagem.kt`):
  - **Faixas:**
    - "Respondendo à postagem de <nome>", com o título (ou o resumo) e o "×" "Não responder à postagem";
    - "Postagem: as respostas ficam embaixo dela", com "Título (opcional)" (até 150 caracteres, em estado local) e o "×" "Não postar";
    - as duas usam o mesmo desenho da faixa de resposta (`FaixaDoCampo`).
  - **"+" › "Postagem":** só em grupo, como a votação.
  - **"Agendar":** some ao responder a uma postagem, porque o servidor recusa resposta agendada.
- **ViewModel** (`ChatViewModel.kt`):
  - **Estado:** `postando` / `respondendoPostagem`.
  - **Ações:**
    - `postar`, `tituloDaPostagem`, `cancelarPostagem` e `responderPostagem`;
    - responder a uma mensagem comum tira a resposta à postagem, e vice-versa.
  - **Envio:** texto, anexos, figurinha, código e gravação levam a postagem do campo (`pegarPostagem`; o `ControleGravacao` ganhou o `pegarPostagem`). A resposta não desce ao fim do chat.
  - **`acoesPostagem`:** minhas respostas na fila, fotos dos membros, "Ver todas" e "Responder".
- **Envio** (`EnvioMensagens.kt`):
  - `PostagemEnvio.Nova(titulo)` → `postagem: {titulo}`; a otimista já é uma postagem sem respostas.
  - `PostagemEnvio.Resposta(id)` → `postagem_id`.
  - Ao sair, a resposta não vira linha do chat: a otimista sai e a postagem é relida (traz as últimas).
- **Rede e banco:**
  - `PostagemEnvioDto`, `postagem` e `postagem_id` em `EnviarMensagemRequisicao`;
  - `postagemId` em `Mensagem`.
  - **Room v5:** migração automática 4→5. A coluna `postagemId` na mensagem existe só para a resposta na fila; o fluxo do chat (`observarDaConversa`) a deixa de fora, e `observarRespostasLocais` a entrega à bolha. O esquema `5.json` foi exportado.
- **Textos** (os do web): `postagem`, `nenhuma_resposta_ainda`, `postagem_respostas` e `postagem_novas` (plurais), `ver_todas_as_respostas`, `mostrar_menos`, `mensagem_apagada`, `responder_a_postagem`, `respondendo_no_campo`, `respondendo_a_postagem_de`, `nao_responder_a_postagem`, `postagem_faixa`, `titulo_opcional`, `titulo_da_postagem` e `nao_postar`.
- **Testes:**
  - classificação;
  - JSON do envio: `postagem` com e sem título, `postagem_id`;
  - envio da nova (otimista já como postagem);
  - envio da resposta: fora do fluxo, embaixo da postagem até sair, com a postagem relida no fim;
  - "Ver todas" sem gravar no Room;
  - ViewModel: postar com título, sem título, título cortado em 150, "Não postar";
  - ViewModel: responder à postagem com figurinha, sem descer ao fim, e a troca com a resposta comum.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou. Depois do ajuste da hora, de novo os testes do chat, o lint e o ktlint.
- **No emulador (A), com o B pela API (as mesmas rotas do web):**
  - **Abertura:** a versão nova migrou o banco para a v5.
  - **Postagem 1170 do B:** o cartão com o título, "4 respostas", as 2 últimas e "Ver todas as 4". Abriu as 4, da mais antiga à mais nova, e "Mostrar menos".
  - **Responder à postagem:**
    - a faixa "Respondendo à postagem de Teste" e o botão "Respondendo no campo de mensagem";
    - o envio pôs "Você · Resposta do Android" embaixo da postagem, com "5 respostas", sem descer ao fim;
    - no servidor, a resposta 1181, do A, ficou na postagem 1170.
  - **Criar postagem:**
    - "+" › "Postagem" mostrou a faixa; com o título "Reuniao", o cartão "Postagem · Você";
    - no servidor, a postagem 1182, do A, com o título.
  - **Resposta do B com o grupo aberto:** chegou sozinha (WS 64), "1 resposta", já vista; a lista ficou sem contador.
  - **Conversa direta:** o "+" não tem "Votação" nem "Postagem".
  - **Ajuste visual:** a hora da postagem ficava logo depois do texto; passou para o canto direito do cartão.

## Decisões
- **A resposta na fila fica no Room**, como qualquer envio (não se perde, sobe anexos, tenta de novo), com a coluna `postagemId` para ficar fora do fluxo. O web descarta a otimista; aqui ela aparece embaixo da postagem até sair.
- **O conteúdo da postagem usa as cores da mensagem recebida**, de quem quer que seja, como o web. Por isso a minha postagem não mostra os tiques de entrega (o web também não).
- **O foco vai para o campo da mensagem** ao escolher "Postagem", não para o título (o web não foca nenhum dos dois).

## Pendências
- 7.13 **Teste**: falta a notificação "<nome> respondeu a uma postagem" (FC-610, 5.4) e postar dos dois lados com o web na tela.
- Doc 05: MSG-22 e ENV-26 passam a ✅ na próxima atualização da matriz.
- No emulador, o `adb shell input text` perde letras quando o texto vai de uma vez (o teclado do emulador). Letra por letra funciona. Não é defeito do app.
