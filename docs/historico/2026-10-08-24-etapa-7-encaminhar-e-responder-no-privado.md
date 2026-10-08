# 2026-10-08 · 24 · Etapa 7 — encaminhar e responder no privado

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.6 (menos o "Copiar", já feito), a linha de itens do 7.1 e "Esconder Encaminhar em votação" do 7.12; FC-506, FC-512, FC-518; ENV-07, ENV-20
- **Branch:** `reescrita`
- **Commits:** `19add2b`

## Contexto
O menu da mensagem passa a ter todas as ações do web (`MensagemAcoes.vue`): Responder, Responder no privado, Encaminhar, Copiar e Ocultar. No web, "Encaminhar" abre o `ForwardMessageModal.vue`. "Responder no privado" abre a conversa direta com quem escreveu, já com a mensagem pendente como encaminhada (`chat.responderNoPrivado`).

## O que foi feito
- **`core:data`:**
  - `EnvioMensagens.enviar` com referência **encaminhada**: os conteúdos da original vão antes do texto, como no web.
    - Vão os identificadores dos anexos (sem subir de novo); conteúdos locais e votação ficam de fora (o servidor recusa votação).
    - Pode ir sem texto. A otimista mostra a citação e os conteúdos.
  - `MensagensRepositorio.buscar`: uma mensagem do aparelho.
- **`feature:chat`:**
  - **`Encaminhar.kt` (novo):**
    - `DestinoEncaminhar` (conversa ou contato) e `destinosParaEncaminhar`: as conversas menos a de origem, depois os contatos sem conversa direta;
    - `filtrarDestinos`: busca no nome e no e-mail;
    - a folha `EncaminharMensagem`, com os textos do web: "Encaminhar mensagem", "Mensagem selecionada", "Buscar destino", "Grupo existente", "Conversa direta existente", "Novo chat direto", etiquetas "Conversa"/"Contato", "Nenhum destino encontrado.".
  - **`ChatViewModel`:**
    - `encaminhar`: para contato, cria a direta antes ("Erro ao encaminhar mensagem" se falhar); manda pela fila e abre o destino;
    - `responderNoPrivado`: só mensagem de outra pessoa; abre a direta com `encaminharDe`;
    - a resposta pendente virou `ReferenciaPendente` (resposta ou encaminhada);
    - aberta com `encaminharDe`, a mensagem fica pendente como encaminhada e o campo pega o foco;
    - o envio sem texto sai quando há encaminhada.
  - **Menu:** "Responder no privado" (grupo, mensagem de outra pessoa) e "Encaminhar" (escondido em votação, FC-518).
  - **Barra do campo:** "Encaminhando de …" e o "Enviar" mesmo com o campo vazio.
  - **Navegação:** `RotaChat.encaminharDe`; `aoAbrirConversa(conversa, mensagem, encaminharDe)`.

## Como foi verificado
- **Testes novos:**
  - `MensagensTest`: a encaminhada leva os conteúdos antes do texto e pode ir sem texto;
  - `ChatViewModelTest`: encaminhar para conversa e para contato (com falha); responder no privado (não em mensagem minha); aberta com `encaminharDe` e envio sem texto;
  - `EncaminharTest`: destinos e busca.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador (A), no "Grupo criado pelo B":**
  - o menu numa mensagem do B mostrou Responder, Responder no privado, Encaminhar e Copiar;
  - "Encaminhar" abriu a folha com as conversas (a direta arquivada com o B inclusive); escolhendo "Grupo de teste 2", o app abriu o grupo com a encaminhada. No servidor: mensagem 1099 com o conteúdo e a referência tipo 2;
  - "Responder no privado" abriu a direta com o B, com "Encaminhando de Teste". O comentário saiu como mensagem 1100: conteúdo da original (ordem 1), comentário (ordem 2) e a referência tipo 2. A bolha mostra a encaminhada com a citação aninhada e o comentário embaixo.

## Decisões
- **Encaminhar pela fila de envio,** e não direto pela API como o web: a mensagem aparece na hora no destino e não se perde sem rede.
- **O destino abre por cima da conversa de origem:** o "voltar" volta a ela. No web, a conversa ativa só troca.

## Pendências
- Ficaram mensagens de teste no "Grupo de teste 2" e na direta A–B (servidor de dev).
